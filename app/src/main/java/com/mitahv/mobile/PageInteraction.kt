package com.mitahv.mobile

import com.mitahv.mobile.model.NativeAction

internal val NativeAction.isPageNavigation: Boolean get() = method == "GET" && confirmation == null

/** A displayed snapshot can supply links while its forms are waiting for a fresh response. */
internal data class PageInteraction(val canNavigate: Boolean, val canSubmit: Boolean) {
    fun allows(action: NativeAction?): Boolean = when {
        action == null -> false
        action.isPageNavigation -> canNavigate
        else -> canSubmit
    }
}
