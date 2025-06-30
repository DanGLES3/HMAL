package icu.nullptr.hidemyapplist.xposed.hook

import android.content.Intent
import android.content.pm.ActivityInfo
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import icu.nullptr.hidemyapplist.xposed.HMAService

class StartActivityHook(private val service: HMAService) : IFrameworkHook {
    private val hooks = mutableListOf<XC_MethodHook.Unhook>()

    override fun load() {
        runCatching {
            val cls = Class.forName("com.android.server.wm.ActivityTaskSupervisor")

            val method = findMethodOrNull(cls, true) { m ->
                m.name == "checkStartAnyActivityPermission" &&
                m.parameterTypes.contentEquals(arrayOf(
                    Intent::class.java,
                    ActivityInfo::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Class.forName("com.android.server.wm.WindowProcessController"),
                    Class.forName("com.android.server.wm.ActivityRecord"),
                    Class.forName("com.android.server.wm.Task")
                ))
            } ?: return@runCatching

            hooks += method.hookBefore { param ->
                runCatching {
                    val intent = param.args[0] as? Intent ?: return@hookBefore
                    val aInfo = param.args[1] as? ActivityInfo ?: return@hookBefore
                    val callingPackage = param.args[6] as? String ?: return@hookBefore

                    val appConfig = service.config.scope[callingPackage] ?: return@hookBefore
                    val targetPkg = aInfo.packageName

                    if (appConfig.useWhitelist) {
                        if (!appConfig.whitelist.contains(targetPkg)) {
                            throw SecurityException("Blocked by whitelist: $callingPackage -> $targetPkg")
                        }
                        return@hookBefore
                    }

                    if (service.shouldHide(callingPackage, targetPkg)) {
                        throw SecurityException("Blocked by blacklist: $callingPackage -> $targetPkg")
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
