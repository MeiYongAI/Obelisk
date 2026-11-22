package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

/** Minimal, token-sanitized excerpts of the user HTML and read-only research snapshots. */
class BazaarSectionsParserTest {
    @Test fun renamedArmoryEquipmentKeepsOriginalMetadataInListsAndDetails() {
        val alias = "My Legendary Blade"
        val original = "Legendary Ethereal Rapier of Slaughter"
        val dictionary = """var dynjs_equip={"101":{"t":"$alias","n":"$original","d":""}};"""
        val list = BazaarSectionsParser.parse("""<div id=armory_outer><form id=equipform method=post>
            <div id=equiplist><table><tr><td><input name='eqids[]' type=checkbox value=101>$alias</td></tr></table></div>
            <input id=equipsubmit type=submit value=Modify></form></div>""",
            HvRoute.Armory, HvWorld.Persistent, supplementalScripts = listOf(dictionary))
        assertEquals(alias, list.forms.single().fields.single().label)
        assertEquals(original, list.forms.single().fields.single().originalEquipmentName)

        val detail = BazaarSectionsParser.parse("""<div id=armory_outer><form id=equipform method=post>
            <input type=hidden name='eqids[]' value=101><div id=equipmodify_outer>
            <div id=equipinfo><div class=showequip><div><div>$alias</div><div>($original)</div></div></div></div>
            </div></form></div>""", HvRoute.Armory, HvWorld.Persistent)
        assertEquals(alias, detail.sections.first().title)
        assertEquals(original, detail.sections.first().originalEquipmentName)
    }

    @Test fun renamedMailEquipmentKeepsOriginalMetadataWithoutChangingAttachmentIds() {
        val alias = "My Legendary Blade"
        val original = "Legendary Ethereal Rapier of Slaughter"
        val dictionary = """var dynjs_equip={"101":{"t":"$alias","n":"$original","d":""}};"""
        val page = BazaarSectionsParser.parse("""<div id=mmail_outer>
            <script>var send_cost=10;var attach_count=0;var attach_cod=0;</script>
            <form id=mailform method=post><input type=hidden name=mmtoken value=test-token>
            <img onclick='mooglemail.mmail_save()'></form>
            <div id=mmail_attachequip><div onclick='mooglemail.set_mooglemail_item(101)'>$alias</div></div>
            <div id=mmail_attachlist><div><span onmouseover='equips.set(101)'>$alias</span></div></div>
            </div>""", HvRoute.MoogleMail, HvWorld.Persistent,
            HvRoute.MoogleMail.url(HvWorld.Persistent).newBuilder().addQueryParameter("filter", "new").build(), listOf(dictionary))
        val candidate = page.forms.single().attachments.single { it.key == "attach-equip-101" }
        assertEquals(alias, candidate.title)
        assertEquals(original, candidate.originalEquipmentName)
        assertEquals("101", candidate.quantity!!.action.fields.toMap()["select_item"])
        val attached = page.sections.single { it.title == "附件" }.entries.single()
        assertEquals(alias, attached.title)
        assertEquals(original, attached.originalEquipmentName)
    }

    @Test fun marketOrderBookOnlyPrefillsDraftAndKeepsLargerEnteredQuantity() {
        val page = parse("""<div id=market_outer><div class=market_itemorders><div>Listed Sell Orders</div><table>
            <tr><th>Count</th><th>Price</th><th>Total</th></tr>
            <tr onclick="autofill_from_sell_order(476,476,8)"><td>7</td><td>476 C</td><td>3,332 C</td></tr>
            </table></div></div>""", HvRoute.Market)
        val row = page.sections.single().entries.single()
        assertNull(row.action)
        val preset = row.prefill!!
        val values = preset.applyTo(mapOf("market_buy/buyorder_batchcount" to "12"))
        assertEquals("12", values["market_buy/buyorder_batchcount"])
        assertEquals("476", values["market_buy/buyorder_batchprice"])
        assertEquals("476", values["market_sell/sellorder_batchprice"])
        assertEquals("8", preset.applyTo(emptyMap())["market_buy/buyorder_batchcount"])
    }

    @Test fun marketPresetsAndSummaryUseBatchUnitsWithoutSubmitting() {
        val page = parse("""<div id=market_outer><script>var market_balance=9000;var player_stock=250;var batch_size=100;</script>
            <div id=market_itemsell><form method=post><input type=number name=sellorder_batchcount value=0><input name=sellorder_batchprice value=0>
            <table><tr><td id=sell_order_stock_field onclick="document.getElementById('sellorder_batchcount').value=2">Stock: 250</td></tr></table>
            <div onclick="autofill_from_sell_order(472,0,0)">Min Undercut: 472 C</div><input type=submit name=sellorder_update value='Place Sell Order'></form></div></div>""", HvRoute.Market)
        val form = page.forms.single()
        assertEquals("2", form.presets.single { it.label == "全部库存" }.values["market_sell/sellorder_batchcount"])
        assertEquals("472", form.presets.first().values["market_sell/sellorder_batchprice"])
        val entered = mapOf("sellorder_batchcount" to "2", "sellorder_batchprice" to "472")
        assertEquals(listOf("数量" to "200", "订单总额" to "944 C"), form.orderSummary(entered))
        assertTrue(form.submit(form.actions.single(), entered).confirmation!!.contains("944 C"))
    }

