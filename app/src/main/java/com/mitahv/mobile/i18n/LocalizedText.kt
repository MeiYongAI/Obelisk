package com.mitahv.mobile.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Presentation only: request values and editable user content never pass through this layer. */
internal class GameTranslator(
    private val catalog: HvTextTranslator?,
    private val pageUrl: String? = null,
    private val battle: Boolean = false,
) {
    private val translateGame = catalog?.translatorFor(pageUrl, battle) ?: { text: String -> text }
    private val translateLog by lazy { catalog?.battleLogTranslatorFor(pageUrl) ?: { text: String -> text } }
    fun translate(text: String): String = translateGame(text)
    fun translateActionLabel(text: String): String = catalog?.translate(text) ?: text
    fun battleLog(text: String): String = translateLog(text)
    fun forPage(url: String, inBattle: Boolean) = GameTranslator(catalog, url, inBattle)
    fun messageTranslator() = AppMessageTranslator(catalog != null, ::translate)
}

internal val LocalGameTranslator = staticCompositionLocalOf<GameTranslator> { error("Missing game translator") }
internal val LocalMessageTranslator = staticCompositionLocalOf<AppMessageTranslator> { error("Missing message translator") }

@Composable
internal fun ProvideLocalizedText(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val game = remember(chinese) {
        val catalog = if (chinese) context.assets.open("hv_zh.json").bufferedReader().use { HvTextTranslator(it.readText()) } else null
        GameTranslator(catalog)
    }
    val messages = remember(game) { game.messageTranslator() }
    CompositionLocalProvider(LocalGameTranslator provides game, LocalMessageTranslator provides messages, content = content)
}

/** Keeps source page rules scoped without changing the raw models or editable fields. */
@Composable
internal fun ProvideGameTextScope(pageUrl: String, battle: Boolean = false, content: @Composable () -> Unit) {
    val base = LocalGameTranslator.current
    val game = remember(base, pageUrl, battle) { base.forPage(pageUrl, battle) }
    val messages = remember(game) { game.messageTranslator() }
    CompositionLocalProvider(LocalGameTranslator provides game, LocalMessageTranslator provides messages, content = content)
}

@Composable
internal fun gameText(text: String): String {
    val translator = LocalGameTranslator.current
    return remember(translator, text) { translator.translate(text) }
}

/** Battle action names are fixed game labels, so use dictionary entries from every page. */
@Composable
internal fun gameActionText(text: String): String {
    val translator = LocalGameTranslator.current
    return remember(translator, text) { translator.translateActionLabel(text) }
}

@Composable
internal fun battleLogText(text: String): String {
    val translator = LocalGameTranslator.current
    return remember(translator, text) { translator.battleLog(text) }
}

@Composable
internal fun messageText(text: String): String {
    val translator = LocalMessageTranslator.current
    return remember(translator, text) { translator.translate(text) }
}
