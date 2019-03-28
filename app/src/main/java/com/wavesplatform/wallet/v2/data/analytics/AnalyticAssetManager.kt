package com.wavesplatform.wallet.v2.data.analytics

import com.wavesplatform.wallet.v1.util.PrefsUtil
import com.wavesplatform.wallet.v2.data.model.remote.response.AssetBalance
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticAssetManager @Inject constructor(private var prefUtil: PrefsUtil) {

    private var assetsIds: MutableSet<String> = mutableSetOf()
        get() {
            return prefUtil.getValue(PrefsUtil.KEY_ASSETS_ALL)
        }
        set(value) {
            field = value
            prefUtil.setValue(PrefsUtil.KEY_ASSETS_ALL, value)
        }


    private var zeroAssetsIds: MutableSet<String> = mutableSetOf()
        get() {
            return prefUtil.getValue(PrefsUtil.KEY_ASSETS_ZERO)
        }
        set(value) {
            field = value
            prefUtil.setValue(PrefsUtil.KEY_ASSETS_ZERO, value)
        }

    fun trackFromZeroBalances(assets: MutableList<AssetBalance>) {
        val zeroAssets = assets.filter { it.balance == 0L }

        saveZeroBalance(zeroAssets)

        val assetBalances = assets.filter { it.balance ?: 0 > 0L }

        val zeroBalances = zeroAssetsIds
        val allBalances = assetsIds

        assetBalances.forEach { asset ->
            if (zeroBalances.contains(asset.assetId) && !allBalances.contains(asset.assetId)) {
                allBalances.add(asset.assetId)
                analytics.trackEvent(AnalyticEvents.WalletStartBalanceFromZeroEvent(asset.getName()))
            }
        }

        assetsIds = allBalances
    }

    private fun saveZeroBalance(assetId: List<AssetBalance>) {
        val zeroBalances = zeroAssetsIds
        assetId.forEach { asset ->
            if (!zeroBalances.contains(asset.assetId)) {
                zeroBalances.add(asset.assetId)
            }
        }
        zeroAssetsIds = zeroBalances
    }
}
