package org.archuser.wallet.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

data class WalletRecord(
    val id: String,
    val name: String,
    val counts: List<Int>,
)

data class WalletStore(
    val wallets: List<WalletRecord>,
    val selectedWalletId: String,
)

class WalletRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadStore(): WalletStore {
        val stored = preferences.getString(KEY_WALLET_STORE, null)
        if (stored.isNullOrBlank()) {
            return migrateLegacyCountsOrDefault()
        }

        return try {
            val store = parseStore(JSONObject(stored))
            saveStore(store)
            store
        } catch (_: JSONException) {
            migrateLegacyCountsOrDefault()
        }
    }

    fun saveStore(store: WalletStore) {
        val normalized = normalizeStore(store)
        val walletsJson = JSONArray()
        normalized.wallets.forEach { wallet ->
            walletsJson.put(
                JSONObject().apply {
                    put("id", wallet.id)
                    put("name", wallet.name)
                    put("counts", JSONArray(wallet.counts))
                }
            )
        }

        val payload = JSONObject().apply {
            put("version", STORE_VERSION)
            put("selectedWalletId", normalized.selectedWalletId)
            put("wallets", walletsJson)
        }

        preferences.edit()
            .putString(KEY_WALLET_STORE, payload.toString())
            .remove(KEY_COUNTS)
            .apply()
    }

    fun clear() {
        saveStore(defaultStore())
    }

    private fun parseStore(json: JSONObject): WalletStore {
        val walletsArray = json.optJSONArray("wallets") ?: JSONArray()
        val wallets = buildList {
            for (index in 0 until walletsArray.length()) {
                val item = walletsArray.optJSONObject(index) ?: continue
                val walletId = item.optString("id").ifBlank { newWalletId() }
                val walletName = sanitizeWalletName(item.optString("name"))
                val countsJson = item.optJSONArray("counts")
                val counts = WalletConfig.DENOMINATIONS.indices.map { countIndex ->
                    val count = countsJson?.optInt(countIndex, 0) ?: 0
                    count.coerceAtLeast(0)
                }
                add(WalletRecord(walletId, walletName, counts))
            }
        }

        val selectedWalletId = json.optString("selectedWalletId")
        return normalizeStore(WalletStore(wallets, selectedWalletId))
    }

    private fun migrateLegacyCountsOrDefault(): WalletStore {
        val legacyCounts = loadLegacyCounts()
        val store = if (legacyCounts != null) {
            WalletStore(
                wallets = listOf(
                    WalletRecord(
                        id = DEFAULT_WALLET_ID,
                        name = DEFAULT_WALLET_NAME,
                        counts = legacyCounts,
                    )
                ),
                selectedWalletId = DEFAULT_WALLET_ID,
            )
        } else {
            defaultStore()
        }
        saveStore(store)
        return store
    }

    private fun loadLegacyCounts(): List<Int>? {
        val stored = preferences.getString(KEY_COUNTS, null) ?: return null
        return try {
            val json = JSONObject(stored)
            WalletConfig.DENOMINATIONS.map { denomination ->
                json.optInt(denomination.toString(), 0).coerceAtLeast(0)
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun normalizeStore(store: WalletStore): WalletStore {
        val wallets = store.wallets
            .mapIndexed { index, wallet ->
                WalletRecord(
                    id = wallet.id.ifBlank { generatedWalletId(index) },
                    name = sanitizeWalletName(wallet.name),
                    counts = normalizeCounts(wallet.counts),
                )
            }
            .ifEmpty {
                listOf(
                    WalletRecord(
                        id = DEFAULT_WALLET_ID,
                        name = DEFAULT_WALLET_NAME,
                        counts = defaultCounts(),
                    )
                )
            }

        val selectedWalletId = store.selectedWalletId
            .takeIf { candidate -> wallets.any { it.id == candidate } }
            ?: wallets.first().id

        return WalletStore(wallets, selectedWalletId)
    }

    private fun normalizeCounts(counts: List<Int>): List<Int> {
        return WalletConfig.DENOMINATIONS.indices.map { index ->
            counts.getOrNull(index)?.coerceAtLeast(0) ?: 0
        }
    }

    private fun sanitizeWalletName(name: String): String {
        return name.trim().ifBlank { DEFAULT_WALLET_NAME }
    }

    private fun defaultStore(): WalletStore {
        return WalletStore(
            wallets = listOf(
                WalletRecord(
                    id = DEFAULT_WALLET_ID,
                    name = DEFAULT_WALLET_NAME,
                    counts = defaultCounts(),
                )
            ),
            selectedWalletId = DEFAULT_WALLET_ID,
        )
    }

    private fun defaultCounts(): List<Int> = List(WalletConfig.DENOMINATIONS.size) { 0 }

    private fun newWalletId(): String = UUID.randomUUID().toString()

    private fun generatedWalletId(index: Int): String = "wallet-${index + 1}"

    companion object {
        private const val PREFS_NAME = "wallet_preferences"
        private const val KEY_COUNTS = "counts"
        private const val KEY_WALLET_STORE = "wallet_store"
        private const val STORE_VERSION = 2
        private const val DEFAULT_WALLET_ID = "wallet-main"
        private const val DEFAULT_WALLET_NAME = "Main wallet"
        fun createWalletId(): String = UUID.randomUUID().toString()
    }
}