    @Test fun armoryExposesStoredEquipmentStatsWithoutOpeningWebPage() {
        val page = parse("""<div id=armory_outer><script>
            dynjs_eqstore={"101":{"t":"Test Cotton Robe","d":"<div class=\"eq\"><div class=\"eqt\">Cloth Armor</div><div class=\"ex\"><div>Evade +12</div></div></div>"}};
            const title="purchase";const eqitems={"101":{"c":100}};
            </script><form id=equipform method=post><div id=equiplist><table><tr><td><input type=checkbox name='eqids[]' value=101>old label</td><td>100 C</td></tr></table></div>
            <input id=equipsubmit type=submit value=Purchase></form></div>""", HvRoute.Armory)
        val field = page.forms.single().fields.single()
        assertEquals("Test Cotton Robe", field.label)
        assertEquals(listOf("" to "Cloth Armor", "" to "Evade +12"), field.details)
    }

    @Test fun armoryProtectedSelectionRequiresActualUnprotectHandler() {
        // The control is disabled until hveqc.confirm_unprotect/unprotect_equip enables it.
        val page = parse("""<div id=armory_outer><form id=equipform method=post><div id=equiplist><table>
            <tr onclick="confirm_unprotect(this,101,'Protected Equipment','')"><td><input type=checkbox disabled name='eqids[]' value=101>Protected Equipment</td></tr>
            <tr><td><input type=checkbox disabled name='eqids[]' value=102>Unavailable Equipment</td></tr></table></div><input type=submit id=equipsubmit value=Sell></form></div>""", HvRoute.Armory)
        val field = page.forms.single().fields.single()
        assertEquals("101", field.value)
        assertNotNull(field.selectionConfirmation)
    }

    @Test fun supplementalEquipmentDictionaryIsUsedButCurrentPageStoreWins() {
        val html = """<div id=armory_outer><script>dynjs_eqstore={"101":{"t":"Store Name","d":"<div class=\"eq\"><div class=\"eqt\">Level 10</div></div>"}};</script>
            <form id=equipform method=post><div id=equiplist><table><tr><td><input name='eqids[]' type=checkbox value=101>101</td></tr>
            <tr><td><input name='eqids[]' type=checkbox value=102>102</td></tr></table></div><input id=equipsubmit type=submit value=Modify></form></div>"""
        val supplement = """var dynjs_equip={"101":{"t":"Old Name","d":""},"102":{"t":"Owned Name","d":"<div class=\"eq\"><div class=\"eqt\">Soulbound</div></div>"}};"""
        val page = BazaarSectionsParser.parse(html, HvRoute.Armory, HvWorld.Persistent, supplementalScripts = listOf(supplement))
        assertEquals(listOf("Store Name", "Owned Name"), page.forms.single().fields.map { it.label })
        assertEquals(listOf("" to "Soulbound"), page.forms.single().fields[1].details)
    }

    @Test fun shopDescriptionIsOptionalAndSeparateFromPrice() {
        val html = """<div id=itshop_outer><div id=shop_pane><div onclick="itemshop.set_item('shop_pane',11191,50,25)">Health Draught</div></div>
            <form id=shopform method=post></form></div>"""
        val page = BazaarSectionsParser.parse(html, HvRoute.Bazaar, HvWorld.Persistent,
            supplementalScripts = listOf("""var dynjs_itemc={"11191":{"n":"Health Draught","q":"Provides a long-lasting health restoration effect."}};"""))
        val item = page.sections.flatMap { it.entries }.single()
        assertEquals("Provides a long-lasting health restoration effect.", item.moreDetails.single().second)
        assertTrue(item.details.contains("单价" to "25 C"))
    }

    @Test fun shopKeepsRareTooltipDescriptionAndUnownedShopStock() {
        val html = """<div id=itshop_outer><div id=item_pane></div><div id=shop_pane>
            <div onmouseover="common.show_itemr_box(10,-40,'shop_pane',this,'left','Gummy Figurine','Pinkie Pie&amp;#039;s faithful pet.','Collectable')"
                 onclick="itemshop.set_item('shop_pane',70102,38,50000,'Gummy Figurine')">Gummy Figurine</div>
            </div><form id=shopform method=post></form></div>"""
        val page = BazaarSectionsParser.parse(html, HvRoute.Bazaar, HvWorld.Persistent)
        val item = page.sections.flatMap { it.entries }.single()
        assertEquals(BazaarShopItem("70102", 38, 0), item.shopItem)
        assertEquals("Pinkie Pie's faithful pet.", item.moreDetails.single().second)
        assertNull(page.shopBalance)
    }

