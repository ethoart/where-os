package com.watchlauncher

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import kotlinx.coroutines.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var wallpaperView: ImageView
    private lateinit var clockView: ClockView
    private lateinit var dockContainer: LinearLayout
    private lateinit var swipeHint: View

    // ── State ────────────────────────────────────────────────────────────────
    private val pinnedApps = mutableListOf<AppInfo>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Gesture detector for swipe-up ────────────────────────────────────────
    private lateinit var gestureDetector: GestureDetectorCompat

    // ── App-install / uninstall receiver ────────────────────────────────────
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadPinnedApps()
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full screen immersive for Wear OS
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_main)

        wallpaperView  = findViewById(R.id.wallpaperView)
        clockView      = findViewById(R.id.clockView)
        dockContainer  = findViewById(R.id.dockContainer)
        swipeHint      = findViewById(R.id.swipeHint)

        setupWallpaper()
        setupGestures()
        loadPinnedApps()
        registerPackageReceiver()
    }

    override fun onResume() {
        super.onResume()
        setupWallpaper()   // refresh if user changed wallpaper
        loadPinnedApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    // ════════════════════════════════════════════════════════════════════════
    // WALLPAPER
    // ════════════════════════════════════════════════════════════════════════
    private fun setupWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(this)
            val drawable = wm.drawable
            if (drawable != null) {
                wallpaperView.setImageDrawable(drawable)
                wallpaperView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                // Fallback: animated gradient set in XML background
                wallpaperView.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        } catch (e: Exception) {
            wallpaperView.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // GESTURES — single finger swipe up opens drawer, long-press opens settings
    // ════════════════════════════════════════════════════════════════════════
    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dY = (e1?.y ?: 0f) - e2.y
                val dX = abs((e1?.x ?: 0f) - e2.x)
                // Swipe UP → open app drawer
                if (dY > 80 && dY > dX) {
                    openAppDrawer()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                // Long-press → open wallpaper picker
                openWallpaperPicker()
            }

            override fun onDown(e: MotionEvent) = true   // required to receive fling
        }

        gestureDetector = GestureDetectorCompat(this, listener)

        // Attach to root so the whole screen is the swipe target
        val rootView = findViewById<View>(R.id.rootLayout)
        rootView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DOCK — shows pinned apps at bottom
    // ════════════════════════════════════════════════════════════════════════
    private fun loadPinnedApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) { queryPinnedApps() }
            pinnedApps.clear()
            pinnedApps.addAll(apps)
            buildDock()
        }
    }

    private fun queryPinnedApps(): List<AppInfo> {
        // Default pinned: Phone, Messages, Settings (if installed)
        val defaultPins = listOf(
            "com.google.android.apps.maps",
            "com.google.android.wearable.app",   // Wear OS companion
            "com.google.android.gms"              // Google Play Services / Settings
        )
        val pm = packageManager
        val result = mutableListOf<AppInfo>()
        for (pkg in defaultPins) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                result.add(
                    AppInfo(
                        label = pm.getApplicationLabel(info).toString(),
                        packageName = pkg,
                        icon = pm.getApplicationIcon(pkg),
                        isPinned = true
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) { }
        }
        // Always add Settings
        try {
            val settingsPkg = "com.android.settings"
            val info = pm.getApplicationInfo(settingsPkg, 0)
            if (result.none { it.packageName == settingsPkg }) {
                result.add(AppInfo(
                    label = "Settings",
                    packageName = settingsPkg,
                    icon = pm.getApplicationIcon(settingsPkg),
                    isPinned = true
                ))
            }
        } catch (_: Exception) { }
        return result
    }

    private fun buildDock() {
        dockContainer.removeAllViews()
        val size = dpToPx(42)
        val margin = dpToPx(6)

        pinnedApps.take(5).forEach { app ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginStart = margin; it.marginEnd = margin
                }
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = app.label
                setOnClickListener { launchApp(app.packageName) }
                setOnLongClickListener {
                    Toast.makeText(context, app.label, Toast.LENGTH_SHORT).show()
                    true
                }
            }
            dockContainer.addView(iv)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // NAVIGATION
    // ════════════════════════════════════════════════════════════════════════
    private fun openAppDrawer() {
        startActivity(Intent(this, AppDrawerActivity::class.java))
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun openWallpaperPicker() {
        startActivity(Intent(this, WallpaperPickerActivity::class.java))
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "App not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
