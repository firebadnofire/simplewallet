package org.archuser.wallet.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.archuser.wallet.WalletApplication
import org.archuser.wallet.R
import org.archuser.wallet.databinding.FragmentHomeBinding
import org.archuser.wallet.databinding.ItemDenominationEntryBinding
import org.archuser.wallet.ui.shared.WalletViewModel
import org.archuser.wallet.ui.shared.WalletViewModelFactory
import java.text.NumberFormat

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val walletViewModel: WalletViewModel by activityViewModels {
        val application = requireActivity().application as WalletApplication
        WalletViewModelFactory(application.walletRepository)
    }
    private val denominations get() = WalletViewModel.DENOMINATIONS
    private val denominationRows = mutableListOf<ItemDenominationEntryBinding>()
    private val currencyFormatter by lazy { NumberFormat.getCurrencyInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupDenominationRows(inflater)
        setupResetButton()
        binding.feedbackMessage.text = getString(R.string.feedback_default)
        walletViewModel.counts.value?.let { updateDisplay(it) }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        walletViewModel.counts.observe(viewLifecycleOwner) { counts ->
            updateDisplay(counts)
        }
    }

    private fun setupDenominationRows(inflater: LayoutInflater) {
        denominationRows.clear()
        binding.denominationList.removeAllViews()

        denominations.forEachIndexed { index, denomination ->
            val rowBinding = ItemDenominationEntryBinding.inflate(inflater, binding.denominationList, false)
            rowBinding.denominationLabel.text = getString(R.string.denomination_label_format, denomination)
            rowBinding.denominationValue.text = getString(R.string.denomination_value_format, formatCurrency(0))
            rowBinding.denominationCount.text = resources.getQuantityString(R.plurals.denomination_count, 0, 0)
            rowBinding.denominationRemoveButton.isEnabled = false
            rowBinding.denominationAddButton.setOnClickListener {
                val quantityChanged = 1
                walletViewModel.increment(index, quantityChanged)
                binding.feedbackMessage.text = getString(
                    R.string.feedback_add_success,
                    formatQuantityWithDenomination(quantityChanged, denomination)
                )
            }
            rowBinding.denominationRemoveButton.setOnClickListener {
                val quantityChanged = 1
                if (walletViewModel.decrement(index, quantityChanged)) {
                    binding.feedbackMessage.text = getString(
                        R.string.feedback_remove_success,
                        formatQuantityWithDenomination(quantityChanged, denomination)
                    )
                } else {
                    binding.feedbackMessage.text = getString(R.string.feedback_remove_empty, denomination)
                }
            }
            binding.denominationList.addView(rowBinding.root)
            denominationRows.add(rowBinding)
        }
    }

    private fun setupResetButton() {
        binding.resetButton.setOnClickListener {
            binding.feedbackMessage.text = getString(R.string.feedback_default)
            walletViewModel.reset()
        }
    }

    private fun updateDisplay(counts: List<Int>) {
        val total = counts.zip(denominations) { count, denomination -> count * denomination }.sum()
        val totalBills = counts.sum()
        binding.totalAmount.text = formatCurrency(total)
        binding.totalBills.text = resources.getQuantityString(R.plurals.wallet_summary_bills, totalBills, totalBills)

        denominationRows.forEachIndexed { index, row ->
            val totalValue = counts[index] * denominations[index]
            row.denominationValue.text = getString(R.string.denomination_value_format, formatCurrency(totalValue))
            row.denominationCount.text = resources.getQuantityString(R.plurals.denomination_count, counts[index], counts[index])
            row.denominationRemoveButton.isEnabled = counts[index] > 0
        }
    }

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