    @Test fun mailActionsRequireRenderedControlsAndZeroCodKeepsDraft() {
        // Handler names and parameters are from the saved official hvc.js MoogleMail object.
        val page = parse("""<div id=mmail_outer><script>var attach_cod=50;var mail_state=0;</script>
            <form id=mailform method=post><input type=hidden name=mmtoken value=test-mail-token><input type=hidden name=action value=0>
            <input name=message_to_name value=Recipient><textarea name=message_body>Draft text</textarea><img onclick='mooglemail.mmail_send()'></form>
            <div id=mmail_attachlist><div>1x Test Item<img onclick='mooglemail.remove_attachment(3)'></div></div>
            <input id=newcod value=50><img onclick='mooglemail.set_cod()'></div>""", HvRoute.MoogleMail, "&filter=new")
        val form = page.forms.single()
        val remove = form.attachments.single { it.key.startsWith("mail-action-") }.action!!
        assertEquals("3", remove.fields.toMap()["action_value"])
        assertEquals("attach_remove", remove.fields.toMap()["action"])
        val cod = form.attachments.single { it.key == "mail-cod" }.quantity!!
        val sent = form.submit(cod.submit(0), mapOf("message_to_name" to "Recipient", "message_body" to "Changed draft"))
        assertEquals("0", sent.fields.toMap()["action_value"])
        assertEquals("Changed draft", sent.fields.toMap()["message_body"])
        assertEquals("attach_cod", sent.fields.toMap()["action"])
        assertEquals(0L, cod.minimum)
    }

    @Test fun readMailKeepsBodyAndOnlyActualTakeReturnReplyControls() {
        val page = parse("""<div id=mmail_outer><script>var attach_cod=125;var mail_state=1;</script>
            <form id=mailform method=post><input type=hidden name=mmtoken value=test-mail-token><input name=message_from_name readonly value=Sender>
            <textarea name=message_body readonly>First line
Second line</textarea></form><div id=mmail_showbuttons><img src=/y/mmail/returnmail.png onclick='mooglemail.return_mail()'>
            <img src=/y/mmail/reply.png onclick="document.location='?s=Bazaar&amp;ss=mm&amp;filter=new&amp;reply=7'"></div>
            <div id=mmail_attachremove><img onclick='mooglemail.remove_attachment(0)'></div></div>""", HvRoute.MoogleMail, "&mid=7")
        assertTrue(page.forms.isEmpty())
        assertTrue(page.sections.single().facts.any { it.second == "First line\nSecond line" })
        assertEquals(2, page.actions.size)
        assertTrue(page.actions.first { it.fields.toMap()["action"] == "attach_remove" }.confirmation!!.contains("125 C"))
        assertEquals("7", page.links.single().action.url.queryParameter("reply"))
        val empty = parse("<div id=mmail_outer><script>var attach_cod=125;</script><form id=mailform method=post></form></div>", HvRoute.MoogleMail)
        assertTrue(empty.actions.isEmpty())
    }

    @Test fun alternatePersistentMailLinksRemainAvailableWithoutEnteringIsekai() {
        val current = HvRoute.MoogleMail.url(HvWorld.Persistent, HvSite.Alt)
        val html = """<div id=mmail_outer><div id=filterbar>
            <a href='https://alt.hentaiverse.org/?s=Bazaar&amp;ss=mm&amp;filter=new'><div class=cfb>Write New</div></a>
            <a href='https://alt.hentaiverse.org/isekai/?s=Bazaar&amp;ss=mm&amp;filter=new'><div class=cfb>Isekai</div></a>
            </div><table id=mmail_list><tr><td colspan=4>No New Mail</td></tr></table></div>"""
        val page = BazaarSectionsParser.parse(html, HvRoute.MoogleMail, HvWorld.Persistent, current)
        assertEquals(1, page.links.size)
        assertEquals("Write New", page.links.single().label)
        assertEquals("alt.hentaiverse.org", page.links.single().action.url.host)
        assertEquals("/", page.links.single().action.url.encodedPath)
    }

    private fun parse(html: String, route: HvRoute, query: String = "") = BazaarSectionsParser.parse(html, route, HvWorld.Persistent,
        if (query.isEmpty()) route.url(HvWorld.Persistent) else (route.url(HvWorld.Persistent).toString() + query).toHttpUrl())

    @Test fun shopUsesRealPaneIdAndCapsPurchaseByCredits() {
        val page = parse("""<script>var current_credits = 100;</script><div id=itshop_outer>
            <div id=item_pane><table class=itemlist><tr><td><div onclick="itemshop.set_item('item_pane',11191,9,1);common.text_select(this)">Health Draught</div></td><td>9</td></tr></table></div>
            <div id=shop_pane><table class=itemlist><tr><td><div onclick="itemshop.set_item('shop_pane',11191,99999,25)">Health Draught</div></td><td>99999</td></tr></table></div>
            <form id=shopform method=post><input name=storetoken type=hidden value=test-store-token><input name=select_mode type=hidden><input name=select_item type=hidden><input name=select_count type=hidden></form></div>""", HvRoute.Bazaar)
        assertEquals(100L, page.shopBalance)
        val owned = page.sections.flatMap { it.entries }.first { it.key.startsWith("item_pane") }
        assertEquals(BazaarShopItem("11191", 9, 9), owned.shopItem)
        val shop = page.sections.flatMap { it.entries }.first { it.key.startsWith("shop_pane") }
        assertEquals(BazaarShopItem("11191", 99999, 9), shop.shopItem)
        val purchase = shop.quantity!!
        assertEquals(4L, purchase.maximum)
        val action = purchase.submit(3)
        assertEquals("shop_pane", action.fields.toMap()["select_mode"])
        assertEquals("11191", action.fields.toMap()["select_item"])
        assertEquals("3", action.fields.toMap()["select_count"])
        assertTrue(action.confirmation!!.contains("75 C"))
        assertEquals("test-store-token", action.fields.toMap()["storetoken"])
    }

