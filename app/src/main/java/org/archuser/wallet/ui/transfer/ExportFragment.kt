package org.archuser.wallet.ui.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.archuser.wallet.WalletApplication
import org.archuser.wallet.R
import org.archuser.wallet.databinding.FragmentExportBinding
import org.archuser.wallet.ui.shared.WalletViewModel
import org.archuser.wallet.ui.shared.WalletViewModelFactory

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!
    private val walletViewModel: WalletViewModel by activityViewModels {
        val application = requireActivity().application as WalletApplication
        WalletViewModelFactory(application.walletRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialExport = walletViewModel.exportData()
        binding.exportData.text = initialExport
        binding.exportSummary.text = getString(
            R.string.export_total,
            walletViewModel.totalAmount()
        )

        binding.copyExportButton.setOnClickListener {
            val exportText = binding.exportData.text?.toString().orEmpty()
            if (exportText.isEmpty()) {
                return@setOnClickListener
            }
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.export_clipboard_label), exportText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.export_copied, Toast.LENGTH_SHORT).show()
        }

        walletViewModel.counts.observe(viewLifecycleOwner) { counts ->
            val exportPayload = walletViewModel.exportData()
            binding.exportData.text = exportPayload
            binding.exportSummary.text = getString(
                R.string.export_total,
                walletViewModel.totalAmount(counts)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
