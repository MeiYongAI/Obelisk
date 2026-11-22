package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.NativeAction
import com.mitahv.mobile.model.RiddleChoice
import com.mitahv.mobile.model.RiddleImage
import com.mitahv.mobile.model.RiddlePage
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Reads the supplied form and literal selection limits without executing any page script. */
object RiddlePageParser {
    fun isRiddlePage(html: String): Boolean = isRiddlePage(Jsoup.parse(html))

    fun isRiddlePage(document: Document): Boolean = document.selectFirst("#riddlemaster") != null

    fun parse(html: String, world: HvWorld, currentUrl: HttpUrl): RiddlePage =
        parse(Jsoup.parse(html, currentUrl.toString()), world, currentUrl)

    fun parse(document: Document, world: HvWorld, currentUrl: HttpUrl): RiddlePage {
        require(world.containsUrl(currentUrl)) { "Riddle page is outside the selected world" }
        val root = requireNotNull(document.select("#riddlemaster").singleOrNull()) { "RiddleMaster page was not found" }
        val form = requireNotNull(root.selectFirst("form#riddleform")) { "RiddleMaster form was not found" }
        require(form.attr("method").equals("post", true)) { "Unsupported RiddleMaster form method" }
        val target = requireNotNull(currentUrl.resolve(form.attr("action"))) { "RiddleMaster form URL was not found" }
        require(world.containsUrl(target)) { "RiddleMaster form is outside the selected world" }
        val imageSource = requireNotNull(root.selectFirst("#riddleimage > img")?.attr("src")?.takeIf(String::isNotBlank)) {
            "RiddleMaster image was not found"
        }
        val choices = form.select("#riddler1 input[type=checkbox][name]").filter { it.attr("name") == "riddleanswer[]" }.map { node ->
            RiddleChoice(node.attr("value"), node.closest("label").hvText(), node.hasAttr("checked"), enabled(node))
        }
        require(choices.isNotEmpty() && choices.all { it.value.isNotBlank() && it.label.isNotBlank() } &&
            choices.map { it.value }.distinct().size == choices.size) { "RiddleMaster options were not found" }
        val scripts = document.select("script").map { it.data() }.filter { CHECK_FUNCTION.containsMatchIn(it) }.joinToString("\n")
        val limits = requireNotNull(SELECTION_LIMITS.find(scripts)) { "RiddleMaster selection limits were not found" }
        val minimum = requireNotNull(limits.groupValues[1].toIntOrNull()) + 1
        val maximum = requireNotNull(limits.groupValues[2].toIntOrNull()) - 1
        require(minimum > 0 && maximum >= minimum && maximum <= choices.size) { "Invalid RiddleMaster selection limits" }
        val button = form.select("input[type=submit][name]").singleOrNull { it.id() == "riddlesubmit" && it.attr("name") == "riddlesubmit" }
        val submitAction = button?.let { submit ->
            // The supplied check_submit_button enables an initially disabled submit control
            // once the person selects the permitted count; disabled is not a permanent lock.
            val hidden = form.select("input[type=hidden][name]").filter(::enabled).map { it.attr("name") to it.attr("value") }
            NativeAction(target, method = "POST", fields = hidden + (submit.attr("name") to submit.attr("value")),
                label = submit.attr("value").ifBlank { "提交答案" })
        }
        val deadline = END_TIME.find(scripts)?.groupValues?.get(1)?.toLongOrNull()
        val serverTime = SERVER_TIME.find(scripts)?.groupValues?.get(1)?.toLongOrNull()
        val duration = if (deadline != null && serverTime != null) (deadline - serverTime).takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() else null
        return RiddlePage(
            image = RiddleImage(imageSource, resourceUrl(imageSource, world, currentUrl)),
            choices = choices,
            submitAction = submitAction,
            minimumSelections = minimum,
            maximumSelections = maximum,
            instruction = root.select("#riddlemid > p").joinToString("\n") { it.hvText() },
            referenceImageUrl = root.selectFirst("#riddlepon a[href]")?.attr("href")?.let { resourceUrl(it, world, currentUrl) },
            timeLimitSeconds = duration,
            displayedSecondsRemaining = root.selectFirst("#riddlecounter").hvText().toIntOrNull()?.takeIf { it >= 0 },
        )
    }

    private fun enabled(node: Element): Boolean = !node.hasAttr("disabled") &&
        node.parents().none { it.tagName() == "fieldset" && it.hasAttr("disabled") }

    private fun resourceUrl(source: String, world: HvWorld, current: HttpUrl): HttpUrl? {
        // Browser Save rewrites the original image URL and may discard its query entirely.
        // Retain that reference for offline injection; never guess an online endpoint from it.
        if (!source.contains("://") && SAVED_RESOURCE.containsMatchIn(source)) return null
        val target = current.resolve(source) ?: return null
        val allowedHosts = if (world == HvWorld.Isekai) setOf(HvSite.Alt.host) else HvSite.entries.map { it.host }.toSet()
        return target.takeIf { it.scheme == "https" && it.port == 443 && it.host in allowedHosts &&
            it.username.isEmpty() && it.password.isEmpty() && it.fragment == null }
    }

    private val CHECK_FUNCTION = Regex("""function\s+check_submit_button\s*\(""")
    private val SELECTION_LIMITS = Regex("""selected_count\s*>\s*(\d+)\s*&&\s*selected_count\s*<\s*(\d+)""")
    private val END_TIME = Regex("""\b(?:var|let|const)\s+end_time\s*=\s*(\d+)\s*;""")
    private val SERVER_TIME = Regex("""\b(?:var|let|const)\s+time_skew\s*=\s*(\d+)\s*-\s*Math\.floor\(\s*Date\.now\(\s*\)\s*/\s*1000\s*\)""")
    private val SAVED_RESOURCE = Regex("""(?:^|/)[^/]*_files(?:_[^/]*)?/""")
}
