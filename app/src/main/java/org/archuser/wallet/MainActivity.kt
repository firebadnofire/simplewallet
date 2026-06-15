package org.archuser.wallet

import android.os.Bundle
import android.util.TypedValue
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.navigation.NavigationView
import org.archuser.wallet.data.WalletRecord
import org.archuser.wallet.databinding.ActivityMainBinding
import org.archuser.wallet.ui.shared.WalletViewModel
import org.archuser.wallet.ui.shared.WalletViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val walletViewModel: WalletViewModel by viewModels {
        val application = application as WalletApplication
        WalletViewModelFactory(application.walletRepository)
    }

    private val walletMenuIds = mutableMapOf<Int, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_import, R.id.nav_export),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_import,
                R.id.nav_export,
                R.id.nav_home -> {
                    navigateTo(item.itemId)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                MENU_ID_NEW_WALLET -> {
                    showCreateWalletDialog()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                else -> {
                    val walletId = walletMenuIds[item.itemId] ?: return@setNavigationItemSelectedListener false
                    walletViewModel.selectWallet(walletId)
                    navigateTo(R.id.nav_home)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            syncCheckedItem(destination.id)
        }

        walletViewModel.wallets.observe(this) { wallets ->
            rebuildWalletMenu(wallets)
        }
        walletViewModel.selectedWalletId.observe(this) {
            syncCheckedItem(navController.currentDestination?.id ?: R.id.nav_home)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun rebuildWalletMenu(wallets: List<WalletRecord>) {
        val menu = binding.navView.menu
        val walletGroup = R.id.group_wallets

        menu.removeGroup(walletGroup)

        walletMenuIds.clear()
        wallets.forEachIndexed { index, wallet ->
            val itemId = MENU_ID_WALLET_BASE + index
            walletMenuIds[itemId] = wallet.id
            menu.add(walletGroup, itemId, index, wallet.name)
                .setIcon(R.drawable.ic_nav_home)
                .isCheckable = true
        }

        menu.removeItem(MENU_ID_NEW_WALLET)
        menu.add(walletGroup, MENU_ID_NEW_WALLET, wallets.size, getString(R.string.menu_new_wallet))
            .setIcon(R.drawable.ic_nav_home)
            .isCheckable = false

        syncCheckedItem(navController.currentDestination?.id ?: R.id.nav_home)
    }

    private fun syncCheckedItem(destinationId: Int) {
        val menu = binding.navView.menu
        menu.setGroupCheckable(R.id.group_wallets, true, true)
        menu.setGroupCheckable(R.id.group_actions, true, true)

        if (destinationId == R.id.nav_import || destinationId == R.id.nav_export) {
            walletMenuIds.keys.forEach { menu.findItem(it)?.isChecked = false }
            menu.findItem(destinationId)?.isChecked = true
            return
        }

        menu.findItem(R.id.nav_import)?.isChecked = false
        menu.findItem(R.id.nav_export)?.isChecked = false
        val selectedWalletId = walletViewModel.selectedWalletId.value
        val selectedMenuId = walletMenuIds.entries.firstOrNull { it.value == selectedWalletId }?.key
        if (selectedMenuId != null) {
            menu.findItem(selectedMenuId)?.isChecked = true
        }
    }

    private fun showCreateWalletDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.new_wallet_name_hint)
            setSingleLine()
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics
            ).toInt()
            setPadding(padding, padding, padding, padding)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.new_wallet_title)
            .setView(input)
            .setPositiveButton(R.string.new_wallet_confirm) { _, _ ->
                walletViewModel.createWallet(input.text?.toString().orEmpty())
                navigateTo(R.id.nav_home)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun navigateTo(destinationId: Int) {
        if (navController.currentDestination?.id != destinationId) {
            navController.navigate(destinationId)
        } else {
            syncCheckedItem(destinationId)
        }
    }

    companion object {
        private const val MENU_ID_WALLET_BASE = 10_000
        private const val MENU_ID_NEW_WALLET = 20_000
    }
}
