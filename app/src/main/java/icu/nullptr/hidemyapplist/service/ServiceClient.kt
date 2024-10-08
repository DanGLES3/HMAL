package icu.nullptr.hidemyapplist.service

import android.os.IBinder.DeathRecipient
import android.os.Parcel
import android.os.RemoteException
import android.os.ServiceManager
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.IHMAService
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object ServiceClient : IHMAService, DeathRecipient {

    private const val TAG = "ServiceHelper"

    private class ServiceProxy(private val obj: IHMAService) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            val result = method.invoke(obj, *args.orEmpty())
            return result
        }
    }

    @Volatile
    private var service: IHMAService? = null

    override fun binderDied() {
        service = null
    }

    override fun asBinder() = service?.asBinder()

    private fun getService(): IHMAService? {
        if (service != null) return service
        val pm = ServiceManager.getService("package")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        val remote = try {
            data.writeInterfaceToken(Constants.DESCRIPTOR)
            data.writeInt(Constants.ACTION_GET_BINDER)
            pm.transact(Constants.TRANSACTION, data, reply, 0)
            reply.readException()
            val binder = reply.readStrongBinder()
            IHMAService.Stub.asInterface(binder)
        } catch (e: RemoteException) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
        if (remote != null) {
            remote.asBinder().linkToDeath(this, 0)
            service = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(IHMAService::class.java),
                ServiceProxy(remote)
            ) as IHMAService
        }
        return service
    }


    override fun getServiceVersion() = getService()?.serviceVersion ?: 0

    override fun syncConfig(json: String) {
        getService()?.syncConfig(json)
    }

    override fun stopService(cleanEnv: Boolean) {
        getService()?.stopService(cleanEnv)
    }
}
