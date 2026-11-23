package com.mitahv.mobile

import com.mitahv.mobile.data.CharacterSectionsParser
import com.mitahv.mobile.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class EquippedUpgradesTest {
    private val world = HvWorld.Persistent
    private val route = HvRoute.Abilities
    private fun url(id: String) = route.url(world).newBuilder().setQueryParameter("tree", id).build()

    @Test fun onlyEquippedServerFormsAreListedOnceAndPartialReadsCanBeRetried() = runBlocking {
        val doc = Jsoup.parse(requireNotNull(javaClass.getResource("/character-sections/abilities.html")).readText())
        for (id in listOf("101", "102")) doc.getElementById("slot_$id")!!.attr("onmouseover", "overability(0, 'HP Tank','HP bonus','Tier 1','Tier 2')")
        doc.getElementById("slot_1101")!!.attr("draggable", "true").attr("onclick", "do_equip_ability(1101)")
        val remote = CharacterSectionsParser.parse(doc, route, world, url("general"))
        val slots = remote.groups.single { it.title == "Ability Slots" }
        val initial = remote.copy(groups = listOf(slots), navigation = listOf(
            CharacterSectionLink("Cloth", null, true, "cloth"),
            CharacterSectionLink("Supportive", NativeAction(url("supportive1"), "GET", label = "Supportive"), false, "supportive1", setOf("g")),
            CharacterSectionLink("General", NativeAction(url("general"), "GET", label = "General"), false, "general", setOf("r"))))
        assertTrue(equippedUpgrades(initial) { throw IOException() }.incomplete)
        val reads = mutableListOf<HttpUrl>()
        val found = equippedUpgrades(initial) { reads += it; remote }
        assertEquals(listOf(url("general")), reads)
        assertFalse(found.incomplete)
        val row = found.rows.single()
        assertEquals("HP Tank", row.title)
        assertEquals(listOf("101", "102"), row.ability!!.equippedSlotIds)
        val post = row.actions.single { ("unlock_ability" to "1101") in it.fields }
        assertEquals("POST", post.method); assertEquals(url("general"), post.url)
        // Visiting the tree again after the server removes its upgrade control must not invent another POST.
        doc.select("#ability_treepane [onclick*=do_unlock_ability]").removeAttr("onclick")
        val updated = CharacterSectionsParser.parse(doc, route, world, url("general"))
        assertEquals(EquippedUpgrades(emptyList(), false), equippedUpgrades(initial) { updated })
    }
}
