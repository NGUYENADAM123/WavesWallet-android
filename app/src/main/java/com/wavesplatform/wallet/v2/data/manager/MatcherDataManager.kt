/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.data.manager

import com.google.common.primitives.Bytes
import com.google.common.primitives.Longs
import com.google.gson.internal.LinkedTreeMap
import com.vicpin.krealmextensions.queryAllAsSingle
import com.wavesplatform.sdk.utils.Constants
import com.wavesplatform.sdk.crypto.Base58
import com.wavesplatform.sdk.crypto.CryptoProvider
import com.wavesplatform.sdk.net.model.WatchMarket
import com.wavesplatform.sdk.net.model.request.CancelOrderRequest
import com.wavesplatform.sdk.net.model.request.OrderRequest
import com.wavesplatform.sdk.net.model.response.*
import com.wavesplatform.sdk.utils.EnvironmentManager
import com.wavesplatform.sdk.utils.notNull
import com.wavesplatform.wallet.App
import com.wavesplatform.wallet.v2.util.PrefsUtil
import com.wavesplatform.wallet.v2.data.Events
import com.wavesplatform.wallet.v2.data.manager.base.BaseDataManager
import com.wavesplatform.wallet.v2.data.model.userdb.MarketResponseDb
import com.wavesplatform.wallet.v2.data.model.db.SpamAssetDb
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatcherDataManager @Inject constructor() : BaseDataManager() {
    var allMarketsList = mutableListOf<MarketResponse>()

    fun loadReservedBalances(): Observable<Map<String, Long>> {
        val timestamp = EnvironmentManager.getTime()
        var signature = ""
        App.getAccessManager().getWallet()?.privateKey.notNull { privateKey ->
            val bytes = Bytes.concat(Base58.decode(getPublicKeyStr()),
                    Longs.toByteArray(timestamp))
            signature = Base58.encode(CryptoProvider.sign(privateKey, bytes))
        }
        return matcherService.loadReservedBalances(getPublicKeyStr(), timestamp, signature)
    }

    fun loadMyOrders(watchMarket: WatchMarket?): Observable<List<OrderResponse>> {
        val timestamp = EnvironmentManager.getTime()
        var signature = ""
        App.getAccessManager().getWallet()?.privateKey.notNull { privateKey ->
            val bytes = Bytes.concat(Base58.decode(getPublicKeyStr()),
                    Longs.toByteArray(timestamp))
            signature = Base58.encode(CryptoProvider.sign(privateKey, bytes))
        }
        return matcherService.getMyOrders(watchMarket?.market?.amountAsset, watchMarket?.market?.priceAsset, getPublicKeyStr(), signature, timestamp)
    }

    fun loadOrderBook(watchMarket: WatchMarket?): Observable<OrderBook> {
        return matcherService.getOrderBook(watchMarket?.market?.amountAsset, watchMarket?.market?.priceAsset)
    }

    fun cancelOrder(orderId: String?, amountAsset: String?, priceAsset: String?): Observable<Any> {
        val request = CancelOrderRequest()
        request.sender = getPublicKeyStr()
        request.orderId = orderId ?: ""
        App.getAccessManager().getWallet().privateKey.notNull {
            request.sign(it)
        }
        return matcherService.cancelOrder(amountAsset, priceAsset, request)
                .doOnNext {
                    rxEventBus.post(Events.UpdateAssetsBalance())
                }
    }

    fun getBalanceFromAssetPair(watchMarket: WatchMarket?): Observable<LinkedTreeMap<String, Long>> {
        return matcherService.getBalanceFromAssetPair(watchMarket?.market?.amountAsset, watchMarket?.market?.priceAsset, getAddress())
    }

    fun getMatcherKey(): Observable<String> {
        return matcherService.getMatcherKey()
    }

    fun placeOrder(orderRequest: OrderRequest): Observable<Any> {
        orderRequest.senderPublicKey = getPublicKeyStr()
        App.getAccessManager().getWallet()?.privateKey.notNull { privateKey ->
            orderRequest.sign(privateKey)
        }
        return matcherService.placeOrder(orderRequest)
                .doOnNext {
                    rxEventBus.post(Events.UpdateAssetsBalance())
                }
    }

    fun getAllMarkets(): Observable<MutableList<MarketResponse>> {
        if (allMarketsList.isEmpty()) {
            return Observable.zip(Observable.just(EnvironmentManager.globalConfiguration)
                    .map {
                        val globalAssets = it.generalAssets.toMutableList()
                        globalAssets.add(Constants.MRTGeneralAsset)
                        globalAssets.add(Constants.WCTGeneralAsset) // todo check const
                        return@map globalAssets.associateBy { it.assetId }
                    },
                    matcherService.getAllMarkets()
                            .map { it.markets },
                    BiFunction { configure: Map<String, GlobalConfiguration.ConfigAsset>, apiMarkets: List<MarketResponse> ->
                        return@BiFunction Pair(configure, apiMarkets)
                    })
                    .flatMap {
                        // configure hash groups
                        val hashGroup = linkedMapOf<String, MutableList<MarketResponse>>()

                        it.first.keys.forEach {
                            hashGroup[it] = mutableListOf()
                        }
                        hashGroup[OTHER_GROUP] = mutableListOf()

                        // fill information and sort by group
                        it.second.forEach { market ->
                            market.id = market.amountAsset + market.priceAsset

                            market.amountAssetLongName = it.first[market.amountAsset]?.displayName
                                    ?: market.amountAssetName
                            market.priceAssetLongName = it.first[market.priceAsset]?.displayName
                                    ?: market.priceAssetName

                            market.amountAssetShortName = it.first[market.amountAsset]?.gatewayId
                                    ?: market.amountAssetName
                            market.priceAssetShortName = it.first[market.priceAsset]?.gatewayId
                                    ?: market.priceAssetName

                            market.popular = it.first[market.amountAsset] != null && it.first[market.priceAsset] != null

                            market.amountAssetDecimals = if (market.amountAssetInfo == null) {
                                8
                            } else {
                                market.amountAssetInfo.decimals
                            }
                            market.priceAssetDecimals = if (market.priceAssetInfo == null) {
                                8
                            } else {
                                market.priceAssetInfo.decimals
                            }

                            val group = hashGroup[market.amountAsset]
                            if (group != null) {
                                group.add(market)
                            } else {
                                hashGroup[OTHER_GROUP]?.add(market)
                            }
                        }

                        hashGroup.values.forEach {
                            allMarketsList.addAll(it)
                        }

                        return@flatMap filterMarketsBySpamAndSelect(allMarketsList)
                    }
        } else {
            return filterMarketsBySpamAndSelect(allMarketsList)
        }
    }

    private fun filterMarketsBySpamAndSelect(markets: List<MarketResponse>): Observable<MutableList<MarketResponse>> {
        return Observable.zip(Observable.just(markets), queryAllAsSingle<SpamAssetDb>().toObservable()
                .map {
                    return@map SpamAssetDb.convertFromDb(it).associateBy { it.assetId }
                },
                queryAllAsSingle<MarketResponseDb>().toObservable()
                        .map {
                            return@map MarketResponseDb.convertFromDb(it).associateBy { it.id }
                        }
                , Function3 { apiMarkets: List<MarketResponse>, spamAssets: Map<String?, SpamAsset>, dbMarkets: Map<String?, MarketResponse> ->
            val filteredSpamList = if (prefsUtil.getValue(PrefsUtil.KEY_ENABLE_SPAM_FILTER, true)) {
                apiMarkets.filter { market -> spamAssets[market.amountAsset] == null && spamAssets[market.priceAsset] == null }
            } else {
                apiMarkets.toMutableList()
            }.toMutableList()

            filteredSpamList.forEach { market ->
                market.checked = dbMarkets[market.id] != null
            }

            return@Function3 filteredSpamList
        })
    }

    companion object {
        var OTHER_GROUP = "other"
    }
}
