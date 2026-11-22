package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvNavigation
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvStamina
import com.mitahv.mobile.model.HvStaminaRecovery
import com.mitahv.mobile.model.HvWorld
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object HvNavigationParser {
    fun parse(
        html: String,
        world: HvWorld,
        route: HvRoute,
        currentUrl: HttpUrl = route.url(world),
    ): HvNavigation? = parse(Jsoup.parse(html), world, route, currentUrl)

    fun parse(document: Document, world: HvWorld, route: HvRoute,
        currentUrl: HttpUrl = route.url(world)): HvNavigation? {
        val navbar = document.selectFirst("#navbar") ?: return null
        val sections = listOf("Character", "Bazaar", "Battle")
        val groups = sections.map { section ->
            val menu = navbar.selectFirst("#child_$section") ?: return null
            menu.select("div[onclick]").mapNotNull { entry ->
                if (entry.isDisabledControl()) return@mapNotNull null
                val href = NAVIGATION_ACTION.matchEntire(entry.attr("onclick"))
                    ?.groupValues?.get(2) ?: return@mapNotNull null
                HvRoute.fromUrl(href, world)?.takeIf { it.section == section }
            }.distinct()
        }
        if (groups.any { it.isEmpty() }) return null

        val stamina = document.selectFirst("#stamina_readout")?.let { readout ->
            readout.hvText().takeIf(String::isNotBlank)?.let { text ->
                HvStamina(
                    value = text,
                    description = readout.selectFirst("img[title]")?.attr("title").orEmpty(),
                    recovery = document.selectFirst("#stamina_restore #recoverform")
                        ?.let { recovery(it, world, route, currentUrl) },
                )
            }
        }
        return HvNavigation(groups.flatten(), stamina,
            document.select("#messagebox_inner > p").map { it.hvText() }.filter(String::isNotBlank))
    }

    private fun recovery(form: Element, world: HvWorld, route: HvRoute, currentUrl: HttpUrl): HvStaminaRecovery? {
        if (!StaminaRequests.isValidTarget(world, route, currentUrl)) return null
        if (!form.attr("method").equals("post", ignoreCase = true) || form.isDisabledControl()) return null
        // The supplied pages submit this form through an enabled image control.
        val canSubmit = form.select("img[onclick]").any { control ->
            !control.isDisabledControl() &&
                !control.attr("src").substringBefore('?').endsWith("_d.png", ignoreCase = true) &&
                RECOVERY_ACTION.matches(control.attr("onclick"))
        }
        if (!canSubmit) return null
        val action = if (form.attr("action").isBlank()) currentUrl else currentUrl.resolve(form.attr("action")) ?: return null
        if (!StaminaRequests.isValidTarget(world, route, action)) return null
        val fields = form.select("input[name]")
            .filter { it.attr("type").equals("hidden", ignoreCase = true) && !it.isDisabledControl() }
            .map { it.attr("name") to it.attr("value") }
        if (!StaminaRequests.hasRecoveryFields(fields)) return null
        return HvStaminaRecovery(action, fields)
    }

    private fun Element.isDisabledControl(): Boolean =
        hasAttr("disabled") || attr("aria-disabled").equals("true", ignoreCase = true) ||
            parents().any { it.tagName() == "fieldset" && it.hasAttr("disabled") }

    private val NAVIGATION_ACTION = Regex("""\s*document\.location\s*=\s*(['"])([^'"]+)\1\s*;?\s*""")
    private val RECOVERY_ACTION = Regex("""\s*document\.getElementById\(\s*(['"])recoverform\1\s*\)\.submit\(\s*\)\s*;?\s*""")
}
