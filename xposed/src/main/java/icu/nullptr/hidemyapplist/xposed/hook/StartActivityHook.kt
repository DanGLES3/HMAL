package icu.nullptr.hidemyapplist.xposed.hook

import android.content.Intent
import android.content.pm.ActivityInfo
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import icu.nullptr.hidemyapplist.xposed.HMAService

class CheckStartAnyActivityPermissionHook(
    private val service: HMAService
) : IFrameworkHook {

    private val hooks = mutableListOf<XC_MethodHook.Unhook>()

    override fun load() {
        runCatching {
            val cls = Class.forName("com.android.server.wm.ActivityTaskSupervisor")
            val method = findMethodOrNull(cls, true) { m ->
                m.name == "checkStartAnyActivityPermission" &&
                m.parameterTypes.size == 13
            } ?: return@runCatching

            hooks += method.hookBefore { param ->
                runCatching {
                    // --- pull args ---
                    val callingPkg = param.args[6] as? String ?: return@hookBefore
                    val aInfo      = param.args[1] as? ActivityInfo ?: return@hookBefore
                    val targetPkg  = aInfo.packageName

                    // --- lookup config ---
                    val appConfig = service.config.scope[callingPkg] ?: return@hookBefore

                    // --- whitelist mode?
                    if (appConfig.useWhitelist) {
                        // if target not explicitly allowed → block
                        if (!appConfig.whiteList.contains(targetPkg)) {
                            throw SecurityException(
                                "Starting $targetPkg blocked by HMA (whitelist)"
                            )
                        }
                        // else allowed: return, no exception
                        return@hookBefore
                    }

                    // --- blacklist mode ---
                    if (service.shouldHide(callingPkg, targetPkg)) {
                        throw SecurityException(
                            "Starting $targetPkg blocked by HMA (blacklist)"
                        )
                    }
                }
                // silently ignore errors here
            }
        }
    }

    override fun unload() {
        hooks.forEach { it.unhook() }
        hooks.clear()
    }
}