    @Test fun shrineDoesNotOfferInsufficientTrophyAndKeepsRewardSlots() {
        val page = parse("""<div id=shrine_outer><div id=inv_item>
            <div onclick="snowflake.set_shrine_item(30016,9,32,'ManBearPig Tail')">ManBearPig Tail</div>
            <div onclick="snowflake.set_shrine_item(30032,1,1,'Noodly Appendage')">Noodly Appendage</div>
            <div onclick="snowflake.set_shrine_item(20001,2,1,'Precursor Artifact')">Precursor Artifact</div></div>
            <form id=shopform method=post><input name=select_item type=hidden><input name=select_reward_type type=hidden><input name=select_reward_slot type=hidden></form>
            <input id=accept_reward value='Random Reward' onclick='snowflake.commit_transaction()'>
            <div id=accept_equip><input value=Helmet onclick="snowflake.submit_shrine_reward('armor_cloth','13')"><input value=Body onclick="snowflake.submit_shrine_reward('armor_cloth','11')"></div></div>""", HvRoute.Shrine)
        val rows = page.sections.single().entries
        assertFalse(rows[0].enabled)
        assertTrue(rows[1].enabled)
        assertEquals("13", rows[1].choices.first().fields.toMap()["select_reward_slot"])
        assertEquals("armor_cloth", rows[1].choices.first().fields.toMap()["select_reward_type"])
        assertEquals("", rows[2].action!!.fields.toMap()["select_reward_type"])
    }

    @Test fun marketGetFilterBuildsQueryWithoutBodyFields() {
        val page = parse("""<div id=market_outer><form id=form_market_itemfilter method=get action='?s=Bazaar&amp;ss=mk'>
            <input type=hidden name=s value=Bazaar><input type=hidden name=ss value=mk><input type=hidden name=screen value=browseitems><input type=hidden name=filter value=co><input type=hidden name=itemid value=0>
            <label><input type=checkbox name=playerstock onchange="document.getElementById('form_market_itemfilter').submit()">Only With Sellable Stock</label>
            <label><input type=checkbox name=marketstock>Only With Buyable Stock</label></form></div>""", HvRoute.Market)
        val form = page.forms.single()
        val action = form.submit(form.actions.single(), mapOf("playerstock:on" to "on", "marketstock:on" to ""))
        assertEquals("GET", action.method)
        assertTrue(action.fields.isEmpty())
        assertEquals("on", action.url.queryParameter("playerstock"))
        assertNull(action.url.queryParameter("marketstock"))
        assertEquals("Bazaar", action.url.queryParameter("s"))
    }

    @Test fun marketLinksKeepNavigationFiltersItemsAndPagesSeparate() {
        val page = parse("""<div id=market_outer>
            <div id=market_left><a href='?s=Bazaar&amp;ss=mk&amp;screen=buyorders'>My Buy Orders</a></div>
            <div id=market_itemheader>
                <a href='?s=Bazaar&amp;ss=mk&amp;screen=browseitems&amp;filter=ar&amp;itemid=20000'>◀</a>
                <a href='?s=Bazaar&amp;ss=mk&amp;screen=browseitems&amp;filter=ar&amp;itemid=20001'>Test Item</a>
                <a href='?s=Bazaar&amp;ss=mk&amp;screen=browseitems&amp;filter=ar&amp;itemid=20002'>▶</a>
            </div>
            <div id=market_itembody><div><div><a href='?s=Bazaar&amp;ss=mk&amp;screen=browseitems&amp;filter=ar&amp;itemid=20001&amp;tradelog=item-20001'>Show Full Trade Log</a></div></div></div>
            <div id=market_right>
                <div id=filterbar>
                    <a href='?s=Bazaar&amp;ss=mk&amp;screen=browseitems&amp;filter=ar'><div class=cfb>Artifacts</div></a>
                    <a href='?s=Bazaar&amp;ss=mk&amp;screen=browseitems&amp;filter=co'><div class=cfb>Consumables</div></a>
                </div>
                <div class=pager><a href='?s=Bazaar&amp;ss=mk&amp;screen=marketlog&amp;page=2'>2</a></div>
            </div>
            </div>""", HvRoute.Market, "&screen=browseitems&filter=ar&itemid=20001")

        assertEquals("My Buy Orders", page.marketLinks!!.views.single().label)
        assertEquals(listOf("Artifacts", "Consumables"), page.marketLinks.filters.map { it.label })
        assertEquals(listOf("Previous item", "Next item"), page.marketLinks.items.map { it.label })
        assertEquals("Test Item", page.marketLinks.currentItem)
        assertEquals("返回列表", page.marketLinks.backToList!!.label)
        assertEquals("ar", page.marketLinks.backToList.action.url.queryParameter("filter"))
        assertNull(page.marketLinks.backToList.action.url.queryParameter("itemid"))
        assertEquals(listOf("Show Full Trade Log", "2"), page.marketLinks.pages.map { it.label })
        assertEquals("item-20001", page.marketLinks.pages.first().action.url.queryParameter("tradelog"))
        assertEquals(7, page.links.size)
    }

