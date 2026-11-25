package com.mitahv.mobile.data

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

private val playerLevelPattern = Regex("""\bLv\.\s*([\d,]+)\b""", RegexOption.IGNORE_CASE)
internal fun Document.hvPlayerLevel(): Int? = playerLevelPattern.find(selectFirst("#level_readout").hvText())
    ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()

/** Reads both custom-font text and the bitmap fonts defined by HV's hvg.css. */
internal fun Element?.hvText(): String {
    if (this == null) return ""
    if (childrenSize() == 0) return classGlyph(this) ?: inlineGlyph(this) ?: text()
    // Ordinary HTML text can be read directly. Only sprite text needs a mutable copy for decoding.
    if (stream().noneMatch { node -> node.childrenSize() == 0 &&
            (classGlyph(node) != null || inlineGlyph(node) != null) }) return text()
    val copy = clone()
    decodeBitmapText(copy)
    return copy.text()
}

private fun decodeBitmapText(element: Element) {
    val children = element.children()
    val glyphs = children.map { classGlyph(it) ?: inlineGlyph(it) }
    if (children.isNotEmpty() && element.ownText().isBlank() && glyphs.all { it != null }) {
        // Whitespace between sprite elements is layout only; c39/c59 encode real spaces.
        // common.get_dynamic_digit_string() emits digits backwards and floats them right.
        val visualOrder = if (children.all {
                FLOAT_RIGHT.containsMatchIn(it.attr("style")) && inlineGlyph(it) != null
            }) glyphs.asReversed() else glyphs
        element.text(visualOrder.joinToString("") { it.orEmpty() })
        return
    }
    children.toList().forEach { child ->
        val glyph = classGlyph(child) ?: inlineGlyph(child)
        if (glyph != null) child.replaceWith(TextNode(glyph)) else decodeBitmapText(child)
    }
}

private fun classGlyph(element: Element): String? {
    if (!element.hasAttr("class") || element.childrenSize() != 0) return null
    val name = element.classNames().firstOrNull { GLYPH_CLASS.matches(it) } ?: return null
    if (element.ownText().isNotBlank()) return null
    return when (name[1]) {
        '2', '4' -> PUNCTUATION_GLYPHS.getOrNull(name[2].digitToInt(36))?.toString()
        '3', '5' -> if (name[2] == '9') " " else name[2].uppercaseChar().toString()
        else -> null
    }
}

private fun inlineGlyph(element: Element): String? {
    if (!element.hasAttr("style") || element.childrenSize() != 0) return null
    val style = element.attr("style")
    val height = FONT_IMAGE.find(style)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    if (element.ownText().isNotBlank()) return null
    val offset = FONT_POSITION.find(style)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    if (offset > 0 || -offset % height != 0) return null
    return PUNCTUATION_GLYPHS.getOrNull(-offset / height)?.toString()
}

// Sprite row indices come from hvg.css and /y/font/12b.png; punctuation indices
// 10, 11, 15, 16 and 22 are also explicit in hvc.js get_dynamic_digit_string().
private const val PUNCTUATION_GLYPHS = "0123456789.,!?%+-=/\\'\":;()[]_"
private val GLYPH_CLASS = Regex("c(?:[24][0-9a-s]|[35][9a-z])")
private val FLOAT_RIGHT = Regex("float\\s*:\\s*right", RegexOption.IGNORE_CASE)
private val FONT_IMAGE = Regex("url\\([^)]*/font/(10|12)[bgary]\\.png[^)]*\\)", RegexOption.IGNORE_CASE)
private val FONT_POSITION = Regex("(?:background-position\\s*:\\s*|url\\([^)]*\\)\\s*)0(?:px)?\\s+(-?\\d+)(?:px)?", RegexOption.IGNORE_CASE)
