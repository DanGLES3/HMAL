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
            arrayOf(
                Intent::class.java
            ),
            arrayOf(
                Intent::class.java,
                Bundle::class.java
            ),
            arrayOf(
                Intent::class.java,
                Int::class.javaPrimitiveType!!
            ),
            arrayOf(
                Intent::class.java,
                Int::class.javaPrimitiveType!!,
                Bundle::class.java
            )
        )

        val methodNames = listOf("startActivity", "startActivityForResult")

        classesToHook.forEach { clazz ->
            methodNames.forEach { name ->
                methodSignatures.forEach { sig ->
                    runCatching {
                        val method = findMethodOrNull(clazz, true) {
                            this.name == name && this.parameterTypes.contentEquals(sig)
                        } ?: return@runCatching

                        hooks += method.hookBefore { param: XC_MethodHook.MethodHookParam ->
                            runCatching {
                                val context = param.thisObject as? Context ?: return@hookBefore
                                val callerPkg = context.packageName
                                val intent = param.args[0] as? Intent ?: return@hookBefore

                                val appConfig = service.config.scope[callerPkg] ?: return@hookBefore
                                if (appConfig.useWhitelist) {
                                    // Whitelist mode: only allow intents to packages in whitelist
                                    val allowList = appConfig.whitelist
                                    val targetPkg = intent.component?.packageName ?: intent.`package`
                                    if (targetPkg == null || !allowList.contains(targetPkg)) {
                                        throw ActivityNotFoundException("No Activity found to handle $intent")
                                    }
                                    return@hookBefore
                                }

                                // Blacklist mode
                                val targetPkg = intent.component?.packageName ?: intent.`package`
                                if (targetPkg != null && service.shouldHide(callerPkg, targetPkg)) {
                                    throw ActivityNotFoundException("No Activity found to handle $intent")
                                }
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
