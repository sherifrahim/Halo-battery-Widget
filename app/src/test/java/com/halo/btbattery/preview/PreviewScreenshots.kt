package com.halo.btbattery.preview

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.btbattery.data.HaloStore
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.ThemeMode
import com.halo.btbattery.ui.HaloScreen
import com.halo.btbattery.ui.MainViewModel
import com.halo.btbattery.ui.theme.HaloTheme
import com.halo.btbattery.widget.OverviewRoot
import com.halo.btbattery.widget.SelectedRoot
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val OUT = "build/previews"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h880dp-xxhdpi", application = Application::class)
class PreviewScreenshots {
    @get:Rule val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun seed() {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        app.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 78).putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING),
        )
        runBlocking { HaloStore.get(app).saveSnapshot(SampleData.snapshot) }
    }

    private fun app(theme: ThemeMode, name: String, page: Int = 0) {
        val vm = MainViewModel(app)
        compose.setContent { HaloTheme(theme) { if (page == 0) HaloScreen(vm) else androidx.compose.foundation.layout.Box { com.halo.btbattery.ui.components.AmbientBackdrop(); com.halo.btbattery.ui.StyleScreen(vm) } } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$OUT/$name.png")
    }

    @Test fun appDark() = app(ThemeMode.DARK, "app_dark")
    @Test @Config(qualifiers = "w400dp-h880dp-notnight-xxhdpi") fun appLight() = app(ThemeMode.LIGHT, "app_light")
    @Test @Config(qualifiers = "w400dp-h880dp-notnight-xxhdpi") fun styleLight() = app(ThemeMode.LIGHT, "style_light", page = 1)
    @Test fun styleDark() = app(ThemeMode.DARK, "style_dark", page = 1)

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun widget(size: DpSize, theme: ThemeMode, name: String, selected: Boolean = false): Unit = runBlocking {
        val settings = Settings(theme = theme)
        val result = GlanceRemoteViews().compose(app, size) { if (selected) SelectedRoot(SampleData.snapshot, settings) else OverviewRoot(SampleData.snapshot, settings) }
        val host = FrameLayout(app)
        val view = result.remoteViews.apply(app, host)
        val density = app.resources.displayMetrics.density
        val w = (size.width.value * density).toInt()
        val h = (size.height.value * density).toInt()
        // Backdrop standing in for a wallpaper so the glass reads as glass.
        val frame = FrameLayout(app).apply {
            setBackgroundColor(if (theme == ThemeMode.DARK) 0xFF1F1F1F.toInt() else 0xFFC9D3E0.toInt())
            setPadding(40, 40, 40, 40)
            addView(view, FrameLayout.LayoutParams(w, h))
        }
        val spec = { px: Int -> View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY) }
        frame.measure(spec(w + 80), spec(h + 80))
        frame.layout(0, 0, w + 80, h + 80)
        val bmp = android.graphics.Bitmap.createBitmap(w + 80, h + 80, android.graphics.Bitmap.Config.ARGB_8888)
        frame.draw(android.graphics.Canvas(bmp))
        java.io.File(OUT).mkdirs()
        java.io.FileOutputStream("$OUT/$name.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        Unit
    }

    @Test fun launcherIcon() {
        val d = androidx.core.content.res.ResourcesCompat.getDrawable(app.resources, com.halo.btbattery.R.mipmap.ic_launcher, null)!!
        val bmp = android.graphics.Bitmap.createBitmap(432, 432, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        // Mask like a launcher would: a rounded square.
        val path = android.graphics.Path().apply { addRoundRect(0f, 0f, 432f, 432f, 110f, 110f, android.graphics.Path.Direction.CW) }
        c.clipPath(path)
        d.setBounds(0, 0, 432, 432)
        d.draw(c)
        java.io.File(OUT).mkdirs()
        java.io.FileOutputStream("$OUT/launcher_icon.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun selected2x2Dark() = widget(DpSize(170.dp, 170.dp), ThemeMode.DARK, "selected_2x2_dark", selected = true)
    @Test fun selected4x2Dark() = widget(DpSize(350.dp, 170.dp), ThemeMode.DARK, "selected_4x2_dark", selected = true)
    @Test fun selected2x2Light() = widget(DpSize(170.dp, 170.dp), ThemeMode.LIGHT, "selected_2x2_light", selected = true)
    @Test fun widget2x1Dark() = widget(DpSize(170.dp, 70.dp), ThemeMode.DARK, "widget_2x1_dark")

    @Test fun widget2x2Dark() = widget(DpSize(170.dp, 170.dp), ThemeMode.DARK, "widget_2x2_dark")
    @Test fun widget2x2Light() = widget(DpSize(170.dp, 170.dp), ThemeMode.LIGHT, "widget_2x2_light")
    @Test fun widget4x1Dark() = widget(DpSize(340.dp, 80.dp), ThemeMode.DARK, "widget_4x1_dark")
    @Test fun widget4x2Dark() = widget(DpSize(350.dp, 170.dp), ThemeMode.DARK, "widget_4x2_dark")
    @Test fun widget4x2Light() = widget(DpSize(350.dp, 170.dp), ThemeMode.LIGHT, "widget_4x2_light")
    @Test fun widget4x4Dark() = widget(DpSize(340.dp, 340.dp), ThemeMode.DARK, "widget_4x4_dark")
    @Test fun widget4x4Light() = widget(DpSize(340.dp, 340.dp), ThemeMode.LIGHT, "widget_4x4_light")
}
