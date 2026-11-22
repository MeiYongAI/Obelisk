package com.mitahv.mobile.model

data class PersonaOption(val id: String, val name: String)

data class CharacterPage(
    val worldName: String,
    val level: String,
    val stamina: String,
    val personaId: String,
    val personaName: String,
    val personaOptions: List<PersonaOption>,
    val usedPersonaSlots: String,
    val usedPersonaCount: Int?,
    val personaSlotCapacity: Int?,
    val canClonePersona: Boolean,
    val canCreateBlankPersona: Boolean,
    val availableExperience: Long,
    val attributes: List<CharacterAttribute>,
    val proficiencyGroups: List<CharacterStatGroup>,
    val statGroups: List<CharacterStatGroup>,
    val canEditAttributes: Boolean = false,
    val canRenamePersona: Boolean = false,
    val canSwitchPersona: Boolean = false,
    val messages: List<String> = emptyList(),
)

data class CharacterAttribute(
    val key: String,
    val name: String,
    val value: Int,
    val equipmentBonus: String,
    val secondaryBonus: String,
    val pendingDelta: Int,
    val nextExperience: Long,
)

data class CharacterStatGroup(
    val name: String,
    val values: List<CharacterStat>,
)

data class CharacterStat(val label: String, val value: String)
