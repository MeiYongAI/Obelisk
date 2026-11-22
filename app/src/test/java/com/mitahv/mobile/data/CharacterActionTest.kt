package com.mitahv.mobile.data

import com.mitahv.mobile.model.CharacterAttribute
import com.mitahv.mobile.model.CharacterPage
import com.mitahv.mobile.model.PersonaOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterActionTest {
    @Test
    fun personaSwitchMustBeReflectedBySelectedId() {
        val before = page()
        val action = CharacterAction.Switch("2")
        assertNotNull(action.verify(before, before))
        assertNull(action.verify(before, before.copy(personaId = "2", personaName = "Second")))
        assertNotNull(CharacterAction.Switch("3").verify(before, before.copy(personaId = "3")))
    }

    @Test
    fun renameMustKeepPersonaIdentityAndMatchName() {
        val before = page()
        val action = CharacterAction.Rename("人格 + A&B")
        assertNull(action.verify(before, before.copy(personaName = "人格 + A&B")))
        assertNotNull(action.verify(before, before))
        assertNotNull(action.verify(before, before.copy(personaId = "2", personaName = "人格 + A&B")))
    }

    @Test
    fun creationRequiresNewOptionButDoesNotRequireAutoSelection() {
        val before = page()
        val created = before.copy(personaOptions = before.personaOptions + PersonaOption("3", "New"))
        for (kind in listOf("clone", "blank")) {
            val action = CharacterAction.Create(kind)
            assertNull(action.verify(before, created))
            assertNull(action.verify(before, created.copy(personaId = "3", personaName = "New")))
            assertNotNull(action.verify(before, before))
        }
    }

    @Test
    fun creationDoesNotAcceptAReplacementIdOrDuplicateOption() {
        val before = page()
        val action = CharacterAction.Create("blank")
        assertNotNull(action.verify(before, before.copy(personaOptions = listOf(PersonaOption("3", "New")))))
        assertNotNull(action.verify(before, before.copy(personaOptions = listOf(
            PersonaOption("3", "New"), PersonaOption("4", "Replacement"), PersonaOption("5", "Another"),
        ))))
        assertNotNull(action.verify(before, before.copy(personaOptions = before.personaOptions + before.personaOptions.first())))
        assertNotNull(CharacterAction.Create("delete").verify(before, before))
    }

    @Test
    fun allocationUsesCommittedBaselineAndSignedSubmittedDeltas() {
        val before = page().let { original ->
            original.copy(attributes = original.attributes.map {
                if (it.key == "str") it.copy(value = 13, pendingDelta = 3) else it
            })
        }
        val action = CharacterAction.Allocate(mapOf("str" to 2, "dex" to -1))
        val after = before.copy(attributes = before.attributes.map {
            when (it.key) {
                "str" -> it.copy(value = 12, pendingDelta = 0)
                "dex" -> it.copy(value = 9, pendingDelta = 0)
                else -> it
            }
        })

        assertNull(action.verify(before, after))
        assertNotNull(action.verify(before, before))
        assertNotNull(action.verify(before, after.copy(personaId = "2")))
    }

    @Test
    fun allocationRejectsUncommittedPreviewEvenWhenDisplayMatches() {
        val before = page()
        val action = CharacterAction.Allocate(mapOf("str" to 2))
        val preview = before.copy(attributes = before.attributes.map {
            if (it.key == "str") it.copy(value = 12, pendingDelta = 2) else it
        })

        assertNotNull(action.verify(before, preview))
        assertNull(action.verify(before, preview.copy(attributes = preview.attributes.map {
            it.copy(pendingDelta = 0)
        })))
    }

    @Test
    fun allocationRejectsMissingAttributesUnknownFieldsAndUnparseableForm() {
        val before = page()
        val action = CharacterAction.Allocate(emptyMap())
        assertNotNull(action.verify(before, before.copy(attributes = before.attributes.dropLast(1))))
        assertNotNull(action.verify(before, before.copy(canEditAttributes = false)))
        assertNotNull(CharacterAction.Allocate(mapOf("unknown" to 1)).verify(before, before))
    }

    @Test
    fun unmatchedActionUsesServerMessageBeforeGenericError() {
        val before = page()
        val rejected = before.copy(messages = listOf("", "Please wait before switching personas."))
        assertEquals(
            "Please wait before switching personas.",
            CharacterAction.Switch("2").verify(before, rejected),
        )
    }

    @Test
    fun matchingStateIsNotRejectedForUnrelatedServerNotice() {
        val before = page()
        val after = before.copy(personaName = "New name", messages = listOf("Persona renamed."))
        assertNull(CharacterAction.Rename("New name").verify(before, after))
    }

    private fun page() = CharacterPage(
        worldName = "Persistent", level = "10", stamina = "99", personaId = "1", personaName = "First",
        personaOptions = listOf(PersonaOption("1", "First"), PersonaOption("2", "Second")),
        usedPersonaSlots = "2 / 3", usedPersonaCount = 2, personaSlotCapacity = 3,
        canClonePersona = true, canCreateBlankPersona = true, availableExperience = 1000L,
        attributes = listOf("str", "dex", "agi", "end", "int", "wis").map {
            CharacterAttribute(it, it, 10, "+0", "+0", 0, 50L)
        },
        proficiencyGroups = emptyList(), statGroups = emptyList(),
        canEditAttributes = true, canRenamePersona = true, canSwitchPersona = true,
    )
}
