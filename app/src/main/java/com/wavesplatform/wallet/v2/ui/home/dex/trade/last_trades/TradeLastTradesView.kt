/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.ui.home.dex.trade.last_trades

import com.wavesplatform.sdk.net.model.response.LastTradesResponse
import com.wavesplatform.wallet.v2.ui.base.view.BaseMvpView

interface TradeLastTradesView : BaseMvpView {
    fun afterSuccessLoadLastTrades(data: List<LastTradesResponse.Data.ExchangeTransaction>)
    fun afterFailedLoadLastTrades()
}
