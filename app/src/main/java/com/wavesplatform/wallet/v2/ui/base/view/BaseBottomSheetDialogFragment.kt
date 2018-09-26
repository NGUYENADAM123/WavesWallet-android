package com.wavesplatform.wallet.v2.ui.base.view

import android.app.Dialog
import android.os.Bundle
import android.support.annotation.NonNull
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.BottomSheetDialogFragment
import android.widget.FrameLayout
import com.wavesplatform.wallet.v2.util.getToolBarHeight
import com.wavesplatform.wallet.v2.util.notNull
import pers.victor.ext.getStatusBarHeight
import pers.victor.ext.screenHeight
import pers.victor.ext.setHeight

open class BaseBottomSheetDialogFragment : BottomSheetDialogFragment(), BaseMvpView {
    override fun showNetworkError() {
        baseActivity.showNetworkError()
    }

    override fun showProgressBar(isShowProgress: Boolean) {
        baseActivity.showProgressBar(isShowProgress)
    }

    var fullScreenHeightEnable = false
    var extraTopMargin = 0
    val baseActivity: BaseActivity
        get() = activity as BaseActivity

    @NonNull
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog

            val bottomSheet = d.findViewById<FrameLayout>(android.support.design.R.id.design_bottom_sheet)
            bottomSheet.notNull {
                if ((it.height > screenHeight - getStatusBarHeight() - it.context.getToolBarHeight()) || fullScreenHeightEnable) {
                    it.setHeight(screenHeight - getStatusBarHeight() - it.context.getToolBarHeight() - extraTopMargin)
                }
                var bottomSheetBehavior = BottomSheetBehavior.from(it)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                bottomSheetBehavior.peekHeight = it.height

            }
        }

        // Do something with your dialog like setContentView() or whatever
        return dialog
    }
}