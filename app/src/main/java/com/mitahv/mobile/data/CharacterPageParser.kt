package com.mitahv.mobile.data

import com.mitahv.mobile.model.CharacterAttribute
import com.mitahv.mobile.model.CharacterPage
import com.mitahv.mobile.model.CharacterStat
import com.mitahv.mobile.model.CharacterStatGroup
import com.mitahv.mobile.model.PersonaOption
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object CharacterPageParser {
    fun parse(html: String): CharacterPage = parse(Jsoup.parse(html))

    fun parse(document: Document): CharacterPage {
        require(document.selectFirst("#mainpane #attr_table") != null) {
            "Character page structure was not found"
        }

        val scripts = document.select("script").joinToString("\n") { it.data() }
        val currentAttributes = scriptObject(scripts, "attr_current")
        val attributeDeltas = scriptObject(scripts, "attr_delta")
        val usableExperience = scriptLong(scripts, "usable_exp")
            ?: document.selectFirst("#remaining_exp").hvText().gameLong()
        val personaForm = document.selectFirst("#persona_form")
        val personaSelect = personaForm?.selectFirst("select[name=persona_set]")
        val personaOptions = personaSelect?.select("option").orEmpty()
        // A native select defaults to its first option when no selected attribute is present.
        val selectedPersona = personaOptions.firstOrNull { it.hasAttr("selected") }
            ?: personaOptions.firstOrNull()
        val hasPersonaForm = personaForm?.attr("method").equals("post", ignoreCase = true) &&
            personaSelect != null && !personaSelect.hasAttr("disabled") &&
            !selectedPersona?.attr("value").isNullOrBlank()
        val usedSlotsText = document.selectFirst("#persona_used").hvText()
        val slotCounts = SLOT_COUNTS.find(usedSlotsText)
        val usedCount = slotCounts?.groupValues?.get(1)?.toIntOrNull()
        val capacity = slotCounts?.groupValues?.get(2)?.toIntOrNull()
        val hasFreeSlots = usedCount != null && capacity != null && usedCount < capacity
        val createActions = document.select("#persona_new img[onclick]")
            .filter { !it.hasAttr("disabled") && !it.attr("src").substringBefore('?').endsWith("_d.png") }
            .map { it.attr("onclick") }
        val attributeForm = document.selectFirst("#attr_form")
        var attributesComplete = usableExperience != null && usableExperience >= 0 &&
            attributeForm?.attr("method").equals("post", ignoreCase = true) &&
            attributeForm?.selectFirst("input[name=attr_apply]")?.attr("value") == "1"
        val attributes = document.select("#attr_table tbody tr").mapNotNull { row ->
            val display = row.selectFirst("td[id$=_display]") ?: return@mapNotNull null
            val key = display.id().removeSuffix("_display")
            if (key !in ATTRIBUTE_KEYS) return@mapNotNull null
            val cells = row.select("td")
            val deltaInput = attributeForm?.selectFirst("input[name=${key}_delta]")
            val pendingDelta = attributeDeltas.intValue(key)
                ?: deltaInput?.attr("value")?.toIntOrNull()
                ?: row.selectFirst("td[id$=_text]").hvText().gameInt()
            val baseValue = currentAttributes.intValue(key)
            val value = if (baseValue != null && pendingDelta != null) {
                (baseValue.toLong() + pendingDelta).takeIf { it in 0..Int.MAX_VALUE }?.toInt()
            } else {
                display.hvText().gameInt()
            }
            val nextExperience = row.selectFirst("td[id$=_left]").hvText().gameLong()
            attributesComplete = attributesComplete && value != null && value >= 0 &&
                pendingDelta != null && nextExperience != null && nextExperience >= 0 &&
                deltaInput != null && !deltaInput.hasAttr("disabled")
            CharacterAttribute(
                key = key,
                name = cells.firstOrNull().hvText(),
                value = value ?: 0,
                equipmentBonus = cells.getOrNull(2).hvText(),
                secondaryBonus = cells.getOrNull(3).hvText(),
                pendingDelta = pendingDelta ?: 0,
                nextExperience = nextExperience ?: 0L,
            )
        }

        return CharacterPage(
            worldName = document.selectFirst("#world_readout").hvText(),
            level = document.selectFirst("#level_readout").hvText(),
            stamina = document.selectFirst("#stamina_readout").hvText(),
            personaId = selectedPersona?.attr("value").orEmpty(),
            personaName = selectedPersona.hvText(),
            personaOptions = personaOptions.map { PersonaOption(it.attr("value"), it.hvText()) },
            usedPersonaSlots = usedSlotsText,
            usedPersonaCount = usedCount,
            personaSlotCapacity = capacity,
            canClonePersona = hasPersonaForm && hasFreeSlots &&
                personaForm?.selectFirst("input[name=persona_create]") != null &&
                createActions.any { CLONE_ACTION.containsMatchIn(it) },
            canCreateBlankPersona = hasPersonaForm && hasFreeSlots &&
                personaForm?.selectFirst("input[name=persona_create]") != null &&
                createActions.any { BLANK_ACTION.containsMatchIn(it) },
            availableExperience = usableExperience ?: 0L,
            attributes = attributes,
            proficiencyGroups = document.select("#prof_outer > div").mapNotNull { it.toStatGroup() },
            statGroups = document.select("#stats_scrollable > .spc").mapNotNull { heading ->
                val table = heading.nextElementSibling()?.takeIf { it.tagName() == "table" }
                    ?: return@mapNotNull null
                CharacterStatGroup(
                    name = heading.hvText(),
                    values = table.select("tbody tr").mapNotNull { row ->
                        val cells = row.select("td")
                        if (cells.size < 2) null else CharacterStat(
                            label = cells[1].hvText(),
                            value = cells[0].hvText(),
                        )
                    },
                )
            },
            canEditAttributes = attributesComplete &&
                attributes.map { it.key }.toSet() == ATTRIBUTE_KEYS &&
                attributes.size == ATTRIBUTE_KEYS.size,
            canRenamePersona = hasPersonaForm &&
                personaForm?.selectFirst("input[name=persona_rename]") != null &&
                document.select("#persona_select [onclick]")
                    .any { RENAME_ACTION.containsMatchIn(it.attr("onclick")) },
            canSwitchPersona = hasPersonaForm,
            messages = document.select("#messagebox_inner > p").map { it.hvText() }
                .filter(String::isNotBlank),
        )
    }

    private fun Element.toStatGroup(): CharacterStatGroup? {
        val title = children().firstOrNull { it.tagName() == "div" }.hvText()
        val table = children().firstOrNull { it.tagName() == "table" } ?: return null
        if (title.isBlank()) return null
        return CharacterStatGroup(
            name = title,
            values = table.select("tbody tr").mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 2) null else CharacterStat(
                    label = cells[0].hvText(),
                    value = cells[1].hvText(),
                )
            },
        )
    }

    private fun scriptObject(scripts: String, name: String): JSONObject? =
        Regex("""\b${Regex.escape(name)}\s*=\s*(\{[^{}]*\})\s*;""")
            .find(scripts)?.groupValues?.get(1)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun scriptLong(scripts: String, name: String): Long? =
        Regex("""\b${Regex.escape(name)}\s*=\s*(-?\d+)\s*;""")
            .find(scripts)?.groupValues?.get(1)?.toLongOrNull()

    private fun JSONObject?.intValue(key: String): Int? =
        this?.opt(key)?.toString()?.toIntOrNull()

    private fun String.gameLong(): Long? = replace(",", "").trim().toLongOrNull()

    private fun String.gameInt(): Int? =
        gameLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private val ATTRIBUTE_KEYS = setOf("str", "dex", "agi", "end", "int", "wis")
    private val SLOT_COUNTS = Regex("""(\d+)\s*/\s*(\d+)""")
    private val CLONE_ACTION = Regex("""\bdo_persona_create_clone\s*\(""")
    private val BLANK_ACTION = Regex("""\bdo_persona_create_blank\s*\(""")
    private val RENAME_ACTION = Regex("""\bdo_persona_rename\s*\(""")
}

