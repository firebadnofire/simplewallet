package org.archuser.wallet

import android.app.Application
import org.archuser.wallet.data.WalletRepository

class WalletApplication : Application() {
    val walletRepository: WalletRepository by lazy { WalletRepository(this) }
}
