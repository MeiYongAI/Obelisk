package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup

class CharacterPageParserTest {
    @Test
    fun sharedDocumentCanBeClassifiedAndParsedWithoutChangingItsContents() {
        val html = fixture("persistent.html")
        val world = HvWorld.Persistent
        val url = HvRoute.Character.url(world)
        val document = Jsoup.parse(html, url.toString())
        val before = document.outerHtml()
        val expected = CharacterPageParser.parse(html)

        HvNavigationParser.parse(document, world, HvRoute.Character, url)
        assertFalse(RiddlePageParser.isRiddlePage(document))
        assertFalse(BattlePageParser.isBattlePage(document))
        assertEquals(expected, CharacterPageParser.parse(document))
        HvDataScriptRequests.fromPage(document, world, HvSite.Main, url)
        assertEquals(expected, CharacterPageParser.parse(document))
        assertEquals(before, document.outerHtml())
    }

    @Test
    fun parsesPersistentCharacterFixtureAndFullPersonaSlots() {
        val page = CharacterPageParser.parse(fixture("persistent.html"))

        assertEquals("2", page.personaId)
        assertEquals("Persona 2", page.personaName)
        assertEquals(3, page.personaOptions.size)
        assertEquals(3, page.usedPersonaCount)
        assertEquals(3, page.personaSlotCapacity)
        assertTrue(page.canSwitchPersona)
        assertTrue(page.canRenamePersona)
        assertFalse(page.canClonePersona)
        assertFalse(page.canCreateBlankPersona)
        assertTrue(page.canEditAttributes)
        assertEquals(61_496_539_988L, page.availableExperience)
        assertEquals(listOf(405, 455, 568, 567, 568, 568), page.attributes.map { it.value })
        assertEquals("+45", page.attributes.first().equipmentBonus)
        assertEquals("+503", page.attributes[2].secondaryBonus)
        assertEquals(256_912_513L, page.attributes.first().nextExperience)
        assertEquals("Equipment proficiency", page.proficiencyGroups.first().name)
        assertEquals("517.244", page.proficiencyGroups.first().values.first().value)
    }

    @Test
    fun parsesIsekaiCharacterFixtureAndAvailablePersonaSlot() {
        val page = CharacterPageParser.parse(fixture("isekai.html"))

        assertEquals("1", page.personaId)
        assertEquals(2, page.usedPersonaCount)
        assertEquals(3, page.personaSlotCapacity)
        assertTrue(page.canClonePersona)
        assertTrue(page.canCreateBlankPersona)
        assertTrue(page.canEditAttributes)
        assertEquals(10_322_973L, page.availableExperience)
        assertEquals(listOf(355, 354, 354, 354, 354, 354), page.attributes.map { it.value })
        assertEquals(52_190_842L, page.attributes.first().nextExperience)
    }

    @Test
    fun defaultsToFirstPersonaWhenSelectHasNoSelectedAttribute() {
        val document = Jsoup.parse(fixture("persistent.html"))
        document.select("#persona_form option").removeAttr("selected")

        val page = CharacterPageParser.parse(document.outerHtml())

        assertEquals("1", page.personaId)
        assertEquals("Persona 1", page.personaName)
        assertTrue(page.canSwitchPersona)
    }

    @Test
    fun readsScriptStateAndKeepsValueAsDisplayedValue() {
        val document = Jsoup.parse(fixture("isekai.html"))
        document.selectFirst("#str_display")!!.text("stale")
        document.selectFirst("#remaining_exp")!!.empty()
        val html = document.outerHtml().replace(
            "var attr_delta = {\"str\":0,",
            "var attr_delta = {\"str\":2,",
        )

        val page = CharacterPageParser.parse(html)

        assertEquals(357, page.attributes.first().value)
        assertEquals(2, page.attributes.first().pendingDelta)
        assertEquals(10_322_973L, page.availableExperience)
        assertTrue(page.canEditAttributes)
    }

