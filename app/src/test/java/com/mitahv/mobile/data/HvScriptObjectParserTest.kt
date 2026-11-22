package com.mitahv.mobile.data

import org.junit.Assert.*
import org.junit.Test

class HvScriptObjectParserTest {
    @Test fun acceptsStoreAssignmentWithoutVariableDeclaration() {
        val parsed = HvScriptObjectParser.parse("""dynjs_eqstore={"123":{"t":"Name","p":{"potency":2}}};""", "dynjs_eqstore")
        assertEquals("Name", parsed!!.getJSONObject("123").getString("t"))
        assertEquals(2, parsed.getJSONObject("123").getJSONObject("p").getInt("potency"))
    }

    @Test fun skipsBracesAndEscapedQuotesInsideStrings() {
        val parsed = HvScriptObjectParser.parse("""var dynjs_itemc={"1":{"q":"Text { \"quoted\" }"}}; var unrelated={};""", "dynjs_itemc")
        assertEquals("Text { \"quoted\" }", parsed!!.getJSONObject("1").getString("q"))
    }
}
