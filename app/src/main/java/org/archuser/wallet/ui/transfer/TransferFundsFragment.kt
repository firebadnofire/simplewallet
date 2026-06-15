package org.archuser.wallet.ui.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.archuser.wallet.R
import org.archuser.wallet.WalletApplication
import org.archuser.wallet.data.WalletRecord
import org.archuser.wallet.databinding.FragmentTransferFundsBinding
import org.archuser.wallet.databinding.ItemDenominationEntryBinding
import org.archuser.wallet.ui.shared.WalletViewModel
import org.archuser.wallet.ui.shared.WalletViewModelFactory
import java.text.NumberFormat

class TransferFundsFragment : Fragment() {
    private var _binding: FragmentTransferFundsBinding? = null
    private val binding get() = _binding!!
    private val walletViewModel: WalletViewModel by activityViewModels {
        val application = requireActivity().application as WalletApplication
        WalletViewModelFactory(application.walletRepository)
    }
    private val denominations get() = WalletViewModel.DENOMINATIONS
    private val denominationRows = mutableListOf<ItemDenominationEntryBinding>()
    private val currencyFormatter by lazy { NumberFormat.getCurrencyInstance() }

    private var wallets: List<WalletRecord> = emptyList()
    private var fromWalletId: String? = null
    private var toWalletId: String? = null
    private var transferCounts = MutableList(denominations.size) { 0 }
    private var feedbackMessage = ""
    private var isUpdatingDropdowns = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferFundsBinding.inflate(inflater, container, false)
        feedbackMessage = getString(R.string.transfer_feedback_default)
        setupWalletPickers()
        setupSwapButton()
        setupDenominationRows(inflater)
        setupTransferButton()
        updateTransferDisplay()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        walletViewModel.wallets.observe(viewLifecycleOwner) { updatedWallets ->
            wallets = updatedWallets
            reconcileWalletSelections(walletViewModel.selectedWalletId.value)
            if (wallets.size < 2) {
                feedbackMessage = getString(R.string.transfer_feedback_need_second_wallet)
            } else if (transferCounts.sum() == 0 && feedbackMessage == getString(R.string.transfer_feedback_need_second_wallet)) {
                feedbackMessage = getString(R.string.transfer_feedback_default)
            }
            updateTransferDisplay()
        }
    }

    private fun setupWalletPickers() {
        binding.fromWalletDropdown.setOnItemClickListener { _, _, position, _ ->
            val wallet = walletsForFromDropdown().getOrNull(position) ?: return@setOnItemClickListener
            fromWalletId = wallet.id
            clampTransferCountsToSource()
            if (wallets.size >= 2 && transferCounts.sum() == 0) {
                feedbackMessage = getString(R.string.transfer_feedback_default)
            }
            updateTransferDisplay()
        }
        binding.toWalletDropdown.setOnItemClickListener { _, _, position, _ ->
            val wallet = walletsForToDropdown().getOrNull(position) ?: return@setOnItemClickListener
            toWalletId = wallet.id
            if (wallets.size >= 2 && transferCounts.sum() == 0) {
                feedbackMessage = getString(R.string.transfer_feedback_default)
            }
            updateTransferDisplay()
        }
    }

    private fun setupDenominationRows(inflater: LayoutInflater) {
        denominationRows.clear()
        binding.denominationList.removeAllViews()

        denominations.forEachIndexed { index, denomination ->
            val rowBinding = ItemDenominationEntryBinding.inflate(inflater, binding.denominationList, false)
            rowBinding.denominationLabel.text = getString(R.string.denomination_label_format, denomination)
            rowBinding.denominationAddButton.setOnClickListener {
                addDenomination(index)
            }
            rowBinding.denominationRemoveButton.setOnClickListener {
                removeDenomination(index)
            }
            binding.denominationList.addView(rowBinding.root)
            denominationRows.add(rowBinding)
        }
    }

    private fun setupTransferButton() {
        binding.transferButton.setOnClickListener {
            commitTransfer()
        }
    }

    private fun setupSwapButton() {
        binding.swapWalletsCard.setOnClickListener {
            swapWallets()
        }
    }

    private fun addDenomination(index: Int) {
        val sourceWallet = sourceWallet()
        if (sourceWallet == null || destinationWallet() == null) {
            feedbackMessage = getString(R.string.transfer_feedback_need_second_wallet)
            updateTransferDisplay()
            return
        }

        val availableCount = sourceWallet.counts[index]
        if (transferCounts[index] >= availableCount) {
            feedbackMessage = getString(
                R.string.transfer_feedback_insufficient,
                denominations[index],
                sourceWallet.name
            )
            updateTransferDisplay()
            return
        }

        transferCounts[index] += 1
        feedbackMessage = getString(
            R.string.transfer_feedback_add_success,
            formatQuantityWithDenomination(1, denominations[index])
        )
        updateTransferDisplay()
    }

    private fun removeDenomination(index: Int) {
        if (transferCounts[index] == 0) {
            feedbackMessage = getString(R.string.transfer_feedback_remove_empty, denominations[index])
            updateTransferDisplay()
            return
        }

        transferCounts[index] -= 1
        feedbackMessage = getString(
            R.string.transfer_feedback_remove_success,
            formatQuantityWithDenomination(1, denominations[index])
        )
        updateTransferDisplay()
    }

    private fun commitTransfer() {
        val sourceWallet = sourceWallet()
        val receivingWallet = destinationWallet()
        if (sourceWallet == null || receivingWallet == null) {
            feedbackMessage = getString(R.string.transfer_feedback_need_second_wallet)
            updateTransferDisplay()
            return
        }

        val movedBillCount = transferCounts.sum()
        val movedValue = denominations.indices.sumOf { index -> denominations[index] * transferCounts[index] }
        when (walletViewModel.transferFunds(sourceWallet.id, receivingWallet.id, transferCounts)) {
            WalletViewModel.TransferResult.SUCCESS -> {
                transferCounts = MutableList(denominations.size) { 0 }
                feedbackMessage = getString(
                    R.string.transfer_feedback_success,
                    movedBillCount,
                    formatCurrency(movedValue),
                    sourceWallet.name,
                    receivingWallet.name
                )
            }

            WalletViewModel.TransferResult.SAME_WALLET -> {
                feedbackMessage = getString(R.string.transfer_feedback_same_wallet)
            }

            WalletViewModel.TransferResult.EMPTY_TRANSFER -> {
                feedbackMessage = getString(R.string.transfer_feedback_empty_selection)
            }

            WalletViewModel.TransferResult.INSUFFICIENT_FUNDS -> {
                feedbackMessage = getString(R.string.transfer_feedback_failed)
            }

            WalletViewModel.TransferResult.UNKNOWN_WALLET -> {
                feedbackMessage = getString(R.string.transfer_feedback_unknown_wallet)
            }
        }
        updateTransferDisplay()
    }

    private fun reconcileWalletSelections(preferredFromWalletId: String?) {
        if (wallets.isEmpty()) {
            fromWalletId = null
            toWalletId = null
            transferCounts = MutableList(denominations.size) { 0 }
            return
        }

        fromWalletId = wallets.firstOrNull { it.id == fromWalletId }?.id
            ?: wallets.firstOrNull { it.id == preferredFromWalletId }?.id
            ?: wallets.first().id

        toWalletId = wallets.firstOrNull { it.id == toWalletId && it.id != fromWalletId }?.id
            ?: wallets.firstOrNull { it.id != fromWalletId }?.id

        clampTransferCountsToSource()
    }

    private fun clampTransferCountsToSource() {
        val sourceCounts = sourceWallet()?.counts ?: List(denominations.size) { 0 }
        transferCounts = transferCounts.mapIndexed { index, count ->
            count.coerceIn(0, sourceCounts[index])
        }.toMutableList()
    }

    private fun swapWallets() {
        val sourceId = fromWalletId ?: return
        val destinationId = toWalletId ?: return
        fromWalletId = destinationId
        toWalletId = sourceId
        clampTransferCountsToSource()
        feedbackMessage = getString(R.string.transfer_feedback_swapped)
        updateTransferDisplay()
    }

    private fun updateTransferDisplay() {
        val sourceWallet = sourceWallet()
        val receivingWallet = destinationWallet()
        val selectedBills = transferCounts.sum()
        val selectedValue = denominations.indices.sumOf { index -> denominations[index] * transferCounts[index] }

        syncDropdowns(sourceWallet, receivingWallet)

        updateWalletSummary(
            wallet = sourceWallet,
            amountView = binding.fromWalletAmount,
            billsView = binding.fromWalletBills
        )
        updateWalletSummary(
            wallet = receivingWallet,
            amountView = binding.toWalletAmount,
            billsView = binding.toWalletBills
        )

        binding.transferSelectionSummary.text = getString(
            R.string.transfer_total_format,
            formatCurrency(selectedValue),
            selectedBills
        )
        binding.transferFeedbackMessage.text = feedbackMessage
        binding.fromWalletDropdown.isEnabled = wallets.size >= 2
        binding.toWalletDropdown.isEnabled = wallets.size >= 2
        binding.swapWalletsCard.isEnabled = wallets.size >= 2
        binding.swapWalletsCard.alpha = if (wallets.size >= 2) 1f else 0.5f
        binding.transferButton.isEnabled = wallets.size >= 2 && selectedBills > 0

        val sourceCounts = sourceWallet?.counts ?: List(denominations.size) { 0 }
        denominationRows.forEachIndexed { index, row ->
            row.denominationValue.text = getString(
                R.string.transfer_denomination_value_format,
                formatCurrency(transferCounts[index] * denominations[index])
            )
            row.denominationCount.text = getString(
                R.string.transfer_denomination_count_format,
                transferCounts[index],
                sourceCounts[index]
            )
            row.denominationAddButton.isEnabled = receivingWallet != null && transferCounts[index] < sourceCounts[index]
            row.denominationRemoveButton.isEnabled = transferCounts[index] > 0
        }
    }

    private fun syncDropdowns(sourceWallet: WalletRecord?, receivingWallet: WalletRecord?) {
        if (_binding == null || isUpdatingDropdowns) {
            return
        }

        isUpdatingDropdowns = true

        val fromOptions = walletsForFromDropdown()
        val toOptions = walletsForToDropdown()
        val context = requireContext()

        binding.fromWalletDropdown.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_list_item_1, fromOptions.map { it.name })
        )
        binding.toWalletDropdown.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_list_item_1, toOptions.map { it.name })
        )

        binding.fromWalletDropdown.setText(sourceWallet?.name.orEmpty(), false)
        binding.toWalletDropdown.setText(receivingWallet?.name.orEmpty(), false)

        isUpdatingDropdowns = false
    }

    private fun walletsForFromDropdown(): List<WalletRecord> {
        return wallets.filter { it.id != toWalletId }
    }

    private fun walletsForToDropdown(): List<WalletRecord> {
        return wallets.filter { it.id != fromWalletId }
    }

    private fun updateWalletSummary(
        wallet: WalletRecord?,
        amountView: android.widget.TextView,
        billsView: android.widget.TextView
    ) {
        val counts = wallet?.counts.orEmpty()
        val totalBills = counts.sum()
        amountView.text = formatCurrency(walletViewModel.totalAmount(counts))
        billsView.text = resources.getQuantityString(R.plurals.wallet_summary_bills, totalBills, totalBills)
    }

    private fun sourceWallet(): WalletRecord? = wallets.firstOrNull { it.id == fromWalletId }

    private fun destinationWallet(): WalletRecord? = wallets.firstOrNull { it.id == toWalletId }

    private fun formatCurrency(amount: Int): String {
        return currencyFormatter.format(amount.toLong())
    }

    private fun formatQuantityWithDenomination(quantity: Int, denomination: Int): String {
        return getString(R.string.quantity_with_denomination, quantity, denomination)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        denominationRows.clear()
        _binding = null
    }
}
