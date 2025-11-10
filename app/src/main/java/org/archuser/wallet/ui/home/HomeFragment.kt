package org.archuser.wallet.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.archuser.wallet.R
import org.archuser.wallet.databinding.FragmentHomeBinding
import org.archuser.wallet.databinding.ItemDenominationEntryBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val denominations = listOf(1, 5, 10, 20, 50, 100)
    private val counts = MutableList(denominations.size) { 0 }
    private val denominationRows = mutableListOf<ItemDenominationEntryBinding>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupDenominationRows(inflater)
        setupResetButton()
        updateDisplay()
        binding.feedbackMessage.text = getString(R.string.feedback_default)
        return binding.root
    }

    private fun setupDenominationRows(inflater: LayoutInflater) {
        denominationRows.clear()
        binding.denominationList.removeAllViews()

        denominations.forEachIndexed { index, denomination ->
            val rowBinding = ItemDenominationEntryBinding.inflate(inflater, binding.denominationList, false)
            rowBinding.denominationLabel.text = getString(R.string.denomination_label_format, denomination)
            rowBinding.denominationValue.text = getString(R.string.denomination_value_format, 0)
            rowBinding.denominationAddButton.setOnClickListener {
                counts[index] += 1
                val quantityAdded = 1
                binding.feedbackMessage.text = getString(
                    R.string.feedback_success,
                    quantityAdded,
                    index + 1,
                    denominationDescription(denomination, quantityAdded)
                )
                updateDisplay()
            }
            binding.denominationList.addView(rowBinding.root)
            denominationRows.add(rowBinding)
        }
    }

    private fun setupResetButton() {
        binding.resetButton.setOnClickListener {
            for (index in counts.indices) {
                counts[index] = 0
            }
            binding.feedbackMessage.text = getString(R.string.feedback_default)
            updateDisplay()
        }
    }

    private fun updateDisplay() {
        val slotRow = buildString {
            append("|")
            for (index in denominations.indices) {
                append("   ${index + 1}   |")
            }
        }
        val denominationRow = denominations.joinToString(separator = "") { "[${it}s]" }
        val amountsRow = buildString {
            append("(")
            append(
                counts.zip(denominations) { count, denomination ->
                    "$$${count * denomination}"
                }.joinToString(")(")
            )
            append(")")
        }
        binding.walletDisplay.text = listOf(slotRow, denominationRow, amountsRow).joinToString("\n")

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
        _binding = null
    }
}
