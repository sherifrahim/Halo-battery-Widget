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
import com.halo.btbattery.widget.WidgetRoot
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

    private fun app(theme: ThemeMode, name: String) {
        val vm = MainViewModel(app)
        compose.setContent { HaloTheme(theme) { HaloScreen(vm) } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$OUT/$name.png")
    }

    @Test fun appDark() = app(ThemeMode.DARK, "app_dark")
    @Test @Config(qualifiers = "w400dp-h880dp-notnight-xxhdpi") fun appLight() = app(ThemeMode.LIGHT, "app_light")

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun widget(size: DpSize, theme: ThemeMode, name: String): Unit = runBlocking {
        val settings = Settings(theme = theme)
        val result = GlanceRemoteViews().compose(app, size) { WidgetRoot(SampleData.snapshot, settings) }
        val host = FrameLayout(app)
        val view = result.remoteViews.apply(app, host)
        val density = app.resources.displayMetrics.density
        val w = (size.width.value * density).toInt()
        val h = (size.height.value * density).toInt()
        // Backdrop standing in for a wallpaper so the glass reads as glass.
        val frame = FrameLayout(app).apply {
            setBackgroundColor(if (theme == ThemeMode.DARK) 0xFF2A3550.toInt() else 0xFFB9C9E0.toInt())
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

    @Test fun widget2x2Dark() = widget(DpSize(170.dp, 170.dp), ThemeMode.DARK, "widget_2x2_dark")
    @Test fun widget4x1Dark() = widget(DpSize(340.dp, 80.dp), ThemeMode.DARK, "widget_4x1_dark")
    @Test fun widget4x2Dark() = widget(DpSize(340.dp, 170.dp), ThemeMode.DARK, "widget_4x2_dark")
    @Test fun widget4x2Light() = widget(DpSize(340.dp, 170.dp), ThemeMode.LIGHT, "widget_4x2_light")
    @Test fun widget4x4Dark() = widget(DpSize(340.dp, 340.dp), ThemeMode.DARK, "widget_4x4_dark")
    @Test fun widget4x4Light() = widget(DpSize(340.dp, 340.dp), ThemeMode.LIGHT, "widget_4x4_light")
}
