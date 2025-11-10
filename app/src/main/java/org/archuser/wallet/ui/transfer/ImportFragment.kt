package org.archuser.wallet.ui.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.archuser.wallet.R
import org.archuser.wallet.databinding.FragmentImportBinding
import org.archuser.wallet.ui.shared.WalletViewModel

class ImportFragment : Fragment() {

    private var _binding: FragmentImportBinding? = null
    private val binding get() = _binding!!
    private val walletViewModel: WalletViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.importFormatExample.text = walletViewModel.exportData()

        binding.importButton.setOnClickListener {
            val input = binding.importInputEditText.text?.toString()?.trim().orEmpty()
            if (input.isEmpty()) {
                binding.importStatus.setText(R.string.import_error_empty)
                return@setOnClickListener
            }

            val success = walletViewModel.importData(input)
            if (success) {
                binding.importStatus.text = getString(
                    R.string.import_success,
                    walletViewModel.totalAmount()
                )
                binding.importInputEditText.text = null
            } else {
                binding.importStatus.setText(R.string.import_error_invalid)
            }
        }

        walletViewModel.counts.observe(viewLifecycleOwner) {
            binding.importFormatExample.text = walletViewModel.exportData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
