package com.wavesplatform.wallet.v2.ui.auth.choose_account

import com.wavesplatform.wallet.v2.data.model.userdb.AddressBookUser

interface ChooseAccountOnClickListener {
    fun onEditClicked(position: Int)
    fun onDeleteClicked(position: Int)
    fun onItemClicked(item: AddressBookUser)
}
