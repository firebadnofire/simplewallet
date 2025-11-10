package org.archuser.wallet.data

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

class WalletRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadCounts(): List<Int> {
        val stored = preferences.getString(KEY_COUNTS, null) ?: return defaultCounts()
        return try {
            val json = JSONObject(stored)
            WalletConfig.DENOMINATIONS.map { denomination ->
                val count = json.optInt(denomination.toString(), 0)
                if (count < 0) 0 else count
            }
        } catch (error: JSONException) {
            defaultCounts()
        }
    }

    fun saveCounts(counts: List<Int>) {
        val payload = JSONObject()
        WalletConfig.DENOMINATIONS.forEachIndexed { index, denomination ->
            val count = counts.getOrNull(index) ?: 0
            payload.put(denomination.toString(), if (count < 0) 0 else count)
        }
        preferences.edit().putString(KEY_COUNTS, payload.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_COUNTS).apply()
    }

    private fun defaultCounts(): List<Int> = List(WalletConfig.DENOMINATIONS.size) { 0 }

    companion object {
        private const val PREFS_NAME = "wallet_preferences"
        private const val KEY_COUNTS = "counts"
    }
}
