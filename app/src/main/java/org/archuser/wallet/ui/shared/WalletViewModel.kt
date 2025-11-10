package org.archuser.wallet.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.json.JSONException
import org.json.JSONObject

class WalletViewModel : ViewModel() {

    companion object {
        val DENOMINATIONS = listOf(1, 5, 10, 20, 50, 100)
    }

    private val _counts = MutableLiveData(List(DENOMINATIONS.size) { 0 })
    val counts: LiveData<List<Int>> = _counts

    fun increment(index: Int, quantity: Int = 1) {
        if (index !in DENOMINATIONS.indices || quantity <= 0) return
        val updated = (_counts.value ?: List(DENOMINATIONS.size) { 0 }).toMutableList()
        updated[index] += quantity
        _counts.value = updated
    }

    fun decrement(index: Int, quantity: Int = 1): Boolean {
        if (index !in DENOMINATIONS.indices || quantity <= 0) return false
        val current = (_counts.value ?: List(DENOMINATIONS.size) { 0 }).toMutableList()
        if (current[index] < quantity) {
            return false
        }
        current[index] -= quantity
        _counts.value = current
        return true
    }

    fun reset() {
        _counts.value = List(DENOMINATIONS.size) { 0 }
    }

    fun importData(raw: String): Boolean {
        return try {
            val json = JSONObject(raw)
            val newCounts = mutableListOf<Int>()
            for (denomination in DENOMINATIONS) {
                if (!json.has(denomination.toString())) {
                    return false
                }
                val count = json.getInt(denomination.toString())
                if (count < 0) {
                    return false
                }
                newCounts += count
            }
            _counts.value = newCounts
            true
        } catch (error: JSONException) {
            false
        }
    }

    fun exportData(): String {
        val json = JSONObject()
        val current = _counts.value ?: List(DENOMINATIONS.size) { 0 }
        DENOMINATIONS.forEachIndexed { index, denomination ->
            json.put(denomination.toString(), current[index])
        }
        return json.toString()
    }

    fun totalAmount(counts: List<Int>? = null): Int {
        val values = counts ?: (_counts.value ?: List(DENOMINATIONS.size) { 0 })
        return values.zip(DENOMINATIONS) { count, denomination -> count * denomination }.sum()
    }
}
