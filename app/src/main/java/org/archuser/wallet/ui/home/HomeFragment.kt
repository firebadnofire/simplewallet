package org.archuser.wallet.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.archuser.wallet.R
import org.archuser.wallet.databinding.FragmentHomeBinding
import org.archuser.wallet.databinding.ItemDenominationEntryBinding
import org.archuser.wallet.ui.shared.WalletViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val walletViewModel: WalletViewModel by activityViewModels()
    private val denominations get() = WalletViewModel.DENOMINATIONS
    private val denominationRows = mutableListOf<ItemDenominationEntryBinding>()

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
            rowBinding.denominationValue.text = getString(R.string.denomination_value_format, 0)
            rowBinding.denominationAddButton.setOnClickListener {
                val quantityChanged = 1
                walletViewModel.increment(index, quantityChanged)
                binding.feedbackMessage.text = getString(
                    R.string.feedback_add_success,
                    quantityChanged,
                    index + 1,
                    denominationDescription(denomination, quantityChanged)
                )
            }
            rowBinding.denominationRemoveButton.setOnClickListener {
                val quantityChanged = 1
                if (walletViewModel.decrement(index, quantityChanged)) {
                    binding.feedbackMessage.text = getString(
                        R.string.feedback_remove_success,
                        quantityChanged,
                        index + 1,
                        denominationDescription(denomination, quantityChanged)
                    )
                } else {
                    binding.feedbackMessage.text = getString(
                        R.string.feedback_remove_empty,
                        index + 1
                    )
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
        binding.totalAmount.text = getString(R.string.total_format, total)

        denominationRows.forEachIndexed { index, row ->
            val totalValue = counts[index] * denominations[index]
            row.denominationValue.text = getString(R.string.denomination_value_format, totalValue)
        }
    }

    private fun denominationDescription(denomination: Int, quantity: Int): String {
        val descriptionRes = if (quantity == 1) {
            R.string.denomination_description_single
        } else {
            R.string.denomination_description_plural
        }
        return getString(descriptionRes, denomination)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        denominationRows.clear()
        _binding = null
    }
}
