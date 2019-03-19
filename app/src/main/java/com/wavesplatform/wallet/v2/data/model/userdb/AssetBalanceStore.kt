package com.wavesplatform.wallet.v2.data.model.userdb

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.vicpin.krealmextensions.saveAll
import com.wavesplatform.sdk.net.model.response.AssetBalance
import io.realm.RealmModel
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import kotlinx.android.parcel.Parcelize

@Parcelize
@RealmClass
open class AssetBalanceStore(
        @PrimaryKey
        @SerializedName("assetId") var assetId: String = "",
        @SerializedName("isHidden") var isHidden: Boolean = false,
        @SerializedName("position") var position: Int = -1,
        @SerializedName("isFavorite") var isFavorite: Boolean = false) : RealmModel, Parcelable {


    companion object {

        fun saveAssetBalanceStore(balances: List<AssetBalance>) {
            val list = mutableListOf<AssetBalanceStore>()
            for (assetBalance in balances) {
                list.add(AssetBalanceStore(
                        assetBalance.assetId,
                        assetBalance.isHidden,
                        assetBalance.position,
                        assetBalance.isFavorite))
            }
            list.saveAll()
        }
    }
}