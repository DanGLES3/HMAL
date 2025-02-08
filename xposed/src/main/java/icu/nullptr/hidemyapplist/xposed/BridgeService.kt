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
        // Hook the onTransact method of IPackageManager
        pms.javaClass.findMethod(true) {
            name == "onTransact"
        }.hookBefore { param ->
            val code = param.args[0] as Int
            val data = param.args[1] as Parcel
            val reply = param.args[2] as Parcel?
            if (myTransact(code, data, reply)) {
                param.result = true
            }
        }
    }

    private fun myTransact(code: Int, data: Parcel, reply: Parcel?): Boolean {
        if (code == Constants.TRANSACTION) {
            // First, verify that the calling package is our expected module.
            // We wrap this in a try/catch to avoid leaking any error via side channels.
            try {
                // Note: Binder.getCallingPackage() is available on API 29+.
                // If targeting earlier versions you might need an alternative approach
                // (for example, querying the package manager with Binder.getCallingUid()).
                val callingPackage = Binder.getCallingPackage()
                if (callingPackage != Constants.APP_PACKAGE_NAME) {
                    // The caller is not our module – do nothing.
                    return false
                }
            } catch (e: Exception) {
                // Any exception (for example, if a malicious caller attempts to spoof the package)
                // causes us to silently reject the request.
                return false
            }

            // Additionally, check that the calling UID is the one we expect.
            if (Binder.getCallingUid() == appUid) {
                runCatching {
                    data.enforceInterface(Constants.DESCRIPTOR)
                    when (data.readInt()) {
                        Constants.ACTION_GET_BINDER -> {
                            reply?.writeNoException()
                            reply?.writeStrongBinder(HMAService.instance)
                            return true
                        }
                        else -> {
                            // No recognized action – do nothing.
                        }
                    }
                }.onFailure {
                    // Any exception here is caught so that unauthorized apps can't
                    // glean any information via error messages.
                }
            }
            // Reset the Parcel positions before returning.
            data.setDataPosition(0)
            reply?.setDataPosition(0)
        }
        return false
    }
} 