    @Test
    fun readsBitmapNumbersWhenScriptStateIsAbsent() {
        val document = Jsoup.parse(fixture("isekai.html"))
        document.select("script").remove()
        document.selectFirst("#str_display")!!.html(
            """<div class="fr f4b"><div class="c43"></div><div class="c45"></div><div class="c45"></div></div>""",
        )
        document.selectFirst("#remaining_exp")!!.html(
            """<div class="fr f4b"><div class="c41"></div><div class="c40"></div><div class="c4b"></div><div class="c43"></div><div class="c42"></div><div class="c42"></div><div class="c4b"></div><div class="c49"></div><div class="c47"></div><div class="c43"></div></div>""",
        )
        val page = CharacterPageParser.parse(document.outerHtml())

        assertEquals(355, page.attributes.first().value)
        assertEquals(10_322_973L, page.availableExperience)
        assertTrue(page.canEditAttributes)
    }

    @Test
    fun missingNumericStateOrFormFieldDisablesAttributeSubmission() {
        val missingExperience = Jsoup.parse(fixture("isekai.html"))
        missingExperience.select("script").remove()
        missingExperience.selectFirst("#remaining_exp")!!.empty()
        assertFalse(CharacterPageParser.parse(missingExperience.outerHtml()).canEditAttributes)

        val missingValue = Jsoup.parse(fixture("isekai.html"))
        missingValue.select("script").remove()
        missingValue.selectFirst("#str_display")!!.empty()
        assertFalse(CharacterPageParser.parse(missingValue.outerHtml()).canEditAttributes)

        val missingFormField = Jsoup.parse(fixture("isekai.html"))
        missingFormField.select("#attr_form input[name=str_delta]").remove()
        assertFalse(CharacterPageParser.parse(missingFormField.outerHtml()).canEditAttributes)
    }

    @Test
    fun disabledCreationControlsAndMissingPersonaFormAreNotEnabled() {
        val document = Jsoup.parse(fixture("isekai.html"))
        document.select("#persona_new img").forEach { element ->
            element.attr("src", element.attr("src").replace(".png", "_d.png"))
        }
        val page = CharacterPageParser.parse(document.outerHtml())
        assertFalse(page.canClonePersona)
        assertFalse(page.canCreateBlankPersona)

        document.select("#persona_form").remove()
        val missingForm = CharacterPageParser.parse(document.outerHtml())
        assertFalse(missingForm.canSwitchPersona)
        assertFalse(missingForm.canRenamePersona)
        assertFalse(missingForm.canClonePersona)
    }

    @Test
    fun readsServerMessagesFromMessageBox() {
        val html = fixture("isekai.html").replace(
            "<body>",
            """<body><div id="messagebox_inner"><p class="messagebox_error">The persona could not be created.</p></div>""",
        )
        assertEquals(
            listOf("The persona could not be created."),
            CharacterPageParser.parse(html).messages,
        )
    }

    @Test
    fun parsesDirectChildProficiencyAndStatSections() {
        val page = CharacterPageParser.parse(
            """
            <div id="mainpane">
              <table id="attr_table"><tbody><tr>
                <td>Strength</td><td id="str_display">355</td><td></td><td>+12</td>
              </tr></tbody></table>
              <div id="prof_outer">
                <div>
                  <div><div><div>Equipment proficiency</div></div></div>
                  <table><tbody><tr><td>One-handed</td><td>517.244</td></tr></tbody></table>
                </div>
              </div>
              <div id="stats_scrollable">
                <div class="spc">Mainhand Attack</div>
                <table><tbody><tr><td>4629</td><td>Crushing Damage</td></tr></tbody></table>
              </div>
            </div>
            """.trimIndent(),
        )

        assertEquals("Equipment proficiency", page.proficiencyGroups.single().name)
        assertEquals("One-handed", page.proficiencyGroups.single().values.single().label)
        assertEquals("517.244", page.proficiencyGroups.single().values.single().value)
        assertEquals("Mainhand Attack", page.statGroups.single().name)
        assertEquals("Crushing Damage", page.statGroups.single().values.single().label)
        assertEquals("4629", page.statGroups.single().values.single().value)
        assertFalse(page.canEditAttributes)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/character/$name")).readText()
}
