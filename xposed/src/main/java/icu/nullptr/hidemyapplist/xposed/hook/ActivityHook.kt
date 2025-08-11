package icu.nullptr.hidemyapplist.xposed.hook

import android.content.Intent
import com.github.kyuubiran.ezxhelper.init.InitFields
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.getStaticIntField
import icu.nullptr.hidemyapplist.xposed.HMAService

class ActivityHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "ActivityHook"
    }

    private var hook: XC_MethodHook.Unhook? = null

    override fun load() {
        hook = findMethod(
            "com.android.server.wm.ActivityStarter"
        ) {
            name == "executeRequest"
        }.hookBefore { param ->
            runCatching {
                val request = param.args[0]
                val caller = getObjectField(request, "callingPackage") as String?
                val intent = getObjectField(request, "intent") as Intent?
                val targetApp = intent?.component?.packageName

                if (service.shouldHide(caller, targetApp)) {
                    param.result = getStaticIntField(
                        findClass(
                            "android.app.ActivityManager",
                            InitFields.ezXClassLoader
                        ),
                        "START_INTENT_NOT_RESOLVED"
                    )
                }
            }.onFailure {
                // unload()
            }
        }
    }

    override fun unload() {
        hook?.unhook()
        hook = null
    }
}
