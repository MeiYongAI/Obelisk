package com.mitahv.mobile.data

import java.io.IOException
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** Only these non-secret fields are exposed to account management UI. */
internal data class SavedAccountInfo(
    val memberId: String,
    val label: String,
    val isActive: Boolean,
    val hasUsableSession: Boolean,
) {
    val displayName: String get() = label.ifBlank { "账号 $memberId" }
}

/** Session material is intentionally not a data class and is never included in toString(). */
internal class SavedAccountSession(
    val auth: HvAuthCookies,
    val cookies: List<Cookie>,
) {
    val memberId: String get() = auth.memberId
    override fun toString(): String = "SavedAccountSession(redacted)"
}

/** A single committed value makes active-session replacement and saved accounts atomic. */
internal interface AccountPersistence {
    fun readState(): String?
    fun readLegacyCookies(): String?
    fun writeState(value: String): Boolean
}

internal class AccountStore(
    private val persistence: AccountPersistence,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Account(val memberId: String, val label: String, val cookies: List<Cookie>)
    private class State(val activeId: String? = null, val cookies: List<Cookie> = emptyList(), val accounts: List<Account> = emptyList())
    private var state: State = readState()

    @Synchronized
    fun accounts(): List<SavedAccountInfo> = state.accounts.map { account ->
        SavedAccountInfo(account.memberId, account.label, state.activeId == account.memberId, session(account) != null)
    }

    @Synchronized
    fun activeAccountId(): String? = state.activeId

    @Synchronized
    fun hasCookies(): Boolean = activeAuth() != null

    @Synchronized
    fun loadForRequest(url: HttpUrl): List<Cookie> = if (url.isHvUrl()) validCookies(state.cookies).filter { it.matches(url) } else emptyList()

    @Synchronized
    fun savedSession(memberId: String): SavedAccountSession? = state.accounts.firstOrNull { it.memberId == memberId }?.let(::session)

    /** Called only after LoginSessionClient has verified the candidate account against HV. */
    @Synchronized
    fun replaceHvSession(auth: HvAuthCookies, verifiedCookies: List<Cookie>, label: String? = null) {
        val cookies = validCookies(verifiedCookies.filterNot { it.name in HvAuthCookies.names } + auth.forDomain("hentaiverse.org"))
        val old = state.accounts.firstOrNull { it.memberId == auth.memberId }
        val account = Account(auth.memberId, label?.normalizedLabel() ?: old?.label.orEmpty(), cookies)
        val replacement = State(auth.memberId, cookies, upsert(state.accounts, account))
        if (!persist(replacement)) throw IOException("无法保存账号会话，请重试。")
    }

    /** A local switch validates the saved cookie pair before changing the active session. */
    @Synchronized
    fun switchAccount(memberId: String): Boolean {
        val account = state.accounts.firstOrNull { it.memberId == memberId } ?: return false
        val candidate = session(account) ?: return false
        if (state.activeId == memberId && state.cookies == candidate.cookies) return true
        return persist(State(memberId, candidate.cookies, state.accounts))
    }

    @Synchronized
    fun saveCurrentAccount(label: String? = null): SavedAccountInfo? {
        val auth = activeAuth() ?: return null
        val old = state.accounts.firstOrNull { it.memberId == auth.memberId }
        val account = Account(auth.memberId, label?.normalizedLabel() ?: old?.label.orEmpty(), validCookies(state.cookies))
        if (!persist(State(auth.memberId, account.cookies, upsert(state.accounts, account)))) return null
        return SavedAccountInfo(account.memberId, account.label, true, true)
    }

    @Synchronized
    fun renameAccount(memberId: String, label: String): Boolean {
        if (state.accounts.none { it.memberId == memberId }) return false
        val accounts = state.accounts.map { if (it.memberId == memberId) Account(it.memberId, label.normalizedLabel(), it.cookies) else it }
        return persist(State(state.activeId, state.cookies, accounts))
    }

    /** Removing an active account signs out as part of the same local commit. */
    @Synchronized
    fun removeAccount(memberId: String): Boolean {
        if (state.accounts.none { it.memberId == memberId }) return false
        val active = state.activeId != memberId
        return persist(State(if (active) state.activeId else null, if (active) state.cookies else emptyList(),
            state.accounts.filterNot { it.memberId == memberId }))
    }

    /** Clear the active session while keeping every saved account available for switching. */
    @Synchronized
    fun logout(): Boolean = persist(State(accounts = state.accounts))

    @Synchronized
    fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
        if (!url.isHvUrl() || state.activeId == null) return
        val merged = validCookies(state.cookies).associateByTo(linkedMapOf()) { it.key() }
        incoming.filter { it.isHvCookie() }.forEach { cookie ->
            if (cookie.expiresAt <= now()) merged.remove(cookie.key()) else merged[cookie.key()] = cookie
        }
        var cookies = validCookies(merged.values.toList())
        val receivedAuth = authFor(cookies)
        if (receivedAuth != null && receivedAuth.memberId != state.activeId) {
            // A game response cannot silently activate another account. Explicit login/switch owns that transition.
            cookies = validCookies(cookies.filterNot { it.name in HvAuthCookies.names } + state.cookies.filter { it.name in HvAuthCookies.names })
        }
        val accounts = state.accounts.map { if (it.memberId == state.activeId) Account(it.memberId, it.label, cookies) else it }
        if (!persist(State(state.activeId, cookies, accounts))) throw IOException("无法保存账号会话，请重试。")
    }

    private fun activeAuth(): HvAuthCookies? = authFor(state.cookies)?.takeIf { it.memberId == state.activeId }

    private fun session(account: Account): SavedAccountSession? {
        val cookies = validCookies(account.cookies)
        val auth = authFor(cookies)?.takeIf { it.memberId == account.memberId } ?: return null
        return SavedAccountSession(auth, cookies)
    }

    private fun authFor(cookies: List<Cookie>): HvAuthCookies? = HvAuthCookies.fromCookies(validCookies(cookies).filter { it.matches(HV_URL) })

    private fun validCookies(cookies: List<Cookie>): List<Cookie> = cookies.filter { it.isHvCookie() && it.expiresAt > now() }
        .associateBy { it.key() }.values.toList()

    private fun upsert(accounts: List<Account>, replacement: Account): List<Account> =
        if (accounts.any { it.memberId == replacement.memberId }) accounts.map { if (it.memberId == replacement.memberId) replacement else it }
        else accounts + replacement

    private fun persist(replacement: State): Boolean {
        val saved = runCatching { persistence.writeState(encode(replacement)) }.getOrDefault(false)
        if (saved) state = replacement
        return saved
    }

    private fun readState(): State {
        val encoded = persistence.readState()
        if (encoded != null) return runCatching { decode(encoded) }.getOrDefault(State())
        val legacy = persistence.readLegacyCookies() ?: return State()
        val cookies = runCatching { validCookies(AccountCookieCodec.decode(JSONArray(legacy), now())) }.getOrDefault(emptyList())
        val auth = authFor(cookies)
        if (auth == null) return State()
        val migrated = State(auth.memberId, cookies, listOf(Account(auth.memberId, "", cookies)))
        // Migration keeps the legacy key until the new value has been committed successfully.
        runCatching { persistence.writeState(encode(migrated)) }
        return migrated
    }

    private fun encode(value: State): String = JSONObject().put("version", 1)
        .put("activeId", value.activeId ?: JSONObject.NULL)
        .put("cookies", AccountCookieCodec.encode(value.cookies))
        .put("accounts", JSONArray().apply {
            value.accounts.forEach { account -> put(JSONObject().put("memberId", account.memberId).put("label", account.label)
                .put("cookies", AccountCookieCodec.encode(account.cookies))) }
        }).toString()

    private fun decode(encoded: String): State {
        val json = JSONObject(encoded)
        require(json.optInt("version") == 1)
        val list = json.optJSONArray("accounts") ?: JSONArray()
        val accounts = buildList<Account> {
            repeat(list.length()) { index ->
                val item = list.optJSONObject(index) ?: return@repeat
                val id = item.optString("memberId").takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true } ?: return@repeat
                if (any { it.memberId == id }) return@repeat
                val cookies = validCookies(AccountCookieCodec.decode(item.optJSONArray("cookies") ?: JSONArray(), now()))
                add(Account(id, item.optString("label").normalizedLabel(), cookies))
            }
        }
        val activeId = json.optString("activeId").takeIf { id -> accounts.any { it.memberId == id } }
        val cookies = if (activeId == null) emptyList() else validCookies(AccountCookieCodec.decode(json.optJSONArray("cookies") ?: JSONArray(), now()))
        return State(activeId, cookies, accounts)
    }

    private fun String.normalizedLabel(): String = trim().take(80)
    private fun HttpUrl.isHvUrl(): Boolean = scheme == "https" && (host == "hentaiverse.org" || host.endsWith(".hentaiverse.org"))
    private fun Cookie.isHvCookie(): Boolean = domain == "hentaiverse.org" || domain.endsWith(".hentaiverse.org")
    private fun Cookie.key(): String = "$domain|$path|$name"

    private companion object { val HV_URL = "https://hentaiverse.org/".toHttpUrl() }
}