    @Test fun marketItemReturnKeepsTheCurrentListAndFilters() {
        for (screen in listOf("browseitems", "buyorders", "sellorders")) {
            val page = parse("<div id=market_outer></div>", HvRoute.Market,
                "&screen=$screen&filter=ar&itemid=20001&playerstock=on")
            val back = page.marketLinks!!.backToList!!.action
            assertEquals("GET", back.method)
            assertEquals(screen, back.url.queryParameter("screen"))
            assertEquals("ar", back.url.queryParameter("filter"))
            assertEquals("on", back.url.queryParameter("playerstock"))
            assertNull(back.url.queryParameter("itemid"))
        }
    }

    @Test fun marketOrdersUseResponseUrlBatchLimitsAndPriceSteps() {
        val page = parse("""<div id=market_outer><script>var market_balance=1000;var player_stock=300;var batch_size=100;</script>
            <div id=market_iteminfo>Traded in batches of 100</div><div id=market_itembuy><form method=post action=''>
            <input type=hidden name=marketoken value=test-market-token><input type=number name=buyorder_batchcount min=0 max=99999999 value=0>
            <input name=buyorder_batchprice value=0><input type=submit disabled name=buyorder_update value='Place Buy Order'></form></div></div>""", HvRoute.Market, "&screen=browseitems&filter=co&itemid=11191")
        val form = page.forms.single()
        val action = form.actions.single()
        assertEquals("11191", action.url.queryParameter("itemid"))
        assertNull(form.validationError(action, mapOf("buyorder_batchcount" to "3", "buyorder_batchprice" to "200")))
        assertNotNull(form.validationError(action, mapOf("buyorder_batchcount" to "6", "buyorder_batchprice" to "200")))
        assertNotNull(form.validationError(action, mapOf("buyorder_batchcount" to "1", "buyorder_batchprice" to "201")))
        assertNotNull(form.validationError(action, mapOf("buyorder_batchcount" to "1", "buyorder_batchprice" to "100000001")))
    }

    @Test fun marketLogsRemainStructuredRows() {
        val page = parse("""<div id=market_outer><div id=market_right><div id=market_accountlog><table>
            <tr><th></th><th>Amount</th><th>Balance</th><th>Info</th></tr>
            <tr><td>2026-11-20 12:00</td><td>-100 C</td><td>500 C</td><td>Purchased 1x Test Item @ 100 C</td></tr>
            </table></div></div></div>""", HvRoute.Market, "&screen=marketlog")
        val log = page.sections.single().entries.single()
        assertEquals("2026-11-20 12:00", log.title)
        assertTrue(log.details.contains("Amount" to "-100 C"))
    }

    @Test fun marketHistoryTablesMatchTheirOwnIds() {
        val page = parse("""<div id=market_outer><table id=market_price><tr><th></th><th>Sold</th><th>Low</th><th>Avg</th><th>High</th><th>Vol</th></tr>
            <tr><td>Day</td><td>100</td><td>10 C</td><td>11 C</td><td>12 C</td><td>1,100 C</td></tr></table>
            <table id=market_recent><tr><th></th><th>Seller</th><th>Buyer</th><th>Sold</th><th>Price</th></tr>
            <tr><td>2026-11-20 12:00</td><td>Seller Fixture</td><td>Buyer Fixture</td><td>100</td><td>11 C</td></tr></table></div>""", HvRoute.Market)
        assertEquals(listOf("Price History", "Recent Trades"), page.sections.map { it.title })
        assertEquals(1, page.sections[0].entries.size)
        assertEquals(1, page.sections[1].entries.size)
    }

    @Test fun armoryKeepsRepeatedEquipmentControlsAndCurrentScreen() {
        val page = parse("""<div id=armory_outer><form id=equipform method=post><input name=postoken type=hidden value=test-equip-token>
            <div id=equiplist><table><tr><td><label><input name='eqids[]' type=checkbox value=101>Equipment A</label></td><td>100 C</td></tr>
            <tr><td><label><input name='eqids[]' type=checkbox value=102>Equipment B</label></td><td>200 C</td></tr></table></div>
            <input id=equipsubmit type=submit value='Purchase Equipment'></form></div>""", HvRoute.Armory, "&screen=purchase&filter=armor_cloth")
        val form = page.forms.single()
        val selected = mapOf("eqids[]:101" to "101", "eqids[]:102" to "102")
        assertNull(form.validationError(form.actions.single(), selected))
        val action = form.submit(form.actions.single(), selected)
        assertEquals(listOf("101", "102"), action.fields.filter { it.first == "eqids[]" }.map { it.second })
        assertEquals("purchase", action.url.queryParameter("screen"))
        assertNotNull(action.confirmation)
    }

    @Test fun emptyArmoryCannotSubmitAnEmptySelection() {
        val page = parse("""<div id=armory_outer><form id=equipform method=post><div id=equiplist><table><tr class=eqselall><th><p>There are no available equipment of this type.</p></th></tr></table></div><input type=submit id=equipsubmit disabled value='Sell Equipment'></form></div>""", HvRoute.Armory)
        assertTrue(page.forms.isEmpty())
        assertTrue(page.sections.first().emptyText.contains("no available equipment"))
    }

