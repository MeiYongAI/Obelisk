package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HvNavigationParserTest {
    @Test fun alternatePersistentMenusKeepAllPersistentRoutesAndRecovery() {
        val html = fixture("persistent.html").replace("https://hentaiverse.org/", "https://alt.hentaiverse.org/")
        val navigation = requireNotNull(HvNavigationParser.parse(html, HvWorld.Persistent, HvRoute.Character,
            HvRoute.Character.url(HvWorld.Persistent, HvSite.Alt)))
        assertEquals(18, navigation.routes.size)
        assertEquals("https://alt.hentaiverse.org/", navigation.stamina!!.recovery!!.actionUrl.toString())
    }

    @Test
    fun persistentMenusAndRecoveryComeFromSuppliedPage() {
        val navigation = requireNotNull(parse(fixture("persistent.html")))

        assertEquals(listOf("ch", "eq", "ab", "tr", "it", "se"),
            navigation.routes.filter { it.section == "Character" }.map { it.subsection })
        assertEquals(listOf("is", "ss", "mk", "am", "ml", "mm", "lt", "la"),
            navigation.routes.filter { it.section == "Bazaar" }.map { it.subsection })
        assertEquals(listOf("ar", "rb", "gr", "iw"),
            navigation.routes.filter { it.section == "Battle" }.map { it.subsection })
        val stamina = requireNotNull(navigation.stamina)
        assertEquals("Stamina: 67", stamina.value)
        assertEquals("Stamina: Great. You receive a 100% EXP Bonus but stamina drains 50% faster.", stamina.description)
        assertEquals(HvWorld.Persistent.baseUrl, stamina.recovery?.actionUrl)
        assertEquals(listOf("recover" to "stamina"), stamina.recovery?.fields)
    }

    @Test
    fun isekaiMenusDifferAndEmptyRecoveryContainerDoesNotBecomeAnAction() {
        val navigation = requireNotNull(HvNavigationParser.parse(fixture("isekai.html"), HvWorld.Isekai, HvRoute.Character))

        assertEquals(listOf("ch", "eq", "ab", "it", "se"),
            navigation.routes.filter { it.section == "Character" }.map { it.subsection })
        assertEquals(listOf("is", "ss", "mk", "am", "mm"),
            navigation.routes.filter { it.section == "Bazaar" }.map { it.subsection })
        assertEquals(listOf("ar", "tw", "rb", "gr", "iw"),
            navigation.routes.filter { it.section == "Battle" }.map { it.subsection })
        assertEquals("Stamina: 43", navigation.stamina?.value)
        assertNull(navigation.stamina?.recovery)
    }

    @Test
    fun loginAndBattleDocumentsDoNotRetainMenuOrRecovery() {
        assertNull(parse("<html><form id='login'><input name='UserName'></form></html>"))
        assertNull(parse("<div id='mainpane'><div id='battle_main'></div></div>"))
        assertNull(parse("<div id='navbar'><iframe src='https://hentaiverse.org/'></iframe></div>"))
    }

    @Test
    fun disabledMissingOrDifferentJavascriptDoesNotOfferRecovery() {
        listOf("disabled", "aria", "image", "onclick", "field", "method").forEach { mutation ->
            val document = Jsoup.parse(fixture("persistent.html"))
            val form = requireNotNull(document.selectFirst("#recoverform"))
            val control = requireNotNull(form.selectFirst("img"))
            when (mutation) {
                "disabled" -> control.attr("disabled", "")
                "aria" -> control.attr("aria-disabled", "true")
                "image" -> control.attr("src", "userestorative_d.png")
                "onclick" -> control.attr("onclick", "do_something_else()")
                "field" -> form.selectFirst("input[name=recover]")!!.attr("disabled", "")
                "method" -> form.attr("method", "get")
            }
            assertNull(mutation, parse(document.outerHtml())?.stamina?.recovery)
        }
    }

    @Test
    fun recoveryCannotCrossWorldPageOrOrigin() {
        listOf(
            "https://alt.hentaiverse.org/isekai/",
            "https://hentaiverse.org/isekai/",
            "https://example.org/",
            "http://hentaiverse.org/",
            "https://hentaiverse.org/?s=Battle&ss=ar",
        ).forEach { action ->
            val document = Jsoup.parse(fixture("persistent.html"))
            document.selectFirst("#recoverform")!!.attr("action", action)
            assertNull(action, parse(document.outerHtml())?.stamina?.recovery)
        }
    }

    @Test
    fun actionOnCurrentArenaPageRetainsOnlySuccessfulHiddenFields() {
        val document = Jsoup.parse(fixture("persistent.html"))
        val form = document.selectFirst("#recoverform")!!
        form.attr("action", "?s=Battle&ss=ar")
        form.append("<input type='hidden' name='synthetic_field' value='a&amp;b'>")
        form.append("<input type='hidden' name='disabled_field' value='ignored' disabled>")
        val navigation = HvNavigationParser.parse(document.outerHtml(), HvWorld.Persistent, HvRoute.Battle)

        assertEquals(HvRoute.Battle.url(HvWorld.Persistent), navigation?.stamina?.recovery?.actionUrl)
        assertEquals(listOf("recover" to "stamina", "synthetic_field" to "a&b"), navigation?.stamina?.recovery?.fields)
    }

    @Test
    fun recoveryRetainsSubpageActionsSeenInSuppliedHtml() {
        listOf(
            HvRoute.Abilities to "?s=Character&ss=ab&tree=onehanded",
            HvRoute.Market to "?s=Bazaar&ss=mk&screen=buyorders&filter=co",
            HvRoute.Armory to "?s=Bazaar&ss=am&screen=modify&filter=equipped&eqids%5B%5D=1001",
        ).forEach { (route, query) ->
            val current = requireNotNull(HvWorld.Persistent.baseUrl.resolve(query))
            val document = Jsoup.parse(fixture("persistent.html"))
            document.selectFirst("#recoverform")!!.attr("action", current.toString())
            val recovery = HvNavigationParser.parse(document.outerHtml(), HvWorld.Persistent, route, current)
                ?.stamina?.recovery

            assertEquals(current, recovery?.actionUrl)
            assertEquals(listOf("recover" to "stamina"), recovery?.fields)
        }
    }

    @Test
    fun emptyRecoveryActionUsesCurrentSubpageInsteadOfSectionRoot() {
        val current = "https://hentaiverse.org/?s=Character&ss=ab&tree=twohanded".toHttpUrl()
        val document = Jsoup.parse(fixture("persistent.html"))
        document.selectFirst("#recoverform")!!.removeAttr("action")

        assertEquals(current, HvNavigationParser.parse(document.outerHtml(), HvWorld.Persistent,
            HvRoute.Abilities, current)?.stamina?.recovery?.actionUrl)
    }

    @Test
    fun duplicateRouteOrDifferentCurrentWorldCannotEnableRecovery() {
        val current = "https://hentaiverse.org/?s=Character&ss=ab&tree=onehanded".toHttpUrl()
        val document = Jsoup.parse(fixture("persistent.html"))
        val form = document.selectFirst("#recoverform")!!
        listOf("s" to "Character", "s" to "Battle", "ss" to "ab", "ss" to "eq").forEach { (name, value) ->
            val duplicate = current.newBuilder().addQueryParameter(name, value).build()
            form.attr("action", duplicate.toString())
            assertNull(HvNavigationParser.parse(document.outerHtml(), HvWorld.Persistent,
                HvRoute.Abilities, current)?.stamina?.recovery)
        }
        form.attr("action", current.toString())
        assertNull(HvNavigationParser.parse(document.outerHtml(), HvWorld.Persistent,
            HvRoute.Abilities, HvRoute.Abilities.url(HvWorld.Isekai))?.stamina?.recovery)
        assertNull(HvNavigationParser.parse(document.outerHtml(), HvWorld.Persistent,
            HvRoute.Abilities, HvRoute.Equipment.url(HvWorld.Persistent))?.stamina?.recovery)
    }

    @Test
    fun missingOneSectionOrOnlyUnknownLinksIsNotRecognizedAsGameNavigation() {
        val missing = Jsoup.parse(fixture("persistent.html"))
        missing.selectFirst("#child_Bazaar")!!.remove()
        assertNull(parse(missing.outerHtml()))

        val unknown = Jsoup.parse(fixture("persistent.html"))
        unknown.select("#child_Battle [onclick]").forEach {
            it.attr("onclick", "document.location='https://example.org/'")
        }
        assertNull(parse(unknown.outerHtml()))
        assertNotNull(parse(fixture("persistent.html")))
    }

    private fun parse(html: String) = HvNavigationParser.parse(html, HvWorld.Persistent, HvRoute.Character)

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/navigation/$name")).readText()
}
