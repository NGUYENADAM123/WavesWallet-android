package com.wavesplatform.wallet.v2.data.manager

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import com.wavesplatform.wallet.App
import com.wavesplatform.wallet.v1.crypto.AESUtil
import com.wavesplatform.wallet.v1.data.auth.WavesWallet
import com.wavesplatform.wallet.v1.data.rxjava.RxUtil
import com.wavesplatform.wallet.v1.data.services.PinStoreService
import com.wavesplatform.wallet.v1.util.AddressUtil
import com.wavesplatform.wallet.v1.util.AppUtil
import com.wavesplatform.wallet.v1.util.PrefsUtil
import com.wavesplatform.wallet.v2.data.helpers.AuthHelper
import com.wavesplatform.wallet.v2.ui.home.profile.address_book.AddressBookUser
import com.wavesplatform.wallet.v2.ui.splash.SplashActivity
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.exceptions.Exceptions
import org.apache.commons.io.Charsets
import org.spongycastle.util.encoders.Hex
import java.security.SecureRandom
import java.util.*


class AccessManager(private var prefs: PrefsUtil, private var appUtil: AppUtil, private var authHelper: AuthHelper) {

    private val pinStore = PinStoreService()
    private var loggedInGuid: String = ""
    private var wallet: WavesWallet? = null
    private var activityCounter = 0

    fun validatePassCodeObservable(guid: String, passCode: String): Observable<String> {
        return readPassCodeObservable(
                guid, passCode, App.getAccessManager().getPassCodeInputFails())
                .flatMap { password ->
                    writePassCodeObservable(guid, password, passCode)
                            .andThen(Observable.just<String>(password))
                }
                .compose(RxUtil.applySchedulersToObservable<String>())
    }

    private fun readPassCodeObservable(guid: String, passedPin: String, tryCount: Int): Observable<String> {
        return pinStore.readPassword(guid, passedPin, tryCount)
                .map { seed ->
                    try {
                        val encryptedPassword = prefs.getValue(
                                guid, PrefsUtil.KEY_ENCRYPTED_PASSWORD, "")
                        AESUtil.decrypt(
                                encryptedPassword, seed, AESUtil.PIN_PBKDF2_ITERATIONS)
                    } catch (e: Exception) {
                        throw Exceptions.propagate(Throwable("Decrypt wallet failed"))
                    }
                }
    }

    fun writePassCodeObservable(guid: String, password: String, passCode: String): Completable {
        appUtil.applyPRNGFixes()

        return Completable.create { subscriber ->
            if (passCode.length != 4) {
                subscriber.onError(RuntimeException("Prohibited pin"))
            }

            try {
                val seed = randomString()
                pinStore.writePassword(guid, passCode, seed)
                        .subscribe({ _ ->
                            val encryptedPassword = AESUtil.encrypt(
                                    password, seed, AESUtil.PIN_PBKDF2_ITERATIONS)
                            prefs.setValue(guid, PrefsUtil.KEY_ENCRYPTED_PASSWORD, encryptedPassword)
                            if (!subscriber.isDisposed) {
                                subscriber.onComplete()
                            }
                        }, { err ->
                            if (!subscriber.isDisposed) {
                                subscriber.onError(err)
                            }
                        })
            } catch (e: Exception) {
                Log.e(javaClass.simpleName, "createPinObservable", e)
                if (!subscriber.isDisposed) {
                    subscriber.onError(RuntimeException("Failed to encrypt password"))
                }
            }
        }.compose(RxUtil.applySchedulersToCompletable())
    }

    private fun randomString(): String {
        val bytes = ByteArray(16)
        val random = SecureRandom()
        random.nextBytes(bytes)
        return String(Hex.encode(bytes), charset("UTF-8"))
    }