/** Shared with the legacy reader; expiresAt/hostOnly and session-cookie semantics are retained. */
internal object AccountCookieCodec {
    fun encode(cookies: List<Cookie>): JSONArray = JSONArray().apply { cookies.forEach { cookie ->
        put(JSONObject().put("name", cookie.name).put("value", cookie.value).put("domain", cookie.domain)
            .put("path", cookie.path).put("expiresAt", cookie.expiresAt).put("persistent", cookie.persistent)
            .put("hostOnly", cookie.hostOnly).put("secure", cookie.secure).put("httpOnly", cookie.httpOnly))
    } }

    fun decode(array: JSONArray, now: Long): List<Cookie> = buildList {
        repeat(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@repeat
            val cookie = runCatching {
                val persistent = item.optBoolean("persistent")
                val expiresAt = item.optLong("expiresAt", Long.MAX_VALUE)
                if (persistent && expiresAt <= now) return@runCatching null
                val builder = Cookie.Builder().name(item.getString("name")).value(item.getString("value"))
                    .path(item.optString("path", "/"))
                val domain = item.getString("domain")
                if (item.optBoolean("hostOnly")) builder.hostOnlyDomain(domain) else builder.domain(domain)
                if (persistent) builder.expiresAt(expiresAt)
                if (item.optBoolean("secure")) builder.secure()
                if (item.optBoolean("httpOnly")) builder.httpOnly()
                builder.build()
            }.getOrNull()
            if (cookie != null) add(cookie)
        }
    }
}
