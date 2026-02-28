package com.watchlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlin.math.abs

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var adapter: AppAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var gestureDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        recyclerView = findViewById(R.id.appRecyclerView)
        searchBar    = findViewById(R.id.searchBar)

        setupRecyclerView()
        setupSearch()
        setupSwipeDownToDismiss()
        loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ════════════════════════════════════════════════════════════════════════
    // RecyclerView — 3-column grid, perfect for a small round watch screen
    // ════════════════════════════════════════════════════════════════════════
    private fun setupRecyclerView() {
        adapter = AppAdapter(
            apps = emptyList(),
            onAppClick = { app ->
                launchApp(app.packageName)
            },
            onAppLongClick = { app ->
                Toast.makeText(this, "Long-press: ${app.label}", Toast.LENGTH_SHORT).show()
                // Future: add to dock / uninstall menu
            }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter
    }

    // ════════════════════════════════════════════════════════════════════════
    // Search
    // ════════════════════════════════════════════════════════════════════════
    private fun setupSearch() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ════════════════════════════════════════════════════════════════════════
    // Swipe DOWN → go back to home (one-finger)
    // ════════════════════════════════════════════════════════════════════════
    private fun setupSwipeDownToDismiss() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dY = e2.y - (e1?.y ?: 0f)
                val dX = abs((e1?.x ?: 0f) - e2.x)
                if (dY > 100 && dY > dX && abs(velocityY) > 300) {
                    finish()
                    overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
                    return true
                }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }

        gestureDetector = GestureDetectorCompat(this, listener)

        // Intercept touches at the activity level
        // (RecyclerView will still scroll, but we get the fling on empty space)
        val root = findViewById<View>(R.id.drawerRoot)
        root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false   // false → let RecyclerView also handle the event
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Load all launchable apps from PackageManager
    // ════════════════════════════════════════════════════════════════════════
    private fun loadApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) { queryAllApps() }
            adapter.updateApps(apps)
        }
    }

    private fun queryAllApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pm = packageManager
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        return resolveInfoList
            .map { ri ->
                AppInfo(
                    label       = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    icon        = ri.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
            .filter { it.packageName != packageName }  // hide this launcher itself
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show()
        }
    }
}
