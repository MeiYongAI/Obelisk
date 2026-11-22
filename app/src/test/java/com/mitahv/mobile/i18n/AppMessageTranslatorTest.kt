package com.mitahv.mobile.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppMessageTranslatorTest {
    private val english = AppMessageTranslator(chinese = false, gameTranslate = { error("English must retain the original game text") })

    @Test
    fun nativeMonsterLabelsAndSkillFieldsTranslateWithoutChangingNames() {
        val asset = sequenceOf(File("src/main/assets/hv_zh.json"), File("app/src/main/assets/hv_zh.json"))
            .first { it.isFile }.readText()
        val game = HvTextTranslator(asset).translatorFor("https://hentaiverse.org/?s=Bazaar&ss=ml")
        val chinese = AppMessageTranslator(chinese = true, gameTranslate = game)
        val cases = mapOf(
            "Unlock Slot" to "解锁栏位",
            "Create Monster" to "创建怪物",
            "Feed All" to "全部喂食",
            "Give Happy Pills" to "喂快乐药丸",
            "Chaos" to "混沌",
            "Slot" to "栏位",
            "Species" to "种族",
            "Judgement Strike · Damage" to "Judgement Strike · 伤害类型",
            "Fire · Skill type" to "Fire · 技能攻击类型",
            "Fire · Damage" to "Fire · 伤害类型",
            "Fire · Name: Power" to "Fire · 名称: Power",
            "Accuracy" to "准确",
        )
        cases.forEach { (source, expected) ->
            assertEquals(source, expected, chinese.translate(source))
            assertEquals(source, source, english.translate(source))
        }
        assertEquals("命中率", chinese.translate("命中率"))
        assertEquals("Accuracy", english.translate("命中率"))
    }

    @Test
    fun abilityHeadingsPointsEffectsAndResetCostsUseSelectedUserscriptTerms() {
        val asset = sequenceOf(File("src/main/assets/hv_zh.json"), File("app/src/main/assets/hv_zh.json"))
            .first { it.isFile }.readText()
        val game = HvTextTranslator(asset).translatorFor("https://hentaiverse.org/?s=Character&ss=ab")
        val chinese = AppMessageTranslator(chinese = true, gameTranslate = game)
        val cases = mapOf(
            "General" to "常规",
            "Deprecating I" to "减益魔法 I",
            "Deprecating II" to "减益魔法 II",
            "Supportive I" to "增益魔法 I",
            "Supportive II" to "增益魔法 II",
            "COST: 1 AP" to "消耗: 1 技能点",
            "ABILITY POINTS: 3" to "技能点: 3",
            "MASTERY POINTS: 1" to "精通点: 1",
            "Not Acquired" to "未获得",
            "At Maximum" to "已满级",
            "Next: Requires Level 10 and 3 Ability Points" to "下一等级：需要等级 10、3 技能点",
            "Cloth Spellacc\n0 Soul Fragments" to "布甲套法术命中值加成\n0 灵魂碎片",
            "HP Tank\n1,250 Soul Fragments" to "生命值增幅\n1,250 灵魂碎片",
            "Requires Level 240 and 5 Ability Points\n+1 Magic Accuracy" to "需要等级 240、5 技能点\n+1 魔法命中值",
        )
        cases.forEach { (source, expected) ->
            assertEquals(source, expected, chinese.translate(source))
            assertEquals(source, source, english.translate(source))
        }
        assertEquals("重置全部技能点和精通点分配？\n0 灵魂碎片", chinese.translate("重置全部技能点和精通点分配？\n0 Soul Fragments"))
        assertEquals("Reset all Ability Point and Mastery Point allocations?\n0 Soul Fragments",
            english.translate("重置全部技能点和精通点分配？\n0 Soul Fragments"))
    }

    @Test
    fun armoryMissingSoulFragmentsUsesSelectedLanguage() {
        val asset = sequenceOf(File("src/main/assets/hv_zh.json"), File("app/src/main/assets/hv_zh.json"))
            .first { it.isFile }.readText()
        val game = HvTextTranslator(asset).translatorFor("https://hentaiverse.org/?s=Bazaar&ss=am&screen=modify")
        val chinese = AppMessageTranslator(chinese = true, gameTranslate = game)
        val source = "You need an additional 100 soul fragments."

        assertEquals("还需 100 灵魂碎片", chinese.translate(source))
        assertEquals(source, english.translate(source))
    }

    @Test
    fun generatedTemplatesPreserveInsertedNamesIdsAndAmounts() {
        val cases = mapOf(
            "账号 000123" to "Account 000123",
            "重命名 皮甲「A」 · ID 324260" to "Rename 皮甲「A」 · ID 324260",
            "支付 10000 C 并领取" to "Pay 10000 C and claim",
            "Scrap Leather 不足（需要 24，持有 0）" to "Insufficient Scrap Leather (required 24, owned 0)",
            "Credits 不足（需要 123456789 C，持有 8765 C）" to "Insufficient Credits (required 123456789 C, owned 8765 C)",
            "装备名称：数量（损毁）\n收件人：数量\n主题：数量" to "Equipment name: 数量（损毁）\nRecipient: 数量\nSubject: 数量",
            "牺牲装备：皮甲 · ID 324260\n融合至：重命名的剑" to "Sacrificed equipment: 皮甲 · ID 324260\nFuse into: 重命名的剑",
            "将此受保护装备加入本次选择？\n数量" to "Include this protected equipment in the current selection?\n数量",
            "Judgement Strike · 名称最多 50 字" to "Judgement Strike · Name must not exceed 50 characters",
            "未取得 Physical Damage 的融合材料，请重新加载" to "Fusion materials for Physical Damage could not be loaded. Reload the page",
            "论坛返回 HTTP 429，请稍后重试。" to "The forum returned HTTP 429. Try again later.",
            "HV 返回 HTTP 503，请稍后重试。" to "HV returned HTTP 503. Try again later.",
        )
        cases.forEach { (source, expected) -> assertEquals(source, expected, english.translate(source)) }
        val unrecognized = "玩家留言：请帮忙买一件装备"
        assertEquals(unrecognized, english.translate(unrecognized))
    }

    @Test
    fun applicationMessagesRenderInEnglishAndChineseDelegatesGameTerms() {
        // Representative actual data/model output, including joined confirmations
        // and field summaries. The inserted game names are deliberately unchanged.
        val messages = listOf(
            "修理材料不足",
            "装备无需修理，也未选择更换护符和护符袋",
            "请选择装备",
            "每次只能牺牲一件装备",
            "未取得材料库存，请重新加载",
            "请填写收件人",
            "批数请输入整数",
            "批数不能小于 1",
            "批数不能大于 413",
            "每批价格最低为 10 C",
            "此价格区间须以 5 C 为步长",
            "未取得 Monster Lab 页面，请重新加载",
            "请选择 1–3 个选项",
            "护符袋：Silk Pouch（损毁）",
            "Scrap Leather：24x · 持有 0",
            "Credits：1200 C · 持有 3000",
            "24（持有 0）",
            "获得 Scrap Leather",
            "献祭 2 × Trophy\n奖励：One-handed",
            "发送 MoogleMail\n邮费：10 C\n附件：2\nCoD：10000 C",
            "领取全部附件将支付 10000 C",
            "附件：Sword\n这是受保护装备，仍要附加到邮件？",
            "创建 Celestial\n伤害类型：Slashing",
            "删除 Celestial\n该装备将永久销毁，无法恢复。",
            "Physical Damage · 升级 10 次\n下一次升级：10 Chaos Tokens",
            "Judgement Strike · 名称",
            "Judgement Strike · 攻击类型",
            "取消 Ability Boost？",
            "数量：3\n合计：75 C",
            "装备将永久灵魂绑定，无法交易。",
            "放弃本期头奖装备？此操作不可撤销。",
            "当前页面没有可用的提交按钮",
            "服务器未保存人格名称。",
            "Cookie 需要包含有效的 ipb_member_id 和 ipb_pass_hash。",
        )
        messages.forEach { source ->
            assertFalse("Untranslated generated message: $source", english.translate(source).any { it in '\u3400'..'\u9fff' })
        }
        val chinese = AppMessageTranslator(chinese = true) {
            it.replace("Credits", "信用点").replace("Silk Pouch", "丝绸护符袋")
                .replace("Character", "角色").replace("Subject", "主题").replace("Fire", "火焰")
        }
        assertEquals("信用点 不足\n护符袋：丝绸护符袋（损毁）", chinese.translate("Credits 不足\n护符袋：Silk Pouch（损毁）"))
        assertEquals(
            "将此受保护装备加入本次选择？\nSilk Pouch\n装备名称：Credits\n收件人：Credits\n主题：Silk Pouch\n重命名 Credits\n删除 Silk Pouch\nSilk Pouch · 名称：Credits\n信用点 不足",
            chinese.translate("将此受保护装备加入本次选择？\nSilk Pouch\n装备名称：Credits\n收件人：Credits\n主题：Silk Pouch\n重命名 Credits\n删除 Silk Pouch\nSilk Pouch · 名称：Credits\nCredits 不足"),
        )
        assertEquals("角色\n主题: Fire\nName: Silk Pouch", chinese.translate("Character\nSubject: Fire\nName: Silk Pouch"))
    }
}
