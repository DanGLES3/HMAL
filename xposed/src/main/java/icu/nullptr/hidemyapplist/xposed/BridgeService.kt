package icu.nullptr.hidemyapplist.xposed

import android.content.pm.IPackageManager
import android.os.Binder
import android.os.Parcel
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import icu.nullptr.hidemyapplist.common.BuildConfig
import icu.nullptr.hidemyapplist.common.Constants

object BridgeService {

    private const val TAG = "HMA-Bridge"

    private var appUid = 0

    fun register(pms: IPackageManager) {
        val service = HMAService(pms)
        appUid = Utils.getPackageUidCompat(service.pms, Constants.APP_PACKAGE_NAME, 0, 0)
        val appPackage = Utils.getPackageInfoCompat(service.pms, Constants.APP_PACKAGE_NAME, 0, 0)
        if (!Utils.verifyAppSignature(appPackage.applicationInfo.sourceDir)) {
            return
        }
        pms.javaClass.findMethod(true) {
            name == "onTransact"
        }.hookBefore { param ->
            val code = param.args[0] as Int
            val data = param.args[1] as Parcel
            val reply = param.args[2] as Parcel?
            if (myTransact(code, data, reply)) param.result = true
        }
    }

    private fun myTransact(code: Int, data: Parcel, reply: Parcel?): Boolean {
        if (code == Constants.TRANSACTION) {
            if (Binder.getCallingUid() == appUid) {
                runCatching {
                    data.enforceInterface(Constants.DESCRIPTOR)
                    when (data.readInt()) {
                        Constants.ACTION_GET_BINDER -> {
                            reply?.writeNoException()
                            reply?.writeStrongBinder(HMAService.instance)
                            return true
                        }
                        else -> { /* Do nothing */ }
                    }
                }.onFailure {
                }
            } else {
            }
            data.setDataPosition(0)
            reply?.setDataPosition(0)
        }
        return false
    }
}
