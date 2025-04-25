package icu.nullptr.hidemyapplist.xposed.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.logE
import icu.nullptr.hidemyapplist.xposed.logI

class StartActivityHook(private val service: HMAService) : IFrameworkHook {

    companion object {
        private const val TAG = "StartActivityHook"
    }

    private val hooks = mutableListOf<XC_MethodHook.Unhook>()

    override fun load() {
        logI(TAG, "Load hook")

        val classes = listOf(
            Context::class.java,
            Activity::class.java,
            ContextWrapper::class.java
        )
        val methodSigs = listOf(
            arrayOf(Intent::class.java),
            arrayOf(Intent::class.java, android.os.Bundle::class.java),
            arrayOf(Intent::class.java, Int::class.javaPrimitiveType),
            arrayOf(Intent::class.java, Int::class.javaPrimitiveType, android.os.Bundle::class.java)
        )
        val methodNames = listOf("startActivity", "startActivityForResult")

        for (clazz in classes) {
            for (method in methodNames) {
                for (sig in methodSigs) {
                    runCatching {
                        val m = findMethodOrNull(clazz, findSuper = true) {
                            name == method && parameterTypes.contentEquals(sig)
                        } ?: return@runCatching
                        hooks += m.hookBefore { param ->
                            runCatching {
                                val context = param.thisObject as? Context ?: return@hookBefore
                                val callerPackageName = context.packageName // Get the package name
                                val intent = param.args[0] as? Intent ?: return@hookBefore
                                val targetPackage = intent.component?.packageName ?: intent.`package` ?: return@hookBefore

                                if (service.shouldHide(callerPackageName, targetPackage)) {
                                    logI(TAG, "Blocked startActivity for $targetPackage from $callerPackageName")
                                    param.throwable = ActivityNotFoundException("Activity not found for $targetPackage")
                                }
                            }.onFailure {
                                logE(TAG, "Error in hook", it)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun unload() {
        hooks.forEach { it.unhook() }
        hooks.clear()
    }

    override fun onConfigChanged() {}
}
