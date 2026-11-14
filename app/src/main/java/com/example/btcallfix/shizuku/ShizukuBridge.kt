package com.example.btcallfix.shizuku

import com.example.btcallfix.IBtService
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object ShizukuBridge {
    private class Req(val address: String, val allowed: Boolean, val done: (String) -> Unit) {
        private val once = AtomicBoolean(false)
        fun finish(m: String) { if (once.compareAndSet(false, true)) done(m) }
    }

    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val waiting = ArrayList<Req>()
    @Volatile private var svc: IBtService? = null
    private var binding = false
    private lateinit var appCtx: Context

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            svc = IBtService.Stub.asInterface(b); binding = false; processAll()
        }
        override fun onServiceDisconnected(n: ComponentName) { svc = null }
    }

    /** Safe to call from any thread. [done] is always called exactly once (timeout 10 s). */
    fun setProfile(ctx: Context, address: String, allowed: Boolean, done: (String) -> Unit) {
        appCtx = ctx.applicationContext
        val r = Req(address, allowed, done)
        main.postDelayed({
            waiting.remove(r); r.finish("timeout: Shizuku not ready (is it running?)")
        }, 10_000)
        main.post {
            waiting.add(r)
            if (svc?.asBinder()?.pingBinder() == true) processAll() else ensureBound()
        }
    }

    private fun ensureBound() {
        if (binding) return
        binding = true
        Shizuku.addBinderReceivedListenerSticky(object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                Shizuku.removeBinderReceivedListener(this)
                main.post { bind() }
            }
        })
    }

    private fun bind() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            binding = false; failAll("Shizuku permission not granted to this app"); return
        }
        val args = Shizuku.UserServiceArgs(ComponentName(appCtx.packageName, BtUserService::class.java.name))
            .daemon(false).processNameSuffix("bt").debuggable(false).version(1)
        Shizuku.bindUserService(args, conn)
    }

    private fun failAll(msg: String) {
        val l = ArrayList(waiting); waiting.clear(); l.forEach { it.finish(msg) }
    }

    private fun processAll() {
        val l = ArrayList(waiting); waiting.clear()
        l.forEach { r ->
            exec.execute {
                r.finish(try { svc!!.setCallProfile(r.address, r.allowed) } catch (t: Throwable) { "call failed: $t" })
            }
        }
    }
}
