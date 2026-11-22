package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RiddlePageParserTest {
    @Test fun suppliedManualFormRetainsOptionsSubmitControlAndTimingWithoutChoosingAnswers() {
        val page = parse()
        assertEquals(listOf("ts", "ra", "fs", "rd", "pp", "aj"), page.choices.map { it.value })
        assertEquals(listOf("Twilight Sparkle", "Rarity", "Fluttershy", "Rainbow Dash", "Pinkie Pie", "Applejack"), page.choices.map { it.label })
        assertTrue(page.choices.none { it.checked })
        assertTrue(page.choices.all { it.enabled })
        assertEquals(1, page.minimumSelections)
        assertEquals(3, page.maximumSelections)
        assertEquals(30, page.timeLimitSeconds)
        assertEquals(8, page.displayedSecondsRemaining)
        assertTrue(page.instruction.contains("提交答案"))
        assertEquals("./The HentaiVerse_files/riddlemaster", page.image.source)
        assertNull(page.image.url)
        assertEquals("https://alt.hentaiverse.org/y/ponychart.jpg", page.referenceImageUrl.toString())
        assertEquals(current, page.submitAction!!.url)
        assertEquals(listOf("riddlesubmit" to "提交答案"), page.submitAction.fields)
    }

    @Test fun manualSubmissionPreservesRepeatedAnswerFieldsInDomOrderAndActualButtonValue() {
        val page = parse()
        // Arbitrary protocol values test encoding; these are not answers to the supplied image.
        val action = page.submit(linkedSetOf("aj", "ts", "rd"))
        assertEquals(listOf("riddleanswer[]" to "ts", "riddleanswer[]" to "rd", "riddleanswer[]" to "aj", "riddlesubmit" to "提交答案"), action.fields)
        val request = NativeRequests.build(HvWorld.Persistent, action, HvSite.Alt)
        assertEquals("POST", request.method)
        assertEquals(current, request.url)
        assertEquals(current.toString(), request.header("Referer"))
        assertTrue(request.body!!.isOneShot())
        val encoded = Buffer().apply { request.body!!.writeTo(this) }.readUtf8()
        assertEquals("riddleanswer%5B%5D=ts&riddleanswer%5B%5D=rd&riddleanswer%5B%5D=aj&riddlesubmit=%E6%8F%90%E4%BA%A4%E7%AD%94%E6%A1%88", encoded)
        assertFalse(encoded.contains("token"))
    }

    @Test fun zeroFourOrUnknownSelectionsCannotBuildASubmission() {
        val page = parse()
        listOf(emptySet(), setOf("ts", "ra", "fs", "rd"), setOf("unknown")).forEach { selections ->
            assertNotNull(page.validationError(selections))
            assertThrows(IllegalArgumentException::class.java) { page.submit(selections) }
        }
        assertNull(page.validationError(setOf("ra")))
        assertNull(page.validationError(setOf("ra", "fs")))
        assertNull(page.validationError(setOf("ra", "fs", "pp")))
    }

    @Test fun checkboxStateIsReadButInitiallyDisabledSubmitCanBeEnabledByManualSelection() {
        val document = Jsoup.parse(fixture())
        document.selectFirst("#riddlesubmit")!!.attr("disabled", "disabled")
        document.select("input[type=checkbox]")[0].attr("checked", "checked")
        document.select("input[type=checkbox]")[1].attr("disabled", "disabled")
        val page = parse(document.outerHtml())
        assertTrue(page.choices[0].checked)
        assertFalse(page.choices[1].enabled)
        assertNull(page.validationError(setOf("ts")))
        assertNotNull(page.validationError(setOf("ra")))
        assertThrows(IllegalArgumentException::class.java) { page.submit(setOf("ra")) }
    }

    @Test fun riddleDetectionWorksWhenSnapshotStillContainsAPreviousBattlePage() {
        val document = Jsoup.parse(fixture())
        // The supplied saved document contains both of these roots. Keep the old battle
        // marker in this synthetic wrapper to assert the dispatch requirement explicitly.
        document.body().prepend("<div id='battle_main'></div>")
        assertTrue(BattlePageParser.isBattlePage(document.outerHtml()))
        assertTrue(RiddlePageParser.isRiddlePage(document.outerHtml()))
        assertEquals(6, parse(document.outerHtml()).choices.size)
        document.select("#riddlemaster").remove()
        assertFalse(RiddlePageParser.isRiddlePage(document.outerHtml()))
    }

    @Test fun realLiveImageSourceIsPreservedWithoutInventingAnEndpoint() {
        val document = Jsoup.parse(fixture())
        // This URI is synthetic and tests resolution only; Save HTML lost the real image URL.
        document.selectFirst("#riddleimage > img")!!.attr("src", "/riddle-image?challenge=fixture-image")
        assertEquals("https://alt.hentaiverse.org/riddle-image?challenge=fixture-image", parse(document.outerHtml()).image.url.toString())
        document.selectFirst("#riddleimage > img")!!.attr("src", "https://example.test/image")
        assertNull(parse(document.outerHtml()).image.url)
        document.selectFirst("#riddleimage > img")!!.attr("src", "./The HentaiVerse_files_小马提交表单页面/riddlemaster")
        assertNull(parse(document.outerHtml()).image.url)
        document.selectFirst("#riddlepon a")!!.attr("href", "https://example.test/ponychart.jpg")
        assertNull(parse(document.outerHtml()).referenceImageUrl)
    }

    @Test fun formTargetMustStayInsideTheSelectedWorld() {
        val document = Jsoup.parse(fixture())
        listOf("https://example.test/?s=Battle&ss=ar", "https://alt.hentaiverse.org/isekai/?s=Battle&ss=ar").forEach { target ->
            document.selectFirst("#riddleform")!!.attr("action", target)
            assertThrows(IllegalArgumentException::class.java) { parse(document.outerHtml()) }
        }
        document.selectFirst("#riddleform")!!.attr("action", "https://hentaiverse.org/?s=Battle&ss=ar")
        assertEquals("hentaiverse.org", parse(document.outerHtml()).submitAction!!.url.host)
        document.selectFirst("#riddleform")!!.attr("method", "get")
        assertThrows(IllegalArgumentException::class.java) { parse(document.outerHtml()) }
    }

    @Test fun anActualIsekaiFormRetainsItsFullPathAndAdditionalQueries() {
        // Derived routing case; the supplied real form is Persistent on the alt host.
        val document = Jsoup.parse(fixture())
        document.selectFirst("#riddleform")!!.attr("action", "?s=Battle&ss=iw&filter=fixture")
        document.selectFirst("#riddleimage > img")!!.attr("src", "/isekai/riddle-image?challenge=fixture-image")
        val currentIsekai = "https://alt.hentaiverse.org/isekai/?s=Battle&ss=iw".toHttpUrl()
        val page = RiddlePageParser.parse(document.outerHtml(), HvWorld.Isekai, currentIsekai)
        val action = page.submit(setOf("fs"))
        assertEquals("https://alt.hentaiverse.org/isekai/?s=Battle&ss=iw&filter=fixture", action.url.toString())
        assertEquals("/isekai/riddle-image", page.image.url?.encodedPath)
        assertEquals("/isekai/", NativeRequests.build(HvWorld.Isekai, action).url.encodedPath)
    }

    @Test fun hiddenSuccessfulControlsArePreservedButMissingButtonsDoNotInventSubmitActions() {
        val document = Jsoup.parse(fixture())
        document.selectFirst("#riddleform")!!.prepend("<input type=hidden name=fixture_hidden value=kept><input type=hidden name=ignored value=discarded disabled>")
        val action = parse(document.outerHtml()).submit(setOf("pp"))
        assertEquals("kept", action.fields.toMap()["fixture_hidden"])
        assertFalse(action.fields.any { it.first == "ignored" })
        document.select("#riddlesubmit").remove()
        val noButton = parse(document.outerHtml())
        assertNull(noButton.submitAction)
        assertNotNull(noButton.validationError(setOf("pp")))
        assertThrows(IllegalArgumentException::class.java) { noButton.submit(setOf("pp")) }
    }

    @Test fun missingSelectionRuleOrAmbiguousDuplicateOptionsCannotBecomeAForm() {
        val document = Jsoup.parse(fixture())
        document.select("script").remove()
        assertThrows(IllegalArgumentException::class.java) { parse(document.outerHtml()) }
        val duplicate = Jsoup.parse(fixture())
        duplicate.select("input[type=checkbox]")[1].attr("value", "ts")
        assertThrows(IllegalArgumentException::class.java) { parse(duplicate.outerHtml()) }
    }

    private fun parse(html: String = fixture()) = RiddlePageParser.parse(html, HvWorld.Persistent, current)
    private fun fixture() = requireNotNull(javaClass.getResource("/riddle-master/manual-form.html")).readText()

    private companion object {
        val current = "https://alt.hentaiverse.org/?s=Battle&ss=ar".toHttpUrl()
    }
}
