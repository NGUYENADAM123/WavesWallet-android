package com.wavesplatform.wallet.v2.ui.home.dex.trade.buy_and_sell.buy

import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.app.AlertDialog
import android.widget.Button
import android.widget.TextView
import com.arellomobile.mvp.presenter.InjectPresenter
import com.arellomobile.mvp.presenter.ProvidePresenter
import com.wavesplatform.wallet.R
import com.wavesplatform.wallet.v2.ui.base.view.BaseFragment
import kotlinx.android.synthetic.main.fragment_trade_buy.*
import pers.victor.ext.click
import pers.victor.ext.findColor
import javax.inject.Inject


class TradeBuyFragment : BaseFragment(), TradeBuyView {

    @Inject
    @InjectPresenter
    lateinit var presenter: TradeBuyPresenter
    var buttonPositive: Button? = null

    @ProvidePresenter
    fun providePresenter(): TradeBuyPresenter = presenter

    override fun configLayoutRes() = R.layout.fragment_trade_buy


    override fun onViewReady(savedInstanceState: Bundle?) {
        text_expiration_value.click {
            val alt_bld = AlertDialog.Builder(baseActivity)
            alt_bld.setTitle(getString(R.string.buy_and_sell_expiration_dialog_title))
            alt_bld.setSingleChoiceItems(presenter.expirationList, presenter.selectedExpiration, { dialog, item ->
                if (presenter.selectedExpiration == item){
                    buttonPositive?.setTextColor(findColor(R.color.basic300))
                    buttonPositive?.isClickable = false
                }else{
                    buttonPositive?.setTextColor(findColor(R.color.submit400))
                    buttonPositive?.isClickable = true
                }
                presenter.newSelectedExpiration = item
            })
            alt_bld.setPositiveButton(getString(R.string.buy_and_sell_expiration_dialog_positive_btn),
                    { dialog, which ->
                        dialog.dismiss()
                        presenter.selectedExpiration = presenter.newSelectedExpiration
                        text_expiration_value.text = presenter.expirationList[presenter.selectedExpiration]
                    })
            alt_bld.setNegativeButton(getString(R.string.buy_and_sell_expiration_dialog_negative_btn),
                    { dialog, which -> dialog.dismiss() })
            val alert = alt_bld.create()
            alert.show()

            val titleTextView = alert?.findViewById<TextView>(R.id.alertTitle)
            buttonPositive = alert?.findViewById<Button>(android.R.id.button1)
            val buttonNegative = alert?.findViewById<Button>(android.R.id.button2)
            buttonPositive?.typeface = ResourcesCompat.getFont(baseActivity, R.font.roboto_medium)
            buttonPositive?.setTextColor(findColor(R.color.basic300))
            buttonPositive?.isClickable = false
            buttonNegative?.typeface = ResourcesCompat.getFont(baseActivity, R.font.roboto_medium)
            titleTextView?.typeface = ResourcesCompat.getFont(baseActivity, R.font.roboto_medium)
        }
    }

}