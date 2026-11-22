package com.mitahv.mobile.data

import com.mitahv.mobile.model.ArmoryRepairConfig
import com.mitahv.mobile.model.ArmoryRepairMaterial
import com.mitahv.mobile.model.BazaarSectionsField
import com.mitahv.mobile.model.BazaarSectionsForm
import com.mitahv.mobile.model.NativeAction
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal object ArmoryRepairParser {
    fun parse(document: Document, basePostAction: NativeAction): BazaarSectionsForm? {
        val opener = document.select("#equipaction button:not([disabled])").firstOrNull {
            Regex("""\s*armory_repair\(\s*\)\s*;\s*return false\s*;?\s*""").matches(it.attr("onclick"))
        } ?: return null
        val script = document.select("script").joinToString("\n") { it.data() }
        val html = Regex("""function\s+armory_repair\(\)\s*\{[\s\S]*?innerHTML\s*=\s*`([^`]*?)`""")
            .find(script)?.groupValues?.get(1) ?: return null
        val dialog = Jsoup.parseBodyFragment(html)
        val submit = dialog.selectFirst("button[type=submit][name=action][value=repair]") ?: return null
        fun flag(name: String) = Regex("\\b${Regex.escape(name)}\\s*=\\s*(true|false)\\b")
            .find(script)?.groupValues?.get(1)?.toBooleanStrictOrNull()
        fun quantity(value: String) = Regex("[0-9][0-9,]*").find(value)?.value?.replace(",", "")?.toLongOrNull()
        val materials = dialog.select("#armory_repaircosts tr").mapNotNull { row ->
            val cells = row.select("td")
            val required = quantity(cells.getOrNull(0).hvText()) ?: return@mapNotNull null
            val name = cells.getOrNull(1).hvText().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val owned = quantity(cells.getOrNull(2).hvText()) ?: return@mapNotNull null
            ArmoryRepairMaterial(name, required, owned, row.attr("data-charmitem") == "1")
        }
        val checkbox = dialog.selectFirst("input[type=checkbox][name=replace_charms]")
        val config = ArmoryRepairConfig(
            needsBaseRepair = flag("needs_repair_base") ?: return null,
            missingBaseMaterials = flag("missing_repair_base") ?: return null,
            missingCharmMaterials = flag("missing_repair_charm") ?: return null,
            materials = materials,
            replacementField = checkbox?.attr("name"),
            replaceCharmsByDefault = checkbox?.hasAttr("checked") ?: true,
        )
        val fields = listOfNotNull(checkbox?.let {
            BazaarSectionsField(it.attr("name"), it.closest("label").hvText(),
                it.attr("value").ifBlank { "on" }, "checkbox", checked = it.hasAttr("checked"))
        })
        val action = basePostAction.copy(method = "POST", label = opener.hvText(),
            fields = basePostAction.fields.filterNot { it.first == submit.attr("name") } +
                (submit.attr("name") to submit.attr("value")),
            confirmation = basePostAction.confirmation ?: dialog.select("p").take(2).joinToString("\n") { it.hvText() })
        return BazaarSectionsForm("armory-repair", "Repair", fields, listOf(action), armoryRepair = config)
    }
}
