package icu.nullptr.hidemyapplist.xposed.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.util.Log
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import icu.nullptr.hidemyapplist.xposed.HMAService

class StartActivityHook(private val service: HMAService) : IFrameworkHook {

    companion object {
        private const val TAG = "StartActivityHook"
    }

    private val hooks = mutableListOf<XC_MethodHook.Unhook>()

    override fun load() {
        Log.d(TAG, "StartActivityHook loading...")

        val classes = listOfNotNull(
            Context::class.java,
            Activity::class.java,
            ContextWrapper::class.java,
            runCatching { Class.forName("android.app.ContextImpl") }.getOrNull()
        )

        val methodSigs = listOf(
            arrayOf(Intent::class.java),
            arrayOf(Intent::class.java, Bundle::class.java),
            arrayOf(Intent::class.java, Int::class.javaPrimitiveType),
            arrayOf(Intent::class.java, Int::class.javaPrimitiveType, Bundle::class.java)
        )

        val methodNames = listOf("startActivity", "startActivityForResult")

        for (clazz in classes) {
            for (method in methodNames) {
                for (sig in methodSigs) {
                    runCatching {
                        val m = findMethodOrNull(clazz, findSuper = true) {
                            name == method && parameterTypes.contentEquals(sig)
                        } ?: run {
                            Log.w(TAG, "❌ Method not found: ${clazz.name}#$method(${sig.joinToString { it.simpleName ?: "?" }})")
                            return@runCatching
                        }

                        Log.d(TAG, "✅ Hooking: ${clazz.name}#$method(${sig.joinToString { it.simpleName ?: "?" }})")

                        hooks += m.hookBefore { param ->
                            runCatching {
                                val thisObj = param.thisObject
                                Log.d(TAG, "→ Hook called in: ${thisObj?.javaClass?.name}")

                                val context = thisObj as? Context
                                if (context == null) {
                                    Log.w(TAG, "⚠️ thisObject is not Context: ${thisObj?.javaClass?.name}")
                                    return@hookBefore
                                }

                                val callerPackageName = context.packageName
                                val intent = param.args[0] as? Intent
                                if (intent == null) {
                                    Log.w(TAG, "⚠️ Intent is null")
                                    return@hookBefore
                                }

                                val targetPackage = intent.component?.packageName ?: intent.`package`
                                if (targetPackage == null) {
                                    Log.w(TAG, "⚠️ Could not resolve target package from Intent: $intent")
                                    return@hookBefore
                                }

                                val shouldBlock = service.shouldHide(callerPackageName, targetPackage)
                                Log.d(TAG, "Intercepted: $callerPackageName → $targetPackage | shouldHide = $shouldBlock")

                                if (shouldBlock) {
                                    Log.i(TAG, "🚫 Blocking startActivity to $targetPackage")
                                    param.throwable = ActivityNotFoundException("Activity not found for $targetPackage")
                                }
                            }.onFailure {
                                Log.e(TAG, "❗ Error inside hook", it)
                            }
                        }
                    }.onFailure {
                        Log.e(TAG, "❗ Error setting up hook: ${clazz.name}#$method", it)
                    }
                }
            }
        }
    }

    override fun unload() {
        hooks.forEach { it.unhook() }
        hooks.clear()
        Log.d(TAG, "Hooks unloaded.")
    }

    override fun onConfigChanged() {}
}