    @Test fun mailAttachmentPreservesCurrentDraftAndSendUsesRealCost() {
        val page = parse("""<div id=mmail_outer><script>var send_cost=10;var attach_count=0;var attach_cod=0;</script>
            <form id=mailform method=post action=''><input type=hidden name=mmtoken value=test-mail-token><input type=hidden name=action value=0>
            <input name=message_to_name maxlength=50><input name=message_subject maxlength=50><textarea name=message_body></textarea>
            <img onclick='mooglemail.mmail_send()'><img onclick='mooglemail.mmail_save()'><img onclick='mooglemail.mmail_discard()'></form>
            <div id=mmail_attachcredits><div>Current Funds: 200 Credits</div><div><input id=count_credits value=1><img onclick='mooglemail.apply_attachment()'></div></div></div>""", HvRoute.MoogleMail, "&filter=new")
        val form = page.forms.single()
        val attachment = form.attachments.single().quantity!!.submit(50)
        val action = form.submit(attachment, mapOf("message_to_name" to "Test Recipient", "message_subject" to "Test subject", "message_body" to "Draft body\nSecond line"))
        assertEquals("Draft body\nSecond line", action.fields.toMap()["message_body"])
        assertEquals("attach_add", action.fields.toMap()["action"])
        assertEquals("credits", action.fields.toMap()["select_pane"])
        assertEquals("50", action.fields.toMap()["select_count"])
        assertEquals("new", action.url.queryParameter("filter"))
        assertTrue(form.actions.first().confirmation!!.contains("10 C"))
    }

    @Test fun armoryRepairAggregatesMaterialsAndHonorsReplaceCharms() {
        val page = parse("""<div id=armory_outer><script>
            const title="repair";const multiselect=true;const credit_balance=1000;
            const eqitems={"101":{"t":"A","m":{"60051":9,"61903":1}},"102":{"t":"B","m":{"60051":9}}};
            const itemdata={"60051":{"n":"Scrap Cloth","c":12},"61903":{"n":"Mithril Charm Pouch","c":0}};
            </script><form id=equipform method=post><input name=postoken type=hidden value=test-token><div id=equiplist><table>
            <tr><td><label><input name='eqids[]' type=checkbox value=101>A</label></td></tr>
            <tr><td><label><input name='eqids[]' type=checkbox value=102>B</label></td></tr></table></div>
            <input type=checkbox name=replace_charms checked><input id=equipsubmit type=submit value='Repair Equipment'></form></div>""", HvRoute.Armory, "&screen=repair")
        val form = page.forms.single()
        val action = form.actions.single()
        val noCharms = mapOf("eqids[]:101" to "101", "replace_charms:on" to "")
        assertNull(form.validationError(action, noCharms))
        assertTrue(form.submit(action, noCharms).confirmation!!.contains("Scrap Cloth：9"))
        assertTrue(form.validationError(action, noCharms + ("eqids[]:102" to "102"))!!.contains("Scrap Cloth 不足"))
        assertTrue(form.validationError(action, noCharms + ("replace_charms:on" to "on"))!!.contains("Mithril Charm Pouch 不足"))
    }

    @Test fun armoryModifyIsSingleSelectAndPurchaseConfirmsTotalPrice() {
        val html = """<div id=armory_outer><script>const title="purchase";const multiselect=false;const credit_balance=250;
            const eqitems={"101":{"t":"A","c":100},"102":{"t":"B","c":200}};</script>
            <form id=equipform method=post><div id=equiplist><table><tr><td><label><input name='eqids[]' type=checkbox value=101>A</label></td></tr>
            <tr><td><label><input name='eqids[]' type=checkbox value=102>B</label></td></tr></table></div><input id=equipsubmit type=submit value='Purchase Equipment'></form></div>"""
        val form = parse(html, HvRoute.Armory, "&screen=purchase").forms.single()
        assertTrue(form.singleEquipment)
        assertNotNull(form.validationError(form.actions.single(), mapOf("eqids[]:101" to "101", "eqids[]:102" to "102")))
        val action = form.submit(form.actions.single(), mapOf("eqids[]:102" to "102"))
        assertTrue(action.confirmation!!.contains("总价：200 C"))
        val multi = form.copy(singleEquipment = false)
        assertEquals("Credits 不足", multi.validationError(multi.actions.single(), mapOf("eqids[]:101" to "101", "eqids[]:102" to "102")))
    }

    @Test fun disabledMonsterControlsStayDisabledAndCostsAreVisible() {
        val page = parse("""<div id=monster_outer><div id=slot_pane></div><div id=monster_actions>
            <div><div><img src='/y/monster/feedallmonsters_d.png'></div><div>Requires: None</div></div>
            <div><div><img src='/y/monster/unlock_slot_d.png'></div><div>Cost: 1 Chaos Token<br>Stock: 0</div></div></div></div>""", HvRoute.MonsterLab)
        assertTrue(page.actions.isEmpty())
        assertTrue(page.sections.single().facts.any { it.second.contains("Stock: 0") })
    }

    @Test fun lotteryRespectsGoldenTicketDisabledStateAndBuyLimit() {
        val page = parse("""<form id=lotteryform method=post><input name=posttoken type=hidden value=test-lottery-token><input name=ticket_count type=hidden value=0><input name=golden_tickets type=hidden value=0><input name=prize_optout type=hidden value=0>
            <div id=rightpane><div>You currently have 5,000 GP.</div><div>Each ticket costs 1,000 GP.</div><div>You hold 10 of 60,000 sold tickets.</div><img onclick='submit_buy()'><img src='lottery_golden_d.png'><p>Today's drawing is in 4 hours.</p></div>
            <div id=leftpane><div id=lottery_eqname>Peerless Test Weapon</div><div id=lottery_eqstat></div><img onclick='submit_dnw()'></div></form>""", HvRoute.WeaponLottery)
        assertEquals(5L, page.sections.first().entries.single().quantity!!.maximum)
        assertEquals(1, page.actions.size)
        assertEquals("1", page.actions.single().fields.toMap()["prize_optout"])
        assertTrue(page.actions.single().confirmation!!.contains("不可撤销"))
    }

