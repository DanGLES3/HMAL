package icu.nullptr.hidemyapplist.xposed.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Bundle
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import icu.nullptr.hidemyapplist.xposed.HMAService

class StartActivityHook(private val service: HMAService) : IFrameworkHook {

    private val hooks = mutableListOf<XC_MethodHook.Unhook>()

    override fun load() {
        val classesToHook = listOf(
            Context::class.java,
            Activity::class.java,
            ContextWrapper::class.java
        )

        val methodSignatures = listOf(
            arrayOf(Intent::class.java),
            arrayOf(Intent::class.java, Bundle::class.java),
            arrayOf(Intent::class.java, Int::class.java),
            arrayOf(Intent::class.java, Int::class.java, Bundle::class.java)
        )

        val methodNamesToHook = listOf("startActivity", "startActivityForResult")

        classesToHook.forEach { clazz ->
            methodNamesToHook.forEach { methodName ->
                methodSignatures.forEach { signature ->
                    runCatching {
                        val method = findMethodOrNull(clazz, true) {
                            this.name == methodName && this.parameterTypes.contentEquals(signature)
                        } ?: return@runCatching

                        hooks += method.hookBefore { param ->
                            val context = param.thisObject as? Context ?: return@hookBefore
                            val callerPackage = context.packageName
                            val intent = param.args[0] as? Intent ?: return@hookBefore

                            val appConfig = service.config.scope[callerPackage]
                            if (appConfig == null || appConfig.useWhitelist) return@hookBefore

                            val isExplicit = intent.component != null || intent.`package` != null
                            val targetPackage = intent.component?.packageName ?: intent.`package`

                            if (isExplicit && targetPackage != null &&
                                service.shouldHide(callerPackage, targetPackage)) {

                                param.throwable = ActivityNotFoundException(
                                    "No Activity found to handle $intent"
                                )
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
}
