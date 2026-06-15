package org.archuser.wallet.ui.transfer

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.archuser.wallet.R
import org.archuser.wallet.WalletApplication
import org.archuser.wallet.data.WalletRecord
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

    private val createExportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            saveExport(uri)
        }
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

        binding.saveExportButton.setOnClickListener {
            createExportFileLauncher.launch(getString(R.string.backup_file_name))
        }

        walletViewModel.wallets.observe(viewLifecycleOwner) { wallets ->
            renderExport(wallets)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderExport(wallets: List<WalletRecord>) {
        val exportPayload = walletViewModel.exportAllWallets()
        val totalAmount = wallets.sumOf { walletViewModel.totalAmount(it.counts) }
        binding.exportWalletCount.text = resources.getQuantityString(
            R.plurals.wallet_count,
            wallets.size,
            wallets.size
        )
        binding.exportSummary.text = getString(R.string.export_total, totalAmount)
        binding.exportData.text = exportPayload
    }

    private fun saveExport(uri: Uri) {
        val payload = walletViewModel.exportAllWallets()
        val success = try {
            val outputStream = requireContext().contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open output stream")
            outputStream.bufferedWriter().use {
                it.write(payload)
            }
            true
        } catch (_: Exception) {
            false
        }

        binding.exportStatus.setText(
            if (success) R.string.export_saved else R.string.export_error_write
        )
    }
}
