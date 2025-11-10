package org.archuser.wallet.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import org.archuser.wallet.R
import org.archuser.wallet.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val denominations = listOf(1, 5, 10, 20, 50, 100)
    private val counts = MutableList(denominations.size) { 0 }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupSpinner()
        setupButtons()
        updateDisplay()
        binding.feedbackMessage.text = getString(R.string.feedback_default)
        return binding.root
    }

    private fun setupSpinner() {
        val spinnerItems = denominations.mapIndexed { index, value ->
            getString(R.string.spinner_item_format, index + 1, value)
        }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            spinnerItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.denominationSpinner.adapter = adapter
        binding.denominationSpinner.prompt = getString(R.string.spinner_prompt)
    }

    private fun setupButtons() {
        binding.depositButton.setOnClickListener {
            val selectedPosition = binding.denominationSpinner.selectedItemPosition
            val amountText = binding.quantityInput.text?.toString()?.trim()
            val quantity = amountText?.toIntOrNull()

            if (quantity == null || quantity < 0) {
                binding.feedbackMessage.text = getString(R.string.feedback_invalid_amount)
                return@setOnClickListener
            }

            counts[selectedPosition] += quantity
            val denominationLabel = "${denominations[selectedPosition]}s"
            binding.feedbackMessage.text = getString(
                R.string.feedback_success,
                quantity,
                selectedPosition + 1,
                denominationLabel
            )
            binding.quantityInput.text?.clear()
            updateDisplay()
        }

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
