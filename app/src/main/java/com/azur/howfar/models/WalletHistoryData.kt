package com.azur.howfar.models

data class WalletHistoryData(
    var myUid: String = "",
    var otherUid: String = "",
    var amount: String = "",
    var time: String = "",
    var reference: String = "",
    var description: String = "",
    var toAccountName: String = "",
    var toAccountBank: String = "",
    var bankWallet: Int = TranBank.WALLET,
    var direction: Int = TranDirection.SENT,
)

object TranDirection {
    const val RECEIVED = 0
    const val SENT = 1
}

object TranBank {
    const val WALLET = 0
    const val BANK = 1
}