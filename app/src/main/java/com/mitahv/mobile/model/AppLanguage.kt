package com.mitahv.mobile.model

enum class AppLanguage(val tag: String) {
    System(""), English("en"), SimplifiedChinese("zh-CN");

    companion object {
        fun fromTag(tag: String?) = when {
            tag.isNullOrBlank() -> System
            tag.startsWith("zh", ignoreCase = true) -> SimplifiedChinese
            else -> English
        }
    }
}
