package com.mitahv.mobile.model

import okhttp3.HttpUrl

/** A human-operated RiddleMaster form. This model never infers answers or submits itself. */
data class RiddlePage(
    val image: RiddleImage,
    val choices: List<RiddleChoice>,
    val submitAction: NativeAction?,
    val minimumSelections: Int,
    val maximumSelections: Int,
    val instruction: String = "",
    val referenceImageUrl: HttpUrl? = null,
    val timeLimitSeconds: Int? = null,
    val displayedSecondsRemaining: Int? = null,
) {
    fun validationError(selectedValues: Set<String>): String? = when {
        submitAction == null -> "当前页面没有可用的提交按钮"
        selectedValues.any { value -> choices.none { it.value == value && it.enabled } } -> "所选选项已不可用，请重新选择"
        selectedValues.size !in minimumSelections..maximumSelections -> "请选择 $minimumSelections–$maximumSelections 个选项"
        else -> null
    }

    /** Called only after the person presses submit; checkbox order follows the current DOM. */
    fun submit(selectedValues: Set<String>): NativeAction {
        require(validationError(selectedValues) == null) { validationError(selectedValues).orEmpty() }
        val action = requireNotNull(submitAction)
        val answers = choices.filter { it.enabled && it.value in selectedValues }.map { "riddleanswer[]" to it.value }
        return action.copy(fields = action.fields.filterNot { it.first in setOf("riddleanswer[]", "riddlesubmit") } +
            answers + action.fields.filter { it.first == "riddlesubmit" })
    }
}

/** Saved browser resources retain their source, but do not become invented remote URLs. */
data class RiddleImage(val source: String, val url: HttpUrl?)

data class RiddleChoice(
    val value: String,
    val label: String,
    val checked: Boolean = false,
    val enabled: Boolean = true,
)
