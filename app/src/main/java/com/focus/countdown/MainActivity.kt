package com.focus.countdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.focus.countdown.timer.TimerData
import com.focus.countdown.timer.TimerStateManager
import com.focus.countdown.ui.screen.TimerSettingScreen
import com.focus.countdown.ui.theme.FocusCountdownTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 时钟显示模式枚举
 */
enum class ClockMode {
    CLOCK,        // 普通时钟模式
    COUNTDOWN,    // 倒计时模式
    COMPLETED     // 完成模式
}

/**
 * 显示模式
 */
enum class DisplayMode {
    HOURS_MINUTES_SECONDS,
    MINUTES_SECONDS,
    COMPLETED
}

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var timerStateManager: TimerStateManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // 设置真正的全屏 - 允许绘制到刘海区域
            setupTrueFullscreen()
            
            timerStateManager = TimerStateManager(this)
            
            setContent {
                var hasError by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf("") }
                
                LaunchedEffect(Unit) {
                    try {
                        timerStateManager = TimerStateManager(this@MainActivity)
                    } catch (e: Exception) {
                        hasError = true
                        errorMessage = e.message ?: "Unknown error"
                    }
                }
                
                if (hasError) {
                    ErrorFallbackScreen("应用初始化失败: $errorMessage")
                } else {
                    FocusCountdownTheme {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MainScreen()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            setContent {
                ErrorFallbackScreen("应用初始化失败: ${e.message}")
            }
        }
    }
    
    private fun setupTrueFullscreen() {
        // 1. 允许内容延伸到刘海区域
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        
        // 2. 内容不被系统栏限制
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 3. 隐藏状态栏和导航栏
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // 4. 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 5. 窗口扩展到屏幕边界
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }
    
    override fun onResume() {
        super.onResume()
        // 重新应用全屏设置
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun ErrorFallbackScreen(errorMessage: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = "应用遇到错误\n$errorMessage\n\n请重新启动应用",
            color = Color.Red,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var errorState by remember { mutableStateOf<String?>(null) }
    
    val timerStateManager = remember {
        try {
            TimerStateManager(context)
        } catch (e: Exception) {
            errorState = "初始化失败: ${e.message}"
            null
        }
    }
    
    errorState?.let { error ->
        ErrorFallbackScreen(error)
        return
    }
    
    timerStateManager?.let { manager ->
        val currentState by manager.stateFlow.collectAsState()
        val timerData by manager.dataFlow.collectAsState()
        
        when {
            manager.shouldShowTimerScreen() -> {
                ClockScreen(
                    mode = ClockMode.COUNTDOWN,
                    timerStateManager = manager,
                    timerData = timerData
                )
            }
            manager.shouldShowCompletedScreen() -> {
                ClockScreen(
                    mode = ClockMode.COMPLETED,
                    timerStateManager = manager,
                    timerData = timerData
                )
            }
            else -> {
                TimerSettingScreen(
                    onConfirm = { hours, minutes ->
                        val totalMinutes = hours * 60 + minutes
                        manager.startTimer(totalMinutes)
                    },
                    onBack = { },
                    timerStateManager = manager,
                    initialHours = 0,
                    initialMinutes = 25
                )
            }
        }
    } ?: ErrorFallbackScreen("状态管理器初始化失败")
}

/**
 * 倒计时显示组件 - 使用 SurfaceView 实现真正的全屏
 */
@Composable
fun ClockScreen(
    mode: ClockMode = ClockMode.CLOCK,
    timerStateManager: TimerStateManager? = null,
    timerData: TimerData = TimerData()
) {
    var timeString by remember { mutableStateOf("00:00") }
    var displayMode by remember { mutableStateOf(DisplayMode.MINUTES_SECONDS) }
    var fontSize by remember { mutableStateOf(200f) }

    DisposableEffect(Unit) {
        val job = try {
            when (mode) {
                ClockMode.CLOCK -> {
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    MainScope().launch {
                        while (true) {
                            timeString = timeFormat.format(Date())
                            displayMode = DisplayMode.HOURS_MINUTES_SECONDS
                            delay(1000)
                        }
                    }
                }
                ClockMode.COUNTDOWN -> {
                    MainScope().launch {
                        while (true) {
                            val formatted = timerStateManager?.getFormattedTime() ?: "00:00"
                            timeString = formatted
                            displayMode = if (formatted.split(":").size == 3) {
                                DisplayMode.HOURS_MINUTES_SECONDS
                            } else {
                                DisplayMode.MINUTES_SECONDS
                            }
                            delay(100)
                        }
                    }
                }
                ClockMode.COMPLETED -> {
                    displayMode = DisplayMode.COMPLETED
                    MainScope().launch { }
                }
            }
        } catch (e: Exception) {
            MainScope().launch { }
        }

        onDispose { job.cancel() }
    }

    // 使用 AndroidView 包装 SurfaceView 实现真正的全屏绘制
    AndroidView(
        factory = { context ->
            FullscreenClockView(context).apply {
                this.timeString = timeString
                this.displayMode = displayMode
            }
        },
        update = { view ->
            view.timeString = timeString
            view.displayMode = displayMode
            view.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 真正的全屏时钟视图 - 使用 SurfaceView 绕过 Compose 布局限制
 */
class FullscreenClockView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    
    var timeString: String = "00:00"
    var displayMode: DisplayMode = DisplayMode.MINUTES_SECONDS
    
    private val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = android.graphics.Color.WHITE
    }
    
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    
    init {
        holder.addCallback(this)
        // 设置透明背景，让黑色背景透过
        setBackgroundColor(android.graphics.Color.BLACK)
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceWidth = width
        surfaceHeight = height
        draw()
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        draw()
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {}
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOnCanvas(canvas)
    }
    
    private fun draw() {
        if (holder.surface.isValid) {
            val canvas = holder.lockCanvas()
            canvas?.let {
                try {
                    drawOnCanvas(it)
                } finally {
                    holder.unlockCanvasAndPost(it)
                }
            }
        }
    }
    
    private fun drawOnCanvas(canvas: Canvas) {
        // 清除画布
        canvas.drawColor(android.graphics.Color.BLACK)
        
        val width = surfaceWidth.toFloat()
        val height = surfaceHeight.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        
        // 计算字体大小（基于屏幕高度）
        val fontSize = when (displayMode) {
            DisplayMode.HOURS_MINUTES_SECONDS -> height * 0.45f
            DisplayMode.MINUTES_SECONDS -> height * 0.6f
            DisplayMode.COMPLETED -> height * 0.3f
        }.coerceAtMost(width * 0.18f)
        
        paint.textSize = fontSize
        
        // 字符宽度（等宽）
        val charWidth = fontSize * 0.6f
        
        // 解析并绘制
        when (displayMode) {
            DisplayMode.HOURS_MINUTES_SECONDS -> {
                val parts = timeString.split(":")
                if (parts.size == 3) {
                    val chars = listOf(
                        parts[0][0], parts[0][1], ':',
                        parts[1][0], parts[1][1], ':',
                        parts[2][0], parts[2][1]
                    )
                    val totalWidth = 8 * charWidth
                    val startX = centerX - totalWidth / 2f
                    
                    chars.forEachIndexed { index, char ->
                        val x = startX + index * charWidth + charWidth / 2f
                        val metrics = paint.fontMetrics
                        val baseline = centerY - metrics.ascent - (metrics.descent - metrics.ascent) / 2f
                        canvas.drawText(char.toString(), x, baseline, paint)
                    }
                }
            }
            DisplayMode.MINUTES_SECONDS -> {
                val parts = timeString.split(":")
                if (parts.size == 2) {
                    val chars = listOf(
                        parts[0][0], parts[0][1], ':',
                        parts[1][0], parts[1][1]
                    )
                    val totalWidth = 5 * charWidth
                    val startX = centerX - totalWidth / 2f
                    
                    chars.forEachIndexed { index, char ->
                        val x = startX + index * charWidth + charWidth / 2f
                        val metrics = paint.fontMetrics
                        val baseline = centerY - metrics.ascent - (metrics.descent - metrics.ascent) / 2f
                        canvas.drawText(char.toString(), x, baseline, paint)
                    }
                }
            }
            DisplayMode.COMPLETED -> {
                // "完成!" 使用更大的字符间距
                val completedCharWidth = fontSize * 0.9f  // 中文字符需要更宽的位置
                val chars = listOf('完', '成', '!')
                val totalWidth = 3 * completedCharWidth
                val startX = centerX - totalWidth / 2f
                
                chars.forEachIndexed { index, char ->
                    val x = startX + index * completedCharWidth + completedCharWidth / 2f
                    val metrics = paint.fontMetrics
                    val baseline = centerY - metrics.ascent - (metrics.descent - metrics.ascent) / 2f
                    canvas.drawText(char.toString(), x, baseline, paint)
                }
            }
        }
    }
    
    override fun invalidate() {
        super.invalidate()
        draw()
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    FocusCountdownTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = "12:34:56",
                color = Color.White,
                fontSize = 48.sp
            )
        }
    }
}
