/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.ui.home.profile.addresses.alias

import com.wavesplatform.sdk.net.model.response.Alias
import com.wavesplatform.wallet.v2.ui.base.view.BaseMvpView

interface AliasView : BaseMvpView {
    fun afterSuccessLoadAliases(ownAliases: List<Alias>)
}
