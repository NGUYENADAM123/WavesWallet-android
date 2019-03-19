package com.wavesplatform.wallet.v2.ui.home.wallet.assets.sorting

import com.arellomobile.mvp.InjectViewState
import com.vicpin.krealmextensions.queryAllAsSingle
import com.vicpin.krealmextensions.save
import com.vicpin.krealmextensions.saveAll
import com.wavesplatform.wallet.v2.data.model.db.AssetBalanceDb
import com.wavesplatform.wallet.v2.data.model.local.AssetSortingItem
import com.wavesplatform.wallet.v2.data.model.userdb.AssetBalanceStore
import com.wavesplatform.wallet.v2.ui.base.presenter.BasePresenter
import com.wavesplatform.sdk.utils.RxUtil
import pyxis.uzuki.live.richutilskt.utils.runAsync
import javax.inject.Inject

@InjectViewState
class AssetsSortingPresenter @Inject constructor() : BasePresenter<AssetsSortingView>() {
    var needToUpdate: Boolean = false
    var visibilityConfigurationActive = false

    fun loadAssets() {
        runAsync {
            addSubscription(queryAllAsSingle<AssetBalanceDb>().toObservable()
                    .compose(RxUtil.applyObservableDefaultSchedulers())
                    .subscribe({
                        val result = mutableListOf<AssetSortingItem>()

                        val favoriteList = it.filter { it.isFavorite }
                                .sortedBy { it.position }
                                .sortedByDescending { it.isFavorite }
                                .mapTo(mutableListOf()) {
                                    AssetSortingItem(AssetSortingItem.TYPE_FAVORITE, it.convertFromDb())
                                }
                        val notFavoriteList = it.filter { !it.isFavorite && !it.isSpam }
                                .sortedBy { it.position }
                                .mapTo(mutableListOf()) {
                                    AssetSortingItem(AssetSortingItem.TYPE_NOT_FAVORITE, it.convertFromDb())
                                }

                        result.addAll(favoriteList)
                        if (favoriteList.isNotEmpty() && notFavoriteList.isNotEmpty()) {
                            result.add(AssetSortingItem(AssetSortingItem.TYPE_LINE))
                        }
                        result.addAll(notFavoriteList)

                        viewState.showAssets(result)
                    }, {
                        it.printStackTrace()
                    }))
        }
    }

    fun saveSortedPositions(data: MutableList<AssetSortingItem>) {
        val list = data
                .filter { it.type != AssetSortingItem.TYPE_LINE }
                .mapIndexedTo(mutableListOf()) { position, item ->
                    item.asset.position = position
                    AssetBalanceStore(item.asset.assetId,
                            item.asset.isHidden,
                            item.asset.position,
                            item.asset.isFavorite).save()
                    return@mapIndexedTo item.asset
                }
        AssetBalanceDb.convertToDb(list).saveAll()
    }
}
