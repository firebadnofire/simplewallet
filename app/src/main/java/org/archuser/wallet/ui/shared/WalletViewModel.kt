package org.archuser.wallet.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.archuser.wallet.data.WalletConfig
import org.archuser.wallet.data.WalletRecord
import org.archuser.wallet.data.WalletRepository
import org.archuser.wallet.data.WalletStore
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class WalletViewModel(private val repository: WalletRepository) : ViewModel() {

    companion object {
        val DENOMINATIONS = WalletConfig.DENOMINATIONS
    }

    enum class TransferResult {
        SUCCESS,
        SAME_WALLET,
        EMPTY_TRANSFER,
        INSUFFICIENT_FUNDS,
        UNKNOWN_WALLET,
    }

    private var store: WalletStore = repository.loadStore()

    private val _wallets = MutableLiveData(store.wallets)
    val wallets: LiveData<List<WalletRecord>> = _wallets

    private val _selectedWalletId = MutableLiveData(store.selectedWalletId)
    val selectedWalletId: LiveData<String> = _selectedWalletId

    private val _counts = MutableLiveData(currentWallet().counts)
    val counts: LiveData<List<Int>> = _counts

    private val _selectedWalletName = MutableLiveData(currentWallet().name)
    val selectedWalletName: LiveData<String> = _selectedWalletName

    fun getWallet(walletId: String?): WalletRecord? {
        return store.wallets.firstOrNull { it.id == walletId }
    }

    fun selectWallet(walletId: String): Boolean {
        if (store.wallets.none { it.id == walletId }) {
            return false
        }
        if (store.selectedWalletId == walletId) {
            syncState()
            return true
        }
        store = store.copy(selectedWalletId = walletId)
        persistStore()
        return true
    }

    fun createWallet(name: String): WalletRecord {
        val existingNames = store.wallets.map { it.name }.toSet()
        val resolvedName = buildWalletName(name, existingNames)
        val wallet = WalletRecord(
            id = WalletRepository.createWalletId(),
            name = resolvedName,
            counts = List(DENOMINATIONS.size) { 0 },
        )
        store = store.copy(
            wallets = store.wallets + wallet,
            selectedWalletId = wallet.id,
        )
        persistStore()
        return wallet
    }

    fun increment(index: Int, quantity: Int = 1) {
        if (index !in DENOMINATIONS.indices || quantity <= 0) return
        mutateCurrentWalletCounts { current ->
            current[index] += quantity
            current
        }
    }

    fun decrement(index: Int, quantity: Int = 1): Boolean {
        if (index !in DENOMINATIONS.indices || quantity <= 0) return false
        var changed = false
        mutateCurrentWalletCounts { current ->
            if (current[index] < quantity) {
                return@mutateCurrentWalletCounts current
            }
            current[index] -= quantity
            changed = true
            current
        }
        return changed
    }

    fun reset() {
        mutateCurrentWalletCounts {
            List(DENOMINATIONS.size) { 0 }.toMutableList()
        }
    }

    fun importAllWallets(raw: String): Boolean {
        return try {
            val importedStore = parseImportedStore(JSONObject(raw))
            store = importedStore
            persistStore()
            true
        } catch (_: JSONException) {
            false
        }
    }

    fun exportAllWallets(): String {
        val walletsArray = JSONArray()
        store.wallets.forEach { wallet ->
            walletsArray.put(
                JSONObject().apply {
                    put("id", wallet.id)
                    put("name", wallet.name)
                    put("counts", JSONArray(wallet.counts))
                }
            )
        }

        return JSONObject().apply {
            put("version", 1)
            put("selectedWalletId", store.selectedWalletId)
            put("wallets", walletsArray)
        }.toString(2)
    }

    fun totalAmount(counts: List<Int>? = null): Int {
        val values = counts ?: currentWallet().counts
        return values.zip(DENOMINATIONS) { count, denomination -> count * denomination }.sum()
    }

    fun transferFunds(fromWalletId: String, toWalletId: String, transferCounts: List<Int>): TransferResult {
        if (fromWalletId == toWalletId) {
            return TransferResult.SAME_WALLET
        }

        val fromWallet = getWallet(fromWalletId) ?: return TransferResult.UNKNOWN_WALLET
        val toWallet = getWallet(toWalletId) ?: return TransferResult.UNKNOWN_WALLET
        val normalizedTransferCounts = DENOMINATIONS.indices.map { index ->
            transferCounts.getOrNull(index)?.coerceAtLeast(0) ?: 0
        }

        if (normalizedTransferCounts.none { it > 0 }) {
            return TransferResult.EMPTY_TRANSFER
        }

        if (normalizedTransferCounts.indices.any { index ->
                normalizedTransferCounts[index] > fromWallet.counts[index]
            }) {
            return TransferResult.INSUFFICIENT_FUNDS
        }

        val updatedFromWallet = fromWallet.copy(
            counts = fromWallet.counts.indices.map { index ->
                fromWallet.counts[index] - normalizedTransferCounts[index]
            }
        )
        val updatedToWallet = toWallet.copy(
            counts = toWallet.counts.indices.map { index ->
                toWallet.counts[index] + normalizedTransferCounts[index]
            }
        )

        store = store.copy(
            wallets = store.wallets.map { wallet ->
                when (wallet.id) {
                    updatedFromWallet.id -> updatedFromWallet
                    updatedToWallet.id -> updatedToWallet
                    else -> wallet
                }
            }
        )
        persistStore()
        return TransferResult.SUCCESS
    }

    private fun mutateCurrentWalletCounts(transform: (MutableList<Int>) -> MutableList<Int>) {
        val current = currentWallet()
        val updatedCounts = transform(current.counts.toMutableList())
        val updatedWallet = current.copy(counts = updatedCounts.map { it.coerceAtLeast(0) })
        store = store.copy(
            wallets = store.wallets.map { wallet ->
                if (wallet.id == current.id) updatedWallet else wallet
            }
        )
        persistStore()
    }

    private fun parseImportedStore(json: JSONObject): WalletStore {
        val walletsArray = json.optJSONArray("wallets") ?: throw JSONException("Missing wallets")
        val wallets = buildList {
            for (index in 0 until walletsArray.length()) {
                val walletJson = walletsArray.optJSONObject(index) ?: continue
                val walletId = walletJson.optString("id").ifBlank {
                    WalletRepository.createWalletId()
                }
                val walletName = walletJson.optString("name").trim()
                if (walletName.isBlank()) {
                    throw JSONException("Wallet name is required")
                }
                val countsJson = walletJson.optJSONArray("counts") ?: throw JSONException("Missing counts")
                val counts = DENOMINATIONS.indices.map { countIndex ->
                    if (countIndex >= countsJson.length()) {
                        throw JSONException("Missing denomination count")
                    }
                    val count = countsJson.getInt(countIndex)
                    if (count < 0) {
                        throw JSONException("Negative count")
                    }
                    count
                }
                add(WalletRecord(walletId, walletName, counts))
            }
        }
        if (wallets.isEmpty()) {
            throw JSONException("No wallets")
        }

        val selectedWalletId = json.optString("selectedWalletId")
            .takeIf { candidate -> wallets.any { it.id == candidate } }
            ?: wallets.first().id

        return WalletStore(wallets, selectedWalletId)
    }

    private fun currentWallet(): WalletRecord {
        return store.wallets.firstOrNull { it.id == store.selectedWalletId } ?: store.wallets.first()
    }

    private fun persistStore() {
        repository.saveStore(store)
        syncState()
    }

    private fun syncState() {
        val wallet = currentWallet()
        _wallets.value = store.wallets
        _selectedWalletId.value = wallet.id
        _selectedWalletName.value = wallet.name
        _counts.value = wallet.counts
    }

    private fun buildWalletName(requestedName: String, existingNames: Set<String>): String {
        val baseName = requestedName.trim().ifBlank { "Wallet" }
        if (baseName !in existingNames) {
            return baseName
        }
        var suffix = 2
        while (true) {
            val candidate = "$baseName $suffix"
            if (candidate !in existingNames) {
                return candidate
            }
            suffix += 1
        }
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
