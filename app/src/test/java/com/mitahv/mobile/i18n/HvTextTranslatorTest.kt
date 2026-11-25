package com.mitahv.mobile.i18n

import java.io.File
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HvTextTranslatorTest {
    private val asset by lazy {
        sequenceOf(File("src/main/assets/hv_zh.json"), File("app/src/main/assets/hv_zh.json"))
            .first { it.isFile }.readText()
    }
    private val translator by lazy { HvTextTranslator(asset) }
    private fun page(section: String, subsection: String) =
        translator.translatorFor("https://hentaiverse.org/?s=$section&ss=$subsection")

    @Test fun selectedUserscriptIsTheOnlyImportedCatalog() {
        val metadata = JSONObject(asset)
        assertEquals("JPX-PLUS/HentaiVerse-Chinese.user.js", metadata.getString("source"))
        assertEquals("2026.11.20.3", metadata.getString("version"))
        assertEquals("MIT", metadata.getString("license"))
        assertEquals(0, metadata.getJSONObject("extraction").getJSONArray("skippedDynamicExpressions").length())
        assertEquals("永久区", translator.translate("Persistent"))
        assertEquals("生命饮剂", translator.translate("Health Draught"))
        assertEquals("已完全升级", translator.translate("Fully Upgraded"))
    }

    @Test fun phrasesAndCompoundAffixesUseSourceTermsWithoutRewritingLongerWords() {
        val character = page("Character", "ch")
        assertEquals("火焰 fireball fireproof", character("Fire fireball fireproof"))
        assertEquals("攻击暴击伤害", character("Attack Crit Damage"))
        val equipment = page("Character", "eq")
        assertTrue(equipment("Legendary Ethereal Rapier of Slaughter").contains("西洋剑"))
        assertFalse(equipment("Legendary Leather Boots of the Wind-waker").contains("-waker"))
        assertFalse(equipment("Legendary Ethereal Rapier of Slaughter").contains("<span"))
    }

    @Test fun sameTermRespectsPageScopeAndCompiledDictionariesAreReused() {
        assertEquals("迅捷", page("Bazaar", "ml")("Swiftness"))
        assertEquals("攻击速度", page("Bazaar", "am")("Swiftness"))
        assertEquals("已存在此名字怪物", page("Bazaar", "ml")("A monster with that name already exists."))
        assertSame(page("Bazaar", "am"), page("Bazaar", "am"))
    }

    @Test fun fragmentMatchingKeepsLongestPhrasesBoundariesAndWhitespace() {
        val local = HvTextTranslator("""{"groups":[{"rules":[
            {"text":"Fire","value":"F"},{"text":"Fire Shield","value":"FS"},
            {"text":" of Ice","value":"+I"},{"text":"[Ice]","value":"IC"},
            {"text":"❌ the ","value":"X"},{"text":"Level ","value":"L"},
            {"text":"Anti-","value":"A"},{"text":"Anti-Block","value":"AB"}
        ]}]}""")
        assertEquals("FS+I / IC", local.translate("FIRE\tShield   of Ice / [Ice]"))
        assertEquals("fire-proof _fire fire2 F", local.translate("fire-proof _fire fire2 Fire"))
        assertEquals("XL7", local.translate("❌ the Level   7"))
        assertEquals("AB ABlock-ext", local.translate("Anti-Block Anti-Block-ext"))
    }

    @Test fun equipmentUsesPlainSourceTermsWhileOriginalNamesKeepTheirSymbols() {
        val equipment = page("Character", "eq")
        val cases = mapOf(
            "Main Hand" to "主手", "Off Hand" to "副手", "Body" to "身体",
            "Hands" to "手部", "Legs" to "腿部", "Feet" to "足部",
            "Legendary" to "传奇", "Peerless" to "无双",
            "Shortsword" to "短剑(单)", "Staff" to "法杖(双)",
            "Helmet" to "头盔", "Gloves" to "手套", "Boots" to "靴子",
        )
        cases.forEach { (source, expected) -> assertEquals(source, expected, equipment(source)) }
        assertEquals("🌟 自定义之刃 🗡️", equipment("🌟 自定义之刃 🗡️"))
        val battle = translator.battleLogTranslatorFor("https://hentaiverse.org/")
        assertEquals("击败了 Fire 🛡️ and Power。", battle("Fire 🛡️ and Power has been defeated."))
        val messages = AppMessageTranslator(chinese = true, gameTranslate = equipment)
        assertEquals("装备名称：🌟 Custom Staff 🗡️", messages.translate("装备名称：🌟 Custom Staff 🗡️"))
    }

    @Test fun originalEquipmentEnergyFieldsKeepUnavailableValuesIntact() {
        val fixtures = listOf("armory/upgrade-unbound.html", "lottery/past-weapon.html", "lottery/past-armor.html", "battle-modes/iw.html")
        fixtures.forEach { path ->
            val html = requireNotNull(javaClass.getResource("/$path")).readText()
            val energy = Jsoup.parse(html).select(".eqr span").map { it.text() }.first { it == "Energy: N/A" }
            listOf("Character" to "eq", "Bazaar" to "am", "Bazaar" to "lt", "Bazaar" to "la", "Battle" to "iw").forEach { (section, subsection) ->
                assertEquals("$path on $section/$subsection", "能量: 不适用", page(section, subsection)(energy))
            }
        }
        val equipment = page("Character", "eq")
        assertEquals("不适用", equipment("N/A"))
        assertEquals("能量: 104%", equipment("Energy: 104%"))
        assertEquals("N/ABC 0xA 0xD 1.0E-3 20 s", equipment("N/ABC 0xA 0xD 1.0E-3 20 s"))
    }

    @Test fun attributeInitialsOnlyTranslateWholeEquipmentLabels() {
        val equipment = page("Character", "eq")
        val initials = mapOf("S" to "力", "D" to "灵", "A" to "敏", "E" to "体", "I" to "智", "W" to "慧")
        initials.forEach { (source, expected) ->
            assertEquals(expected, equipment(source))
            assertEquals(source.lowercase(), equipment(source.lowercase()))
            assertEquals(source, page("Character", "ab")(source))
            assertEquals(source, page("Bazaar", "mm")(source))
        }
        assertEquals("I need a socket.", equipment("I need a socket."))
        assertEquals("3 技能点", page("Character", "ab")("3 AP"))
        val battle = translator.battleLogTranslatorFor("https://hentaiverse.org/")
        assertEquals("击败了 A N/A I。", battle("A N/A I has been defeated."))
        val messages = AppMessageTranslator(chinese = true, gameTranslate = equipment)
        assertEquals("装备名称：A N/A I", messages.translate("装备名称：A N/A I"))
    }

    @Test fun settingsAutoSellAndSalvageOptionsTranslateTheirQualityCapture() {
        val html = requireNotNull(javaClass.getResource("/character-sections/settings.html")).readText()
        val options = Jsoup.parse(html).select("#settings_autosalvage option").map { it.text() }.distinct()
        val qualities = mapOf("Crude" to "粗糙", "Fair" to "普通", "Average" to "中等", "Superior" to "上等",
            "Exquisite" to "精良", "Magnificent" to "史诗", "Legendary" to "传奇")
        val settings = page("Character", "se")
        for ((action, translatedAction) in mapOf("Sell" to "自动出售", "Salvage" to "自动分解")) {
            qualities.forEach { (quality, translatedQuality) ->
                val label = "$action $quality"
                assertTrue("Missing source option: $label", label in options)
                assertEquals("$translatedAction $translatedQuality 或更低品质", settings(label))
            }
        }
        assertEquals("不自动出售", settings("No Auto-Sell"))
        assertEquals("不自动分解", settings("No Auto-Salvage"))
    }

    @Test fun lotteryHistoryLabelsAndOrdinalDatesUseSelectedUserscriptTerms() {
        for (host in listOf("hentaiverse.org", "alt.hentaiverse.org")) {
            for (subsection in listOf("lt", "la")) {
                val lottery = translator.translatorFor("https://$host/?s=Bazaar&ss=$subsection&lottery=4817")
                assertEquals("装备中奖者", lottery("Equip Winner"))
                assertEquals("核心中奖者", lottery("Core Winner"))
                assertEquals("获奖者", lottery("Winner"))
                assertEquals("二等奖", lottery("2nd Prize"))
                assertEquals("三等奖", lottery("3rd Prize"))
                assertEquals("四等奖", lottery("4th Prize"))
                assertEquals("五等奖", lottery("5th Prize"))
                assertEquals("本期彩票", lottery("Current Drawing"))
                assertEquals("本期彩票售卖已结束", lottery("Today's ticket sale is closed."))
                assertEquals("一等奖 · 11 月 21 日", lottery("Grand Prize for November 21st:"))
                assertEquals("一等奖 · 12 月 31 日", lottery("Grand Prize for December 31st:"))
            }
        }
    }

    @Test fun battleCapturesKeepPlayerNamesWhileTranslatingTypedGameTerms() {
        val battle = translator.battleLogTranslatorFor("https://alt.hentaiverse.org/isekai/")
        val hit = battle("You hit Fire and Power, causing 123 points of Fire damage.")
        assertTrue(hit, hit.contains("Fire and Power"))
        assertTrue(hit, hit.contains("火焰"))
        assertFalse(hit, hit.contains("points of"))
        assertEquals("击败了 Fire and Power。", battle("Fire and Power has been defeated."))
        assertEquals("你施放了 治疗术。", battle("You cast Cure."))
        assertEquals("Fire and Power 送来了礼物！", page("Bazaar", "ml")("Fire and Power brought you a gift!"))
    }

    @Test fun nativeBattleEventsPreserveNamesAndTranslateTheirSourceRendererTemplates() {
        val battle = translator.battleLogTranslatorFor("https://hentaiverse.org/")
        assertEquals("你施放了 治疗术，为 Fire and Power 恢复了 100 点生命。",
            battle("You cast Cure, healing Fire and Power for 100 points of health."))
        assertEquals("已扫描 Fire and Power", battle("Scanning Fire and Power"))
        assertEquals("Fire and Power从混乱中脱离。", battle("Fire and Power got knocked out of confuse."))
        assertEquals("你恢复了 80 点魔力。", battle("You recover 80 MP."))
        val unknown = "Fire and Power invents a new move with Cure."
        assertEquals(unknown, battle(unknown))
        assertEquals("Fire and Power施放了 Fire Power。", battle("Fire and Power casts Fire Power."))
        val powerup = battle("Fire and Power drops a Health Gem powerup!")
        assertTrue(powerup, powerup.contains("Fire and Power"))
        assertTrue(powerup, powerup.contains("生命宝石"))
        val resisted = battle("Fire and Power was blasted for 200 Fire damage. (25% resisted)")
        assertTrue(resisted, resisted.contains("Fire and Power"))
        assertTrue(resisted, resisted.contains("抵抗 25%"))
    }

    @Test fun battleItemLabelsAndGroupsUseTheBattleScopedDictionary() {
        val battle = translator.translatorFor("https://hentaiverse.org/?s=Battle&ss=ar", battle = true)
        assertEquals("生命药水", battle("Health Potion"))
        assertEquals("风暴魔药", battle("Infusion of Storms"))
        assertEquals("神之卷轴", battle("Scroll of the Gods"))
        assertEquals("道具", battle("Items"))
        assertEquals("卷轴", battle("Scrolls"))
        assertEquals("魔药", battle("Infusions"))
    }

    @Test fun battleSpiritResourceDoesNotInheritTheStanceLabel() {
        val battle = translator.translatorFor("https://hentaiverse.org/?s=Battle&ss=ar", battle = true)
        assertEquals("灵力", battle("Spirit"))
        assertEquals("灵力", battle("spirit"))
        assertEquals("灵动架势", battle("Spirit Stance"))
    }

    @Test fun staticTemplateCallbacksTranslateItemsAndKeepPersonaNamesLiteral() {
        val bazaar = page("Bazaar", "is")
        val purchase = bazaar("Are you sure you wish to purchase 2 Health Potion for 300 credits ?")
        assertTrue(purchase, purchase.contains("生命药水"))
        assertTrue(purchase, purchase.contains("300 信用点"))
        assertFalse(purchase, purchase.contains("Health Potion"))
        val persona = "Fire and Power"
        val original = "Are you sure you wish to create a new persona with the same attribute, slot, equipment and ability assignments as \"$persona\"? This action is irreversible, and created personas cannot be deleted."
        val translated = page("Character", "ch")(original)
        assertTrue(translated, translated.contains(persona))
        assertTrue(translated, translated.startsWith("是否确认创建"))
    }
}
