package com.mitahv.mobile.data

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class HvHtmlTextTest {
    @Test
    fun readsPlainCustomFontAndBitmapFontText() {
        assertEquals("Strength", read("""<div class="fc4 fal fcb"><div>Strength</div></div>"""))
        assertEquals(
            "STAMINA: 67",
            read("""<div class="fl f4b"><div class="c5s"></div><div class="c5t"></div><div class="c5a"></div><div class="c5m"></div><div class="c5i"></div><div class="c5n"></div><div class="c5a"></div><div class="c4m"></div><div class="c59"></div><div class="c46"></div><div class="c47"></div></div>"""),
        )
        assertEquals(
            "517.244",
            read("""<div class="fr f2b"><div class="c25"></div><div class="c21"></div><div class="c27"></div><div class="c2a"></div><div class="c22"></div><div class="c24"></div><div class="c24"></div></div>"""),
        )
        assertEquals("+45", read("""<div class="fr f4r"><div class="c4f"></div><div class="c44"></div><div class="c45"></div></div>"""))
    }

    @Test
    fun ignoresSpriteLayoutWhitespaceAndKeepsEncodedSpaces() {
        val html = """<div class="fl f4b">
            <div class="c5s"> </div>
            <div class="c5t"></div>
            <div class="c59"></div>
            <div class="c43"></div>
            <div class="c45"></div>
            <div class="c45"></div>
        </div>"""
        assertEquals("ST 355", read(Jsoup.parseBodyFragment(html).outerHtml()))
        assertEquals("Strength bonus", read("<div>Strength bonus</div>"))
    }

    @Test
    fun reversesDynamicDigitsFloatedRightByCommonScript() {
        // hvc.js appends the least significant digit first, each floated right.
        assertEquals("1,234", read(
            """<div style="position:relative;display:inline;height:12px">
              <div style="float:right;height:12px;width:11px;background:transparent url(/isekai/y/font/12b.png) 0px -48px"></div>
              <div style="float:right;height:12px;width:11px;background:transparent url(/isekai/y/font/12b.png) 0px -36px"></div>
              <div style="float:right;height:12px;width:11px;background:transparent url(/isekai/y/font/12b.png) 0px -24px"></div>
              <div style="float:right;height:12px;width:5px;background:transparent url(/isekai/y/font/12b.png) 0px -132px"></div>
              <div style="float:right;height:12px;width:6px;background:transparent url(/isekai/y/font/12b.png) 0px -12px"></div>
            </div>""",
        ))
    }

    @Test
    fun keepsUnknownImagesAndStylesOutOfNumericText() {
        assertEquals("", read("""<div class="c4z"></div><img src="/y/character/inc.png">"""))
        assertEquals("", read("""<div style="background:transparent url(/other/12b.png) 0px -12px"></div>"""))
    }

    private fun read(html: String): String = Jsoup.parseBodyFragment(html).body().hvText()
}