    fun storeWalletData(seed: String, password: String, walletName: String, skipBackup: Boolean): String {
        try {
            wallet = WavesWallet(seed.toByteArray(Charsets.UTF_8))
            val guid = UUID.randomUUID().toString()
            loggedInGuid = guid
            prefs.setGlobalValue(PrefsUtil.GLOBAL_LAST_LOGGED_IN_GUID, guid)
            prefs.addGlobalListValue(PrefsUtil.LIST_WALLET_GUIDS, guid)
            prefs.setValue(PrefsUtil.KEY_PUB_KEY, wallet!!.publicKeyStr)
            prefs.setValue(PrefsUtil.KEY_WALLET_NAME, walletName)
            prefs.setValue(PrefsUtil.KEY_ENCRYPTED_WALLET, wallet!!.getEncryptedData(password))
            authHelper.configureDB(wallet?.address)
            if (skipBackup) {
                prefs.setValue(PrefsUtil.KEY_SKIP_BACKUP, true)
            }
            return guid
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "storeWalletData: ", e)
            return ""
        }
    }

    fun storeWalletName(address: String, name: String) {
        if (!TextUtils.isEmpty(address) && !TextUtils.isEmpty(name)) {
            val searchWalletGuid = findGuidBy(address)
            prefs.setGlobalValue(searchWalletGuid + PrefsUtil.KEY_WALLET_NAME, name)
        }
    }

    fun findGuidBy(address: String): String {
        val guids = prefs.getGlobalValueList(PrefsUtil.LIST_WALLET_GUIDS)
        var resultGuid = ""
        for (guid in guids) {
            val publicKey = prefs.getValue(guid, PrefsUtil.KEY_PUB_KEY, "")
            if (AddressUtil.addressFromPublicKey(publicKey) == address) {
                resultGuid = guid
            }
        }
        return resultGuid
    }

    fun isAccountNameExist(checkedName: String): Boolean {
        if (TextUtils.isEmpty(checkedName)) {
            return true
        }

        val guids = prefs.getGlobalValueList(PrefsUtil.LIST_WALLET_GUIDS)
        for (guid in guids) {
            if (checkedName == prefs.getValue(guid, PrefsUtil.KEY_WALLET_NAME, "")) {
                return true
            }
        }
        return false
    }

    fun getCurrentWavesWalletEncryptedData(): String {
        return getWalletData(loggedInGuid)
    }

    fun getLoggedInGuid(): String {
        return loggedInGuid
    }

    fun getLastLoggedInGuid(): String {
        return prefs.guid
    }

    fun setLastLoggedInGuid(guid: String) {
        prefs.setGlobalValue(PrefsUtil.GLOBAL_LAST_LOGGED_IN_GUID, guid)
        loggedInGuid = guid
    }

    fun resetWallet() {
        wallet = null
        loggedInGuid = ""
    }

    fun setWallet(guid: String, password: String) {
        wallet = WavesWallet(getWalletData(guid), password)
        setLastLoggedInGuid(guid)
        authHelper.configureDB(wallet?.address)
    }

    fun getWallet(): WavesWallet? {
        return wallet
    }

    fun createAddressBookCurrentAccount(): AddressBookUser? {
        if (TextUtils.isEmpty(loggedInGuid)) {
            return null
        }

        val name = prefs.getGlobalValue(loggedInGuid + PrefsUtil.KEY_WALLET_NAME, "")
        val publicKey = prefs.getGlobalValue(loggedInGuid + PrefsUtil.KEY_PUB_KEY, "")

        return if (TextUtils.isEmpty(publicKey) || TextUtils.isEmpty(name)) {
            null
        } else AddressBookUser(AddressUtil.addressFromPublicKey(publicKey), name)

    }

    fun deleteCurrentWavesWallet(): Boolean {
        val currentUser = createAddressBookCurrentAccount()
        return if (currentUser == null) {
            false
        } else {
            deleteWavesWallet(currentUser.address)
            true
        }
    }


    fun deleteWavesWallet(address: String) {
        val searchWalletGuid = App.getAccessManager().findGuidBy(address)

        prefs.removeValue(searchWalletGuid, PrefsUtil.KEY_PUB_KEY)
        prefs.removeValue(searchWalletGuid, PrefsUtil.KEY_WALLET_NAME)
        prefs.removeValue(searchWalletGuid, PrefsUtil.KEY_ENCRYPTED_WALLET)

        prefs.setGlobalValue(PrefsUtil.LIST_WALLET_GUIDS, createGuidsListWithout(searchWalletGuid))

        if (searchWalletGuid == getLoggedInGuid()) {
            loggedInGuid = ""
            prefs.removeGlobalValue(PrefsUtil.GLOBAL_LAST_LOGGED_IN_GUID)
        }
    }

    private fun createGuidsListWithout(guidToRemove: String): Array<String> {
        val guids = prefs.getGlobalValueList(PrefsUtil.LIST_WALLET_GUIDS)
        val resultGuidsList = ArrayList<String>()
        for (guid in guids) {
            if (guid != guidToRemove) {
                resultGuidsList.add(guid)
            }
        }
        return resultGuidsList.toTypedArray()
    }


    fun getWalletData(guid: String): String {
        return if (TextUtils.isEmpty(guid)) {
            ""
        } else {
            prefs.getGlobalValue(guid + PrefsUtil.KEY_ENCRYPTED_WALLET, "")
        }
    }

    fun getWalletName(guid: String): String {
        return if (TextUtils.isEmpty(guid)) {
            ""
        } else {
            prefs.getGlobalValue(guid + PrefsUtil.KEY_WALLET_NAME, "")
        }
    }

    fun getWalletAddress(guid: String): String {
        if (TextUtils.isEmpty(guid)) {
            return ""
        }
        val publicKey = prefs.getValue(guid, PrefsUtil.KEY_PUB_KEY, "")
        return AddressUtil.addressFromPublicKey(publicKey)
    }

    fun findPublicKeyBy(address: String): String {
        if (TextUtils.isEmpty(address)) {
            return ""
        }
        return prefs.getValue(App.getAccessManager().findGuidBy(address),
                PrefsUtil.KEY_PUB_KEY, "")
    }

    fun storePassword(guid: String, publicKeyStr: String, encryptedPassword: String) {
        prefs.setGlobalValue(PrefsUtil.GLOBAL_LAST_LOGGED_IN_GUID, guid)
        prefs.setValue(PrefsUtil.KEY_PUB_KEY, publicKeyStr)
        prefs.setValue(PrefsUtil.KEY_ENCRYPTED_WALLET, encryptedPassword)
    }

    fun incrementPassCodeInputFails() {
        var fails = prefs.getValue(PrefsUtil.KEY_PIN_FAILS, 0)
        prefs.setValue(PrefsUtil.KEY_PIN_FAILS, ++fails)
    }

    fun getPassCodeInputFails(): Int {
        return prefs.getValue(PrefsUtil.KEY_PIN_FAILS, 0)
    }

    fun resetPassCodeInputFails() {
        prefs.removeValue(PrefsUtil.KEY_PIN_FAILS)
    }

    fun setCurrentAccountBackupDone() {
        prefs.setValue(PrefsUtil.KEY_SKIP_BACKUP, false)
    }

    fun isCurrentAccountBackupSkipped(): Boolean {
        return prefs.getValue(PrefsUtil.KEY_SKIP_BACKUP, true)
    }

    fun setUseFingerPrint(use: Boolean) {
        prefs.setValue(PrefsUtil.KEY_USE_FINGERPRINT, use)
    }

    fun isGuidUseFingerPrint(guid: String): Boolean {
        return prefs.getGuidValue(guid, PrefsUtil.KEY_USE_FINGERPRINT, false)
    }

    fun isUseFingerPrint(): Boolean {
        return prefs.getValue(PrefsUtil.KEY_USE_FINGERPRINT, false)
    }

    fun setEncryptedPassCode(guid: String, data: String) {
        prefs.setValue(guid, PrefsUtil.KEY_ENCRYPTED_PIN, data)
    }

    fun getEncryptedPassCode(guid: String): String {
        return prefs.getValue(guid, PrefsUtil.KEY_ENCRYPTED_PIN, "")
    }

    fun restartApp(context: Context) {
        val intent = Intent(context, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun addActivity() {
        activityCounter++
    }

    fun removeActivity() {
        activityCounter--
        if (wallet != null) {
            val timer = Timer()
            val timerTask = object : TimerTask() {
                override fun run() {
                    if (activityCounter == 0) {
                        resetWallet()
                    }
                }
            }
            timer.schedule(timerTask, SESSION_LIFE_AFTER_MINIMIZE)
        }
    }

    companion object {
        const val SESSION_LIFE_AFTER_MINIMIZE = 5000L
    }
}