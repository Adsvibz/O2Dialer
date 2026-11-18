@file:Suppress("DEPRECATION")

package com.o2dialer.app

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** User-initiated, signed self-updates via Android's official installer.
 * The platform always retains authority over whether confirmation is required.
 */
internal object UpdateInstaller {
    private const val ACTION = "com.o2dialer.app.UPDATE_INSTALL_RESULT"
    var installing by mutableStateOf(false); private set
    var confirmation by mutableStateOf<Intent?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var allowLegacyInstaller by mutableStateOf(false); private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("o2installer", Context.MODE_PRIVATE)
    private fun clear(ctx: Context) { prefs(ctx).edit().clear().apply() }

    fun foreground(activity: Activity): Boolean =
        !activity.isFinishing && !activity.isDestroyed &&
            (activity as? ComponentActivity)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

    fun resetError() { message = null; allowLegacyInstaller = false }

    suspend fun start(activity: Activity, file: File, legacy: Boolean) {
        val ctx = activity.applicationContext
        val installer = ctx.packageManager.packageInstaller
        var id = -1
        resetError()
        try {
            val previous = prefs(ctx).getInt("session", -1)
            if (previous >= 0) runCatching { installer.abandonSession(previous) }
            clear(ctx)
            if (legacy) {
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = ClipData.newRawUri("O2Dialer update", uri)
                activity.startActivity(intent)
                return
            }
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(ctx.packageName)
            params.setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            id = withContext(Dispatchers.IO) { installer.createSession(params) }
            withContext(Dispatchers.IO) {
                installer.openSession(id).use { session ->
                    file.inputStream().use { input ->
                        session.openWrite("base.apk", 0, file.length()).use { output ->
                            input.copyTo(output, 64 * 1024)
                            session.fsync(output)
                        }
                    }
                }
            }
            if (Store.campActive() || !foreground(activity)) {
                runCatching { installer.abandonSession(id) }
                return
            }
            val nonce = UUID.randomUUID().toString()
            check(prefs(ctx).edit().putInt("session", id).putString("nonce", nonce).commit())
            val callback = Intent(ctx, UpdateInstallReceiver::class.java).setAction(ACTION)
                .setData(Uri.parse("o2-update://session/$id/$nonce"))
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(ctx, id, callback, flags)
            installing = true
            installer.openSession(id).use { it.commit(result.intentSender) }
        } catch (e: CancellationException) {
            if (id >= 0) runCatching { installer.abandonSession(id) }
            clear(ctx); installing = false
            throw e
        } catch (_: Exception) {
            if (id >= 0) runCatching { installer.abandonSession(id) }
            clear(ctx); installing = false
            message = T("Could not start the installer. Your download is saved.", "تعذر تشغيل المثبت. ملف التحديث محفوظ.")
            allowLegacyInstaller = !legacy
        }
    }

    fun continueInstallation(activity: Activity) {
        val consent = confirmation ?: return
        if (!foreground(activity) || Store.campActive()) return
        runCatching { activity.startActivity(consent) }.onSuccess { confirmation = null }
            .onFailure {
                cancel(activity)
                message = T("Could not open Android confirmation. Try the standard installer.", "تعذر فتح تأكيد أندرويد. جرّب المثبت العادي.")
                allowLegacyInstaller = true
            }
    }

    fun cancel(context: Context) {
        val id = prefs(context).getInt("session", -1)
        if (id >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(id) }
        clear(context)
        confirmation = null; installing = false; allowLegacyInstaller = false
        message = T("Installation cancelled. The download is saved.", "تم إلغاء التثبيت. ملف التحديث محفوظ.")
    }

    fun onResult(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = prefs(context).getInt("session", -1)
        val nonce = prefs(context).getString("nonce", null) ?: return
        if (id < 0 || intent.data?.toString() != "o2-update://session/$id/$nonce") return
        if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, id) != id) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val consent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (consent != null) {
                installing = true; confirmation = consent
                AppUpdates.open()
            } else {
                cancel(context)
                message = T("Android did not return a confirmation window.", "لم يرسل أندرويد نافذة التأكيد.")
                allowLegacyInstaller = true
            }
            return
        }
        clear(context)
        installing = false; confirmation = null
        if (status == PackageInstaller.STATUS_SUCCESS) {
            resetError()
            AppUpdates.installed()
            return
        }
        // A rejected or cancelled install must not trigger another installation automatically.
        allowLegacyInstaller = false
        message = when (status) {
            PackageInstaller.STATUS_FAILURE_BLOCKED -> T("Android or device policy blocked installation.", "النظام أو سياسة الجهاز تمنع التثبيت.")
            PackageInstaller.STATUS_FAILURE_ABORTED -> T("Installation cancelled.", "تم إلغاء التثبيت.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> T("Not enough free storage.", "مساحة التخزين غير كافية.")
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> T("Update incompatible with this device.", "التحديث غير متوافق مع الجهاز.")
            PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_CONFLICT ->
                T("Android rejected the update package.", "رفض أندرويد حزمة التحديث.")
            else -> T("Installation failed (status $status). Try again.", "فشل التثبيت (رمز $status). حاول مجددًا.")
        }
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UpdateInstaller.onResult(context, intent)
    }
}
