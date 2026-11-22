package com.mitahv.mobile.model

enum class MailViewMode { List, Detail, Compose }

/** Names, subjects, bodies and source timestamps are user content, not translation keys. */
data class MailListRow(
    val id: String,
    val correspondent: String,
    val subject: String,
    val sentAt: String,
    val readAt: String,
    val unread: Boolean,
    val action: NativeAction,
)

data class MailMessageDetail(
    val to: String = "",
    val from: String = "",
    val subject: String = "",
    val body: String = "",
)

data class MailPageInfo(
    val mode: MailViewMode,
    val folder: String,
    val folders: List<BazaarSectionsLink> = emptyList(),
    val previousPage: NativeAction? = null,
    val nextPage: NativeAction? = null,
    val listAction: NativeAction? = null,
    val composeAction: NativeAction? = null,
    val replyAction: NativeAction? = null,
    val rows: List<MailListRow> = emptyList(),
    val detail: MailMessageDetail? = null,
    val postage: Long? = null,
    val attachmentCount: Long? = null,
    val attachmentLimit: Long? = null,
    val cod: Long? = null,
)