    @Test fun lotteryPurchaseResponseUpdatesBalanceAndKeepsPurchaseAndOptOutFieldsSeparate() {
        val page = parse(javaClass.getResource("/lottery/after-purchase.html")!!.readText(), HvRoute.ArmorLottery)
        val facts = page.sections.first().facts.toMap()
        assertEquals("3985289", facts["GP"])
        assertEquals("1000 GP", facts["每张"])
        assertEquals("10 / 13,002", facts["持有 / 已售"])
        val tickets = page.sections.flatMap { it.entries }.single { it.key == "tickets" }
        assertTrue(tickets.enabled)
        val quantity = tickets.quantity!!
        assertEquals(3985L, quantity.maximum)
        val optOut = page.actions.single()
        assertEquals("POST", optOut.method)
        assertEquals(HvRoute.ArmorLottery.url(HvWorld.Persistent), optOut.url)
        assertEquals(mapOf("posttoken" to "fixture-lottery-token", "ticket_count" to "0",
            "golden_tickets" to "0", "prize_optout" to "1"), optOut.fields.toMap())
        val purchase = quantity.submit(7)
        assertEquals("POST", purchase.method)
        assertEquals(optOut.url, purchase.url)
        assertEquals(mapOf("posttoken" to "fixture-lottery-token", "ticket_count" to "7",
            "golden_tickets" to "0", "prize_optout" to "0"), purchase.fields.toMap())
    }

    @Test fun zeroGpDisablesLotteryPurchaseAndNoTicketsKeepsTheOptOutReason() {
        val reason = "You cannot opt out unless you have at least one ticket."
        val page = parse("""<form id=lotteryform method=post>
            <input type=hidden name=posttoken value=fixture-lottery-token>
            <input type=hidden name=ticket_count value=0><input type=hidden name=golden_tickets value=0><input type=hidden name=prize_optout value=0>
            </form><div id=leftpane><div id=lottery_eqname>Peerless Test Prize</div>
            <img src='lottery_donotwant_d.png' title='$reason'></div>
            <div id=rightpane><div>You currently have 0 GP.</div><div>Each ticket costs 1,000 GP.</div>
            <input id=ticket_temp value=0><img src='buytickets.png' onclick='submit_buy()'>
            <div>You hold 0 of 8,450 sold tickets.</div><img src='lottery_golden_d.png'></div>""", HvRoute.ArmorLottery)
        val entries = page.sections.flatMap { it.entries }
        val tickets = entries.single { it.key == "tickets" }
        assertFalse(tickets.enabled)
        assertEquals(0L, tickets.quantity!!.maximum)
        assertTrue(page.actions.isEmpty())
        assertTrue(entries.single { it.key == "lottery-prize" }.details.any { it.second == reason })
    }

    @Test fun shrineRetainsCurrentRewardLimitAndTrophyTotalWithRulesInDetails() {
        val page = parse("""<div id=shrine_outer><form id=shopform method=post></form>
            <div id=shrine_artifact><p>Artifacts can be exchanged for a random reward.</p>
                <ul><li>Some Hath</li><li>A permanent +1 bonus to a primary stat</li></ul>
                <p>You cannot currently receive more than +49 to any primary stat. This increases by one for every tenth level.</p></div>
            <div id=shrine_trophy><p>Trophies can be exchanged for a piece of equipment.</p>
                <p>You have handed in 3,075,600 credits worth of trophies.</p></div>
            <div id=shrine_collectible><p>Collectibles can be exchanged for a random selection of bindings and materials.</p></div>
            </div>""", HvRoute.Shrine)
        assertTrue(page.sections.first().facts.contains("Primary Stat Bonus Limit" to "+49"))
        assertTrue(page.sections.first().facts.contains("Trophy Value Offered" to "3,075,600 C"))
        val rules = page.sections.flatMap { it.entries }.single { it.key == "shrine-rules" }
        assertTrue(rules.details.isEmpty())
        assertTrue(rules.moreDetails.any { it.second == "Some Hath" })
        assertTrue(rules.moreDetails.any { it.second.startsWith("Collectibles can be exchanged") })
    }

