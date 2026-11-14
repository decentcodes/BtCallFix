package com.example.btcallfix.shizuku

import com.example.btcallfix.IBtService
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Runs inside Shizuku's process with shell (uid 2000) privileges. */
class BtUserService : IBtService.Stub() {

    @Volatile private var headset: BluetoothHeadset? = null

    private var initNote = ""

    init {
        try { HiddenApiBypass.addHiddenApiExemptions("L") } catch (_: Throwable) {}
        // Normal app processes get this set up by ActivityThread; the Shizuku process doesn't.
        try {
            val cls = Class.forName("android.os.BluetoothServiceManager")
            val ctor = cls.getDeclaredConstructor(); ctor.isAccessible = true
            val bsm = ctor.newInstance()
            val fi = Class.forName("android.bluetooth.BluetoothFrameworkInitializer")
            val m = fi.getDeclaredMethod("setBluetoothServiceManager", cls); m.isAccessible = true
            m.invoke(null, bsm)
            initNote = "bsm set"
        } catch (t: Throwable) { initNote = "bsm init: ${t.cause ?: t}" }
        if (Looper.getMainLooper() == null) {
            Thread { Looper.prepareMainLooper(); Looper.loop() }.start()
            Thread.sleep(300)
        }
    }

    override fun destroy() { exitProcess(0) }

    private var ctxCache: Context? = null

    /** Run [block] on the main looper thread and wait for its result. */
    private fun <T> onMain(block: () -> T): T {
        val latch = CountDownLatch(1)
        var res: Result<T>? = null
        Handler(Looper.getMainLooper()).post { res = runCatching(block); latch.countDown() }
        if (!latch.await(8, TimeUnit.SECONDS)) throw RuntimeException("main looper not responding")
        return res!!.getOrThrow()
    }

    private fun shellContext(): Context {
        ctxCache?.let { return it }
        val at = Class.forName("android.app.ActivityThread")
        val sys = at.getMethod("systemMain").invoke(null)
        val sysCtx = at.getMethod("getSystemContext").invoke(sys) as Context
        val c = try { sysCtx.createPackageContext("com.android.shell", 0) } catch (_: Throwable) { sysCtx }
        ctxCache = c
        return c
    }

    private fun manualAdapter(notes: StringBuilder): BluetoothAdapter? {
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java).invoke(null, "bluetooth_manager") as? IBinder
        notes.append("sm=${binder != null}; ")
        binder ?: return null
        val mgr = Class.forName("android.bluetooth.IBluetoothManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val builder = Class.forName("android.content.AttributionSource\$Builder")
        val b = builder.getConstructor(Int::class.javaPrimitiveType).newInstance(android.os.Process.myUid())
        builder.getMethod("setPackageName", String::class.java).invoke(b, "com.android.shell")
        val attr = builder.getMethod("build").invoke(b)
        val ctor = BluetoothAdapter::class.java.declaredConstructors.first { it.parameterTypes.size == 2 }
        ctor.isAccessible = true
        notes.append("ctor=${ctor.parameterTypes.joinToString { it.simpleName }}; ")
        val args = ctor.parameterTypes.map { t ->
            when {
                t.name == "android.content.Context" -> shellContext()
                t.name.endsWith("AttributionSource") -> attr
                else -> mgr
            }
        }.toTypedArray()
        return ctor.newInstance(*args) as BluetoothAdapter
    }

    private fun findAdapter(notes: StringBuilder): BluetoothAdapter? {
        try {
            val a = shellContext().getSystemService(BluetoothManager::class.java)?.adapter
            if (a != null) return a
            notes.append("ctxMgr=null; ")
        } catch (t: Throwable) { notes.append("ctxMgr err ${t.cause ?: t}; ") }
        try {
            val a = BluetoothAdapter.getDefaultAdapter()
            if (a != null) return a
            notes.append("default=null; ")
        } catch (t: Throwable) { notes.append("default err ${t.cause ?: t}; ") }
        try {
            return manualAdapter(notes)
        } catch (t: Throwable) { notes.append("manual err ${t.cause ?: t}; ") }
        return null
    }

    private fun getHeadset(adapter: BluetoothAdapter): BluetoothHeadset? {
        headset?.let { return it }
        val latch = CountDownLatch(1)
        val ctx = shellContext()
        onMain {
            adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    headset = proxy as BluetoothHeadset; latch.countDown()
                }
                override fun onServiceDisconnected(profile: Int) { headset = null }
            }, BluetoothProfile.HEADSET)
        }
        latch.await(6, TimeUnit.SECONDS)
        return headset
    }

    private fun invoke(obj: Any, name: String, device: BluetoothDevice, extra: Int? = null): Any? {
        val m = if (extra == null)
            obj.javaClass.getDeclaredMethod(name, BluetoothDevice::class.java)
        else
            obj.javaClass.getDeclaredMethod(name, BluetoothDevice::class.java, Int::class.javaPrimitiveType)
        m.isAccessible = true
        return if (extra == null) m.invoke(obj, device) else m.invoke(obj, device, extra)
    }

    override fun setCallProfile(address: String, allowed: Boolean): String {
        return try {
            val notes = StringBuilder()
            val adapter = onMain { findAdapter(notes) } ?: return "no bluetooth adapter [$notes] [$initNote]"
            val device = adapter.getRemoteDevice(address)
            val hs = getHeadset(adapter) ?: return "headset proxy timed out"
            val policy = if (allowed) 100 else 0   // ALLOWED / FORBIDDEN
            val r1 = invoke(hs, "setConnectionPolicy", device, policy)
            val r2 = try { invoke(hs, if (allowed) "connect" else "disconnect", device) } catch (t: Throwable) { "n/a: ${t.cause ?: t}" }
            "calls ${if (allowed) "ON" else "OFF"}: policy=$r1 action=$r2"
        } catch (t: Throwable) {
            headset = null
            val c = t.cause ?: t
            "ERROR: $c @ ${c.stackTrace.take(4).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }} [$initNote]"
        }
    }
}
