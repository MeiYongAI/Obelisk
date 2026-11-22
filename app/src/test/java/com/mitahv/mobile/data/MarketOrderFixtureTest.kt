package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class MarketOrderFixtureTest {
    private fun page(name: String, query: String): BazaarSectionsPage = BazaarSectionsParser.parse(
        javaClass.getResource("/market-orders/$name.html")!!.readText(), HvRoute.Market, HvWorld.Persistent,
        (HvRoute.Market.url(HvWorld.Persistent).toString() + query).toHttpUrl())

    @Test fun activeBuyOrderCanBeUpdatedAndCancelledIndependently() {
        val page = page("buy-detail", "&screen=browseitems&filter=ar&itemid=20001")
        val form = page.forms.single { it.key == "market_buy" }
        val values = form.fields.associate { it.key to it.value }
        assertEquals("1", values["buyorder_batchcount"])
        assertEquals("3820", values["buyorder_batchprice"])
        val update = form.actions.single { it.fields.any { f -> f.first == "buyorder_update" } }
        val cancel = form.actions.single { it.fields.any { f -> f.first == "buyorder_cancel" } }
        assertEquals("更新挂单", update.label)
        assertEquals("撤销买单", cancel.label)
        val invalid = values + mapOf("buyorder_batchcount" to "", "buyorder_batchprice" to "1")
        assertNotNull(form.validationError(update, invalid))
        assertNull(form.validationError(cancel, invalid))
        val sent = form.submit(cancel, invalid)
        assertEquals("Delete", sent.fields.toMap()["buyorder_cancel"])
        assertNull(sent.fields.toMap()["buyorder_update"])
        assertEquals("test-market-token", sent.fields.toMap()["marketoken"])
        assertEquals("20001", sent.url.queryParameter("itemid"))
        assertNull(form.validationError(update, values + ("buyorder_batchprice" to "3840")))
    }

    @Test fun activeSellOrderKeepsOriginalCountAndNeverAddsCancelToEmptyOrder() {
        val page = page("sell-detail", "&screen=browseitems&filter=tr&itemid=30023")
        val sell = page.forms.single { it.key == "market_sell" }
        val values = sell.fields.associate { it.key to it.value }
        assertEquals("42", values["sellorder_batchcount"])
        assertEquals("1290", values["sellorder_batchprice"])
        val cancel = sell.actions.single { it.label == "撤销卖单" }
        assertNull(sell.validationError(cancel, values + ("sellorder_batchcount" to "99999999")))
        val update = sell.actions.single { it.label == "更新挂单" }
        assertNotNull(sell.validationError(update, values + ("sellorder_batchcount" to "43")))
        assertNull(sell.validationError(update, values))
        val buy = page.forms.single { it.key == "market_buy" }
        assertTrue(buy.actions.none { it.fields.any { f -> f.first.endsWith("_cancel") } })
    }

    @Test fun actualOrderListsNavigateToTheirOriginalItemAndScreen() {
        for ((name, screen, filter, item) in listOf(
            listOf("buy-list", "buyorders", "ar", "20001"),
            listOf("sell-list", "sellorders", "tr", "30023")
        )) {
            val page = page(name, "&screen=$screen&filter=$filter")
            val action = page.sections.flatMap { it.entries }.mapNotNull { it.action }.single { it.url.queryParameter("itemid") == item }
            assertEquals("GET", action.method)
            assertEquals(screen, action.url.queryParameter("screen"))
            assertEquals(filter, action.url.queryParameter("filter"))
            assertTrue(page.forms.none { it.key.startsWith("market_") })
        }
    }
}
