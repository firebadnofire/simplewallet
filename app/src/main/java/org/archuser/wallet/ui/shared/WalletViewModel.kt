package org.archuser.wallet.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.archuser.wallet.data.WalletConfig
import org.archuser.wallet.data.WalletRepository
import org.json.JSONException
import org.json.JSONObject

class WalletViewModel(private val repository: WalletRepository) : ViewModel() {

    companion object {
        val DENOMINATIONS = WalletConfig.DENOMINATIONS
    }

    private val _counts = MutableLiveData(repository.loadCounts())
    val counts: LiveData<List<Int>> = _counts

    fun increment(index: Int, quantity: Int = 1) {
        if (index !in DENOMINATIONS.indices || quantity <= 0) return
        val updated = currentCounts().toMutableList()
        updated[index] += quantity
        updateCounts(updated)
    }

    fun decrement(index: Int, quantity: Int = 1): Boolean {
        if (index !in DENOMINATIONS.indices || quantity <= 0) return false
        val current = currentCounts().toMutableList()
        if (current[index] < quantity) {
            return false
        }
        current[index] -= quantity
        updateCounts(current)
        return true
    }

    fun reset() {
        val empty = List(DENOMINATIONS.size) { 0 }
        updateCounts(empty)
        repository.clear()
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
            updateCounts(newCounts)
            true
        } catch (error: JSONException) {
            false
        }
    }

    fun exportData(): String {
        val json = JSONObject()
        val current = currentCounts()
        DENOMINATIONS.forEachIndexed { index, denomination ->
            json.put(denomination.toString(), current[index])
        }
        return json.toString()
    }

    fun totalAmount(counts: List<Int>? = null): Int {
        val values = counts ?: currentCounts()
        return values.zip(DENOMINATIONS) { count, denomination -> count * denomination }.sum()
    }

    private fun updateCounts(newCounts: List<Int>) {
        _counts.value = newCounts
        repository.saveCounts(newCounts)
    }

    private fun currentCounts(): List<Int> {
        return _counts.value ?: repository.loadCounts()
    }
}

class WalletViewModelFactory(private val repository: WalletRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WalletViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
