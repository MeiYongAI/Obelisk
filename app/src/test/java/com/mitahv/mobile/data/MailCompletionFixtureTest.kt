package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class MailCompletionFixtureTest {
    @Test fun nonzeroCodShowsTheActualPaymentAndAttachmentBeforeClaiming() {
        val inbox = page("cod-inbox", "&filter=inbox")
        assertEquals("1003", inbox.sections.flatMap { it.entries }.single().action!!.url.queryParameter("mid"))
        val received = page("cod-received", "&filter=inbox&mid=1003")
        val claim = received.actions.single { it.fields.toMap()["action"] == "attach_remove" }
        assertEquals("支付 10000 C 并领取", claim.label)
        assertTrue(claim.confirmation!!.contains("10000 C"))
        assertEquals(mapOf("mmtoken" to "fixture-mail-token", "action" to "attach_remove", "action_value" to "0"), claim.fields.toMap())
        assertTrue(received.actions.any { it.label == "退回邮件" && it.fields.toMap()["action"] == "return_message" })
        val attachments = received.sections.single { it.title == "附件" }
        assertTrue(attachments.facts.any { it.second.contains("10,000") })
        assertEquals("Legendary Cobalt Force Shield of Deflection", attachments.entries.single().title)
        assertTrue(attachments.entries.single().moreDetails.any { it.second.contains("Block") })
    }

    private fun page(name: String, query: String = "&filter=sent&mid=1001") = BazaarSectionsParser.parse(
        javaClass.getResource("/mail-completion/$name.html")!!.readText(), HvRoute.MoogleMail, HvWorld.Persistent,
        (HvRoute.MoogleMail.url(HvWorld.Persistent).toString() + query).toHttpUrl())

    @Test fun sentRecallUsesTheRenderedRecallControlAndOriginalPostProtocol() {
        val page = page("sent-recallable")
        assertTrue(page.forms.isEmpty())
        val action = page.actions.single()
        assertEquals("撤回邮件", action.label)
        assertEquals("return_message", action.fields.toMap()["action"])
        assertEquals("fixture-mail-token", action.fields.toMap()["mmtoken"])
        assertEquals("POST", action.method)
        assertEquals(2, page.sections.single { it.title == "附件" }.entries.size)
        assertTrue(page.actions.none { it.fields.toMap()["action"] == "attach_remove" })
    }

    @Test fun returnedMailCanClaimExactlyOnceAndClaimedHtmlHasNoInventedControls() {
        for (name in listOf("sent-recalled", "inbox-returned-unclaimed")) {
            val page = page(name, "&filter=inbox&mid=1001")
            val action = page.actions.single()
            assertEquals("领取全部附件", action.label)
            assertEquals("attach_remove", action.fields.toMap()["action"])
            assertEquals("0", action.fields.toMap()["action_value"])
            assertTrue(page.forms.isEmpty())
        }
        val claimed = page("inbox-returned-claimed", "&filter=inbox&mid=1001")
        assertTrue(claimed.actions.isEmpty())
        assertTrue(claimed.sections.none { it.title == "附件" })
        assertTrue(claimed.sections.flatMap { it.facts }.any { it.first == "正文" })
    }

    @Test fun readMessageUsesRowLabelsAndKeepsBodySeparateFromItsParentText() {
        val page = page("read-message", "&filter=read&mid=1002")
        val facts = page.sections.single().facts
        assertEquals(listOf("收件人", "寄信人", "主题", "正文"), facts.map { it.first })
        assertFalse(facts.last().first.contains("To:"))
        val reply = page.links.single { it.label == "回复" }.action
        assertEquals("GET", reply.method)
        assertEquals("1002", reply.url.queryParameter("reply"))
        assertTrue(page.actions.isEmpty())
        val info = page.mail!!
        val detail = info.detail!!
        assertEquals(MailViewMode.Detail, info.mode)
        assertEquals("read", info.folder)
        assertEquals("Fixture User 2", detail.to)
        assertEquals("Fixture User 4", detail.from)
        assertEquals("Fixture subject 4", detail.subject)
        assertEquals("Fixture message body.", detail.body)
        assertEquals(reply, info.replyAction)
        assertEquals("read", info.listAction!!.url.queryParameter("filter"))
        assertNull(info.listAction!!.url.queryParameter("mid"))
    }

    @Test fun actualComposeAttachmentsAreGroupedAndPreserveCurrentDraftOnPost() {
        val page = page("compose-with-attachments", "&filter=new")
        val form = page.forms.single()
        assertEquals(setOf("已附加", "货币", "道具", "装备"), form.attachments.map { it.category }.toSet())
        assertEquals(3, page.sections.single { it.title == "附件" }.entries.size)
        val all = form.attachments.single { it.action?.fields?.toMap()?.get("action_value") == "0" }.action!!
        assertEquals("移除全部附件", all.label)
        val remove = form.attachments.first { it.action?.fields?.toMap()?.get("action_value") == "1" }.action!!
        val action = form.submit(remove, mapOf("message_to_name" to "Local recipient", "message_subject" to "Local title", "message_body" to "Line one\nLine two"))
        assertEquals("attach_remove", action.fields.toMap()["action"])
        assertEquals("Line one\nLine two", action.fields.toMap()["message_body"])
        assertEquals("Local title", action.fields.toMap()["message_subject"])
        val cod = form.attachments.single { it.key == "mail-cod" }.quantity!!
        assertEquals("0", cod.submit(0).fields.toMap()["action_value"])
        assertEquals(999999999L, cod.maximum)
        assertTrue(form.attachments.any { it.quantity?.action?.confirmation?.contains("受保护装备") == true })
        val attached = page.sections.single { it.title == "附件" }.entries
        assertEquals(listOf("1", "2", "3"), attached.map { it.action!!.fields.toMap()["action_value"] })
        assertTrue(attached.all { it.action!!.fields.toMap()["action"] == "attach_remove" })
        val mail = page.mail!!
        assertEquals(MailViewMode.Compose, mail.mode)
        assertEquals(3L, mail.attachmentCount)
        assertEquals(10L, mail.attachmentLimit)
        assertEquals(0L, mail.cod)
        assertEquals("inbox", mail.listAction!!.url.queryParameter("filter"))
    }

    @Test fun returnedInboxKeepsNativeMessageNavigation() {
        val page = page("inbox-after-recall", "&filter=inbox")
        val row = page.sections.flatMap { it.entries }.first { it.action?.url?.queryParameter("mid") == "1001" }
        assertEquals("GET", row.action!!.method)
        assertEquals("inbox", row.action.url.queryParameter("filter"))
    }

    @Test fun attachedItemsKeepTheirDescriptionsAndTheRenderedAttachmentLimit() {
        val html = javaClass.getResource("/mail-completion/compose-with-attachments.html")!!.readText()
            .replace("3 / 10 items attached", "3 / 12 items attached")
        val page = BazaarSectionsParser.parse(html, HvRoute.MoogleMail, HvWorld.Persistent,
            (HvRoute.MoogleMail.url(HvWorld.Persistent).toString() + "&filter=new").toHttpUrl(),
            supplementalScripts = listOf("""var dynjs_itemc = {"11191":{"q":"<p>Restores <b>health</b>.</p>"}};"""))
        val attachments = page.sections.single { it.title == "附件" }
        assertTrue(attachments.facts.any { it.second == "3 / 12 items attached" })
        assertEquals(listOf("" to "Restores health."),
            attachments.entries.single { it.title == "1x Health Draught" }.moreDetails)
        assertEquals(12L, page.mail!!.attachmentLimit)
    }

    @Test fun mailListMetadataSeparatesFoldersPagerAndOriginalUserContentOnIsekai() {
        val current = HvRoute.MoogleMail.url(HvWorld.Isekai).newBuilder()
            .setQueryParameter("filter", "sent").setQueryParameter("page", "1").build()
        val html = """<div id=mmail_outer><div id=filterbar>
            <a href='?s=Bazaar&amp;ss=mm&amp;filter=inbox'><div class=cfb>Inbox</div></a>
            <a href='?s=Bazaar&amp;ss=mm&amp;filter=new'><div class=cfb>Write New</div></a>
            <a href='?s=Bazaar&amp;ss=mm&amp;filter=sent'><div class=cfbs>Sent Mail</div></a></div>
            <table id=mmail_list><tr><th>TO</th><th>SUBJECT</th><th>SENT</th><th>READ</th></tr>
            <tr onclick="document.location='?s=Bazaar&amp;ss=mm&amp;filter=sent&amp;mid=12'"><td>Legendary Friend</td><td>Health Potion 中文</td><td>2026-11-21 12:30</td><td>Never</td></tr>
            <tr onclick="document.location='?s=Bazaar&amp;ss=mm&amp;filter=sent&amp;mid=13'"><td>Other Player</td><td>Re: auction</td><td>2026-11-20 08:15</td><td>2026-11-21 09:30</td></tr></table>
            <div id=mmail_pager><div><a href='?s=Bazaar&amp;ss=mm&amp;filter=sent&amp;page=0'>&lt; Prev</a></div>
            <div><a href='?s=Bazaar&amp;ss=mm&amp;filter=sent&amp;page=2'>Next &gt;</a></div></div></div>"""
        val info = BazaarSectionsParser.parse(html, HvRoute.MoogleMail, HvWorld.Isekai, current).mail!!
        assertEquals(MailViewMode.List, info.mode)
        assertEquals("sent", info.folder)
        assertEquals(3, info.folders.size)
        assertEquals("0", info.previousPage!!.url.queryParameter("page"))
        assertEquals("2", info.nextPage!!.url.queryParameter("page"))
        assertEquals("new", info.composeAction!!.url.queryParameter("filter"))
        assertEquals("Legendary Friend", info.rows.first().correspondent)
        assertEquals("Health Potion 中文", info.rows.first().subject)
        assertTrue(info.rows.first().unread)
        assertFalse(info.rows.last().unread)
        assertEquals("2026-11-21 09:30", info.rows.last().readAt)
        assertEquals(current.host, info.rows.first().action.url.host)
        assertEquals(current.encodedPath, info.listAction!!.url.encodedPath)
    }

    @Test fun mailDetailRecognizesUppercaseLabelsAndPreservesBodyLineBreaks() {
        val html = javaClass.getResource("/mail-completion/read-message.html")!!.readText()
            .replace("To:", "TO:").replace("From:", "FROM:").replace("Subject:", "SUBJECT:")
            .replace("Fixture message body.", "Health Potion\n\n中文 body &amp; punctuation.")
        val detail = BazaarSectionsParser.parse(html, HvRoute.MoogleMail, HvWorld.Persistent,
            (HvRoute.MoogleMail.url(HvWorld.Persistent).toString() + "&filter=read&mid=1002").toHttpUrl()).mail!!.detail!!
        assertEquals("Fixture User 2", detail.to)
        assertEquals("Fixture User 4", detail.from)
        assertEquals("Fixture subject 4", detail.subject)
        assertEquals("Health Potion\n\n中文 body & punctuation.", detail.body)
    }
}