    @Test fun bothLotteriesKeepDrawingDateGoldenStockAndCompactPrizeDetails() {
        for (route in listOf(HvRoute.WeaponLottery, HvRoute.ArmorLottery)) {
            val page = parse("""<form id=lotteryform method=post><input name=posttoken type=hidden value=test-lottery-token>
                <input name=ticket_count type=hidden value=0><input name=golden_tickets type=hidden value=0>
                <div id=leftpane><div>Grand Prize for November 20th:</div><div id=lottery_eqname>Peerless Test Prize</div>
                    <div id=lottery_eqstat><div class=eq><div>Condition: 100%</div><div class=ex><div>Parry +345.3</div></div></div></div>
                    <div>2nd Prize: 2 Golden Lottery Tickets</div></div>
                <div id=rightpane><div>You currently have 5,000 GP.</div><div>Each ticket costs 1,000 GP.</div>
                    <div>You hold 10 of 60,000 sold tickets.</div><img onclick='submit_buy()'>
                    <div><img onclick='submit_glt()'></div><div>Stock: 2</div>
                    <div><p>Each lottery period lasts 24 hours.</p><p>Today's drawing is in 4 hours.</p></div>
                </div></form>""", route)
            assertTrue(page.sections.first().facts.contains("" to "Grand Prize for November 20th:"))
            assertTrue(page.sections.first().facts.contains("Golden Lottery Tickets" to "2"))
            assertEquals(5L, page.sections.first().entries.single { it.key == "tickets" }.quantity!!.maximum)
            val prize = page.sections.flatMap { it.entries }.single { it.key == "lottery-prize" }
            assertEquals("Peerless Test Prize", prize.title)
            assertTrue(prize.details.isEmpty())
            assertTrue(prize.moreDetails.contains("" to "Parry +345.3"))
            val rules = page.sections.flatMap { it.entries }.single { it.key == "lottery-rules" }
            assertEquals(listOf("" to "Each lottery period lasts 24 hours."), rules.moreDetails)
            assertTrue(page.actions.any { it.fields.toMap()["golden_tickets"] == "1" })
            val purchase = page.sections.first().entries.single { it.key == "tickets" }.quantity!!.submit(3)
            assertEquals("3", purchase.fields.toMap()["ticket_count"])
            assertTrue(purchase.confirmation!!.contains("3000 GP"))
        }
    }

    @Test fun pastLotteriesPairRewardsWithWinnersAndLinkBackToTheCurrentDrawing() {
        for ((route, fixture, period) in listOf(
            Triple(HvRoute.WeaponLottery, "past-weapon", 4817),
            Triple(HvRoute.ArmorLottery, "past-armor", 4620),
        )) {
            val current = route.url(HvWorld.Persistent).newBuilder().addQueryParameter("lottery", period.toString()).build()
            val page = BazaarSectionsParser.parse(javaClass.getResource("/lottery/$fixture.html")!!.readText(),
                route, HvWorld.Persistent, current)
            val weapon = route == HvRoute.WeaponLottery
            val entries = page.sections.flatMap { it.entries }
            val grand = entries.single { it.key == "lottery-prize" }
            assertEquals(if (weapon) "Peerless Mithril Kite Shield of the Barrier" else "Peerless Zircon Kevlar Boots of the Thrice-blessed", grand.title)
            assertEquals(listOf("Equip Winner" to if (weapon) "WillemJansen32" else "koohi74",
                "Core Winner" to if (weapon) "Chaos Dragon Apostle" else "yetsun"), grand.details)
            val results = entries.filter { it.key.startsWith("lottery-result-") }
            assertEquals(listOf("2nd Prize", "3rd Prize", "4th Prize", "5th Prize"), results.map { it.title })
            assertEquals(if (weapon) listOf("2 Golden Lottery Tickets", "9 Caffeinated Candies", "81 Chaos Tokens", "81 Chaos Tokens")
                else listOf("1 Golden Lottery Ticket", "2 Caffeinated Candies", "13 Chaos Tokens", "13 Chaos Tokens"),
                results.map { it.details.toMap().getValue("") })
            assertEquals(if (weapon) listOf("0nine", "QiTianYang", "treesloth", "쿠루링빵")
                else listOf("Mecrob6969", "atomicpuppy", "GPiiiiii", "fleeceflower"),
                results.map { it.details.toMap().getValue("Winner") })
            assertTrue(page.sections.first().facts.contains("" to "Today's ticket sale is closed."))
            assertTrue(page.sections.first().facts.contains("持有 / 已售" to if (weapon) "10 / 80,199" else "0 / 12,551"))
            assertFalse(entries.single { it.key == "lottery-rules" }.moreDetails.any { it.second.contains("sale is closed") })
            assertTrue(page.actions.isEmpty())
            assertTrue(page.forms.isEmpty())
            assertTrue(entries.none { it.action != null || it.quantity != null || it.choices.isNotEmpty() })
            assertEquals(listOf("上一期", "Current Drawing"), page.links.map { it.label })
            assertEquals(listOf(current.newBuilder().setQueryParameter("lottery", (period - 1).toString()).build(),
                route.url(HvWorld.Persistent)), page.links.map { it.action.url })
            assertTrue(page.links.all { it.action.method == "GET" && !it.selected })
        }
    }

    @Test fun feedbackIsReturnedAndCrossWorldLinksAreRejected() {
        val page = parse("""<div id=messagebox_inner><p>Transaction rejected: insufficient stock.</p></div><div id=armory_outer><div id=armory_left>
            <a href='https://alt.hentaiverse.org/isekai/?s=Bazaar&amp;ss=am&amp;screen=sell'>Sell</a></div></div>""", HvRoute.Armory)
        assertEquals(listOf("Transaction rejected: insufficient stock."), page.messages)
        assertTrue(page.links.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class) fun errorPageIsNotAnEmptySuccessfulShop() {
        parse("<h1>Service unavailable</h1>", HvRoute.Bazaar)
    }
}
