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
import org.archuser.wallet.databinding.FragmentImportBinding
import org.archuser.wallet.ui.shared.WalletViewModel
import org.archuser.wallet.ui.shared.WalletViewModelFactory

class ImportFragment : Fragment() {

    private var _binding: FragmentImportBinding? = null
    private val binding get() = _binding!!
    private val walletViewModel: WalletViewModel by activityViewModels {
        val application = requireActivity().application as WalletApplication
        WalletViewModelFactory(application.walletRepository)
    }

    private val importWalletsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importWallets(uri)
        }
    }

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

        binding.importFormatExample.text = walletViewModel.exportAllWallets()
        binding.importButton.setOnClickListener {
            importWalletsLauncher.launch(arrayOf("application/json", "text/plain"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun importWallets(uri: Uri) {
        val rawJson = try {
            requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }

        if (rawJson.isNullOrBlank()) {
            binding.importStatus.setText(R.string.import_error_read)
            return
        }

        val success = walletViewModel.importAllWallets(rawJson)
        if (success) {
            val walletCount = walletViewModel.wallets.value?.size ?: 0
            binding.importStatus.text = getString(R.string.import_success, walletCount)
            binding.importFormatExample.text = walletViewModel.exportAllWallets()
        } else {
            binding.importStatus.setText(R.string.import_error_invalid)
        }
    }
}
