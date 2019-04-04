/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.ui.home.profile.address_book.add

import com.arellomobile.mvp.InjectViewState
import com.vicpin.krealmextensions.save
import com.wavesplatform.wallet.v2.data.model.userdb.AddressBookUser
import com.wavesplatform.wallet.v2.ui.base.presenter.BasePresenter
import javax.inject.Inject

@InjectViewState
class AddAddressPresenter @Inject constructor() : BasePresenter<AddAddressView>() {
    var nameFieldValid = false
    var addressFieldValid = false

    fun isAllFieldsValid(): Boolean {
        return nameFieldValid && addressFieldValid
    }

    fun saveAddress(address: String, name: String) {
        val addressBookUser = AddressBookUser(address, name)
        addressBookUser.save()
        viewState.successSaveAddress(addressBookUser)
    }
}
