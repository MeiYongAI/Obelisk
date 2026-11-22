package com.mitahv.mobile.data

import com.mitahv.mobile.model.CharacterPage

internal sealed interface CharacterAction {
    data class Switch(val personaId: String) : CharacterAction
    data class Rename(val name: String) : CharacterAction
    data class Create(val kind: String) : CharacterAction
    data class Allocate(val deltas: Map<String, Int>) : CharacterAction
}

/** Checks the returned server state rather than treating HTTP 200 as a completed action. */
internal fun CharacterAction.verify(before: CharacterPage, after: CharacterPage): String? {
    val matched = when (this) {
        is CharacterAction.Switch -> personaId.isNotBlank() &&
            before.personaOptions.any { it.id == personaId } && after.personaId == personaId
        is CharacterAction.Rename -> name.isNotBlank() && before.personaId.isNotBlank() &&
            after.personaId == before.personaId && after.personaName == name
        is CharacterAction.Create -> {
            val beforeIds = before.personaOptions.map { it.id }.toSet()
            val afterIds = after.personaOptions.map { it.id }.toSet()
            kind in setOf("clone", "blank") &&
                after.personaOptions.size > before.personaOptions.size &&
                afterIds.containsAll(beforeIds) && afterIds.size > beforeIds.size &&
                afterIds.any { it.isNotBlank() && it !in beforeIds }
        }
        is CharacterAction.Allocate -> {
            val beforeAttributes = before.attributes.associateBy { it.key }
            val afterAttributes = after.attributes.associateBy { it.key }
            before.personaId.isNotBlank() && after.personaId == before.personaId &&
                before.canEditAttributes && after.canEditAttributes &&
                deltas.keys.all { it in ATTRIBUTE_KEYS } &&
                before.attributes.size == ATTRIBUTE_KEYS.size && after.attributes.size == ATTRIBUTE_KEYS.size &&
                beforeAttributes.keys == ATTRIBUTE_KEYS && afterAttributes.keys == ATTRIBUTE_KEYS &&
                ATTRIBUTE_KEYS.all { key ->
                    val attribute = beforeAttributes.getValue(key)
                    val expected = attribute.value.toLong() - attribute.pendingDelta + (deltas[key] ?: 0)
                    expected in 0..Int.MAX_VALUE.toLong() &&
                        afterAttributes.getValue(key).pendingDelta == 0 &&
                        afterAttributes.getValue(key).value.toLong() == expected
                }
        }
    }
    if (matched) return null

    val serverMessage = after.messages.filter(String::isNotBlank).joinToString("\n")
    if (serverMessage.isNotBlank()) return serverMessage
    return when (this) {
        is CharacterAction.Switch -> "服务器未切换到所选人格。"
        is CharacterAction.Rename -> "服务器未保存人格名称。"
        is CharacterAction.Create -> "服务器未返回新人格，请刷新确认。"
        is CharacterAction.Allocate -> "服务器返回的属性与提交不一致，请检查后重新分配。"
    }
}

private val ATTRIBUTE_KEYS = setOf("str", "dex", "agi", "end", "int", "wis")
