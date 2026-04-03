package com.focus.countdown.timer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * 倒计时状态枚举
 */
enum class TimerState {
    IDLE,        // 空闲状态 - 未开始倒计时
    RUNNING,     // 运行中 - 倒计时正在进行
    PAUSED,      // 暂停状态 - 倒计时已暂停
    COMPLETED    // 完成状态 - 倒计时结束
}

/**
 * 倒计时数据类
 */
data class TimerData(
    val totalTime: Long = 0L,           // 总时间（毫秒）
    val remainingTime: Long = 0L,       // 剩余时间（毫秒）
    val startTime: Long = 0L,           // 开始时间戳
    val endTime: Long = 0L,             // 结束时间戳
    val isRunning: Boolean = false,     // 是否正在运行
    val isPaused: Boolean = false       // 是否暂停
) {
    val progress: Float
        get() = if (totalTime > 0) {
            ((totalTime - remainingTime).toFloat() / totalTime.toFloat()).coerceIn(0f, 1f)
        } else 0f
    
    val isFinished: Boolean
        get() = remainingTime <= 0L
}

/**
 * SharedPreferences 键值定义
 */
object TimerPreferences {
    // 键名常量
    const val KEY_TIMER_STATE = "timer_state"
    const val KEY_TOTAL_TIME = "total_time"
    const val KEY_REMAINING_TIME = "remaining_time"
    const val KEY_START_TIME = "start_time"
    const val KEY_END_TIME = "end_time"
    const val KEY_IS_RUNNING = "is_running"
    const val KEY_IS_PAUSED = "is_paused"
    const val KEY_LAST_UPDATE = "last_update"
    
    // 默认值
    const val DEFAULT_TIMER_STATE = "IDLE"
    const val DEFAULT_TIME = 0L
    const val DEFAULT_BOOLEAN = false
}

/**
 * 改进的倒计时状态管理器
 * 增强了错误处理和初始化安全性
 */
class TimerStateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TimerStateManager"
        private const val PREF_NAME = "timer_preferences"
    }
    
    // SharedPreferences 实例 - 使用lazy延迟初始化
    private val preferences: SharedPreferences by lazy {
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SharedPreferences", e)
            // 返回一个空的SharedPreferences作为fallback
            context.getSharedPreferences("${PREF_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }
    
    // 当前倒计时状态
    var currentState: TimerState by mutableStateOf(TimerState.IDLE)
        private set
    
    // 当前倒计时数据
    var timerData: TimerData by mutableStateOf(TimerData())
        public set
    
    // 状态流，用于观察状态变化
    private val _stateFlow = MutableStateFlow(TimerState.IDLE)
    val stateFlow: StateFlow<TimerState> = _stateFlow.asStateFlow()
    
    // 数据流，用于观察数据变化
    private val _dataFlow = MutableStateFlow(TimerData())
    val dataFlow: StateFlow<TimerData> = _dataFlow.asStateFlow()
    
    // 协程作用域 - 使用SupervisorJob防止单个任务失败影响整个Scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 定时器任务
    private var timerJob: Job? = null
    
    // 状态变化回调
    private var onStateChangeListener: ((TimerState) -> Unit)? = null
    private var onTimerCompleteListener: (() -> Unit)? = null
    
    // 初始化状态标记
    private var isInitialized = false
    
    init {
        try {
            Log.d(TAG, "Initializing TimerStateManager")
            // 应用启动时恢复状态
            restoreState()
            isInitialized = true
            Log.d(TAG, "TimerStateManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TimerStateManager", e)
            // 初始化失败时使用默认状态
            resetTimer()
            isInitialized = true
        }
    }
    
    /**
     * 确保管理器已正确初始化
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            Log.w(TAG, "TimerStateManager not fully initialized, using default state")
            resetTimer()
            isInitialized = true
        }
    }
    
    /**
     * 开始倒计时
     */
    fun startTimer(totalMinutes: Int) {
        ensureInitialized()
        
        try {
            Log.d(TAG, "Starting timer for $totalMinutes minutes")
            val totalTime = totalMinutes * 60 * 1000L // 转换为毫秒
            val currentTime = System.currentTimeMillis()
            
            timerData = TimerData(
                totalTime = totalTime,
                remainingTime = totalTime,
                startTime = currentTime,
                endTime = currentTime + totalTime,
                isRunning = true,
                isPaused = false
            )
            
            currentState = TimerState.RUNNING
            updateStateFlow()
            
            // 开始计时器
            startTimerJob()
            
            // 保存状态
            saveState()
            
            Log.d(TAG, "Timer started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start timer", e)
            // 启动失败时重置状态
            resetTimer()
        }
    }
    
    /**
     * 暂停倒计时
     */
    fun pauseTimer() {
        ensureInitialized()
        
        try {
            if (currentState == TimerState.RUNNING) {
                timerData = timerData.copy(
                    isRunning = false,
                    isPaused = true
                )
                
                currentState = TimerState.PAUSED
                updateStateFlow()
                
                // 停止计时器
                stopTimerJob()
                
                // 保存状态
                saveState()
                
                Log.d(TAG, "Timer paused")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause timer", e)
        }
    }
    
    /**
     * 恢复倒计时
     */
    fun resumeTimer() {
        ensureInitialized()
        
        try {
            if (currentState == TimerState.PAUSED) {
                val currentTime = System.currentTimeMillis()
                val remainingTime = timerData.endTime - currentTime
                
                timerData = timerData.copy(
                    isRunning = true,
                    isPaused = false,
                    remainingTime = remainingTime.coerceAtLeast(0L)
                )
                
                currentState = TimerState.RUNNING
                updateStateFlow()
                
                // 重新开始计时器
                startTimerJob()
                
                // 保存状态
                saveState()
                
                Log.d(TAG, "Timer resumed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume timer", e)
        }
    }
    
    /**
     * 停止倒计时
     */
    fun stopTimer() {
        ensureInitialized()
        
        try {
            timerData = TimerData()
            currentState = TimerState.IDLE
            updateStateFlow()
            
            // 停止计时器
            stopTimerJob()
            
            // 清除保存的状态
            clearPreferences()
            
            Log.d(TAG, "Timer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop timer", e)
        }
    }
    
    /**
     * 重置倒计时
     */
    fun resetTimer() {
        try {
            timerData = TimerData()
            currentState = TimerState.IDLE
            updateStateFlow()
            
            // 停止计时器
            stopTimerJob()
            
            // 保存状态
            saveState()
            
            Log.d(TAG, "Timer reset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset timer", e)
        }
    }
    
    /**
     * 开始计时器协程任务
     */
    private fun startTimerJob() {
        stopTimerJob() // 确保没有重复的任务
        
        timerJob = scope.launch {
            try {
                while (timerData.isRunning && timerData.remainingTime > 0) {
                    val currentTime = System.currentTimeMillis()
                    val newRemainingTime = timerData.endTime - currentTime
                    
                    timerData = timerData.copy(
                        remainingTime = newRemainingTime.coerceAtLeast(0L)
                    )
                    
                    updateDataFlow()
                    
                    if (newRemainingTime <= 0) {
                        // 倒计时完成
                        onTimerComplete()
                        break
                    }
                    
                    delay(1000) // 每秒更新一次
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Timer job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in timer job", e)
            }
        }
    }
    
    /**
     * 停止计时器协程任务
     */
    private fun stopTimerJob() {
        timerJob?.cancel()
        timerJob = null
    }
    
    /**
     * 倒计时完成处理
     */
    private fun onTimerComplete() {
        try {
            timerData = timerData.copy(
                remainingTime = 0L,
                isRunning = false,
                isPaused = false
            )
            
            currentState = TimerState.COMPLETED
            updateStateFlow()
            
            // 停止计时器
            stopTimerJob()
            
            // 通知完成
            onTimerCompleteListener?.invoke()
            
            // 保存状态
            saveState()
            
            Log.d(TAG, "Timer completed")
            
            // 3秒后自动回到空闲状态
            scope.launch {
                try {
                    delay(3000)
                    resetTimer()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in auto-reset", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in timer completion", e)
        }
    }
    
    /**
     * 获取格式化的时间字符串 (MM:SS 或 HH:MM:SS)
     */
    fun getFormattedTime(): String {
        return try {
            val millis = timerData.remainingTime
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting time", e)
            "00:00"
        }
    }
    
    /**
     * 获取进度百分比
     */
    fun getProgressPercentage(): Float {
        return try {
            timerData.progress * 100f
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating progress", e)
            0f
        }
    }
    
    /**
     * 检查是否应该显示设置界面
     */
    fun shouldShowSetupScreen(): Boolean {
        return currentState == TimerState.IDLE
    }
    
    /**
     * 检查是否应该显示倒计时界面
     */
    fun shouldShowTimerScreen(): Boolean {
        return currentState == TimerState.RUNNING || currentState == TimerState.PAUSED
    }
    
    /**
     * 检查是否应该显示完成界面
     */
    fun shouldShowCompletedScreen(): Boolean {
        return currentState == TimerState.COMPLETED
    }
    
    /**
     * 设置状态变化监听器
     */
    fun setOnStateChangeListener(listener: (TimerState) -> Unit) {
        onStateChangeListener = listener
    }
    
    /**
     * 设置倒计时完成监听器
     */
    fun setOnTimerCompleteListener(listener: () -> Unit) {
        onTimerCompleteListener = listener
    }
    
    /**
     * 保存状态到 SharedPreferences
     */
    private fun saveState() {
        try {
            preferences.edit().apply {
                putString(TimerPreferences.KEY_TIMER_STATE, currentState.name)
                putLong(TimerPreferences.KEY_TOTAL_TIME, timerData.totalTime)
                putLong(TimerPreferences.KEY_REMAINING_TIME, timerData.remainingTime)
                putLong(TimerPreferences.KEY_START_TIME, timerData.startTime)
                putLong(TimerPreferences.KEY_END_TIME, timerData.endTime)
                putBoolean(TimerPreferences.KEY_IS_RUNNING, timerData.isRunning)
                putBoolean(TimerPreferences.KEY_IS_PAUSED, timerData.isPaused)
                putLong(TimerPreferences.KEY_LAST_UPDATE, System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state", e)
        }
    }
    
    /**
     * 从 SharedPreferences 恢复状态
     */
    private fun restoreState() {
        try {
            val savedState = preferences.getString(TimerPreferences.KEY_TIMER_STATE, TimerPreferences.DEFAULT_TIMER_STATE)
            val totalTime = preferences.getLong(TimerPreferences.KEY_TOTAL_TIME, TimerPreferences.DEFAULT_TIME)
            val remainingTime = preferences.getLong(TimerPreferences.KEY_REMAINING_TIME, TimerPreferences.DEFAULT_TIME)
            val startTime = preferences.getLong(TimerPreferences.KEY_START_TIME, TimerPreferences.DEFAULT_TIME)
            val endTime = preferences.getLong(TimerPreferences.KEY_END_TIME, TimerPreferences.DEFAULT_TIME)
            val isRunning = preferences.getBoolean(TimerPreferences.KEY_IS_RUNNING, TimerPreferences.DEFAULT_BOOLEAN)
            val isPaused = preferences.getBoolean(TimerPreferences.KEY_IS_PAUSED, TimerPreferences.DEFAULT_BOOLEAN)
            val lastUpdate = preferences.getLong(TimerPreferences.KEY_LAST_UPDATE, TimerPreferences.DEFAULT_TIME)
            
            try {
                currentState = TimerState.valueOf(savedState ?: TimerPreferences.DEFAULT_TIMER_STATE)
                
                timerData = TimerData(
                    totalTime = totalTime,
                    remainingTime = remainingTime,
                    startTime = startTime,
                    endTime = endTime,
                    isRunning = isRunning,
                    isPaused = isPaused
                )
                
                updateStateFlow()
                updateDataFlow()
                
                // 如果状态是运行中，重新启动计时器
                if (currentState == TimerState.RUNNING && isRunning) {
                    // 检查是否超时，如果超时则标记为完成
                    val currentTime = System.currentTimeMillis()
                    val newRemainingTime = endTime - currentTime
                    
                    if (newRemainingTime <= 0) {
                        onTimerComplete()
                    } else {
                        timerData = timerData.copy(remainingTime = newRemainingTime)
                        startTimerJob()
                    }
                }
                
                // 关键修复：如果是已完成状态（倒计时在后台结束）
                // 重新打开应用时直接进入设置界面，而不是卡在"完成!"界面
                if (currentState == TimerState.COMPLETED) {
                    Log.d(TAG, "Timer was completed in background, resetting to IDLE")
                    resetTimer()
                    return
                }
                
                Log.d(TAG, "State restored successfully: $currentState")
            } catch (e: IllegalArgumentException) {
                // 如果状态值无效，重置为默认状态
                Log.w(TAG, "Invalid state value, resetting to default", e)
                resetTimer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore state", e)
            resetTimer()
        }
    }
    
    /**
     * 清除 SharedPreferences 中的数据
     */
    private fun clearPreferences() {
        try {
            preferences.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear preferences", e)
        }
    }
    
    /**
     * 更新状态流
     */
    private fun updateStateFlow() {
        try {
            _stateFlow.value = currentState
            onStateChangeListener?.invoke(currentState)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update state flow", e)
        }
    }
    
    /**
     * 更新数据流
     */
    private fun updateDataFlow() {
        try {
            _dataFlow.value = timerData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update data flow", e)
        }
    }
    
    /**
     * 更新计时器数据
     */
    fun updateTimerData(remainingTime: Long) {
        try {
            timerData = timerData.copy(remainingTime = remainingTime)
            updateDataFlow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update timer data", e)
        }
    }
    
    /**
     * 更新计时器状态
     */
    fun updateTimerState(newState: TimerState) {
        try {
            currentState = newState
            updateStateFlow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update timer state", e)
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            Log.d(TAG, "Cleaning up TimerStateManager")
            stopTimerJob()
            scope.cancel()
            Log.d(TAG, "TimerStateManager cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * 检查应用是否在倒计时过程中被销毁并重新创建
     */
    fun handleConfigurationChange() {
        try {
            // 在配置变更时，重新计算剩余时间
            if (currentState == TimerState.RUNNING && timerData.isRunning) {
                val currentTime = System.currentTimeMillis()
                val newRemainingTime = timerData.endTime - currentTime
                
                if (newRemainingTime <= 0) {
                    onTimerComplete()
                } else {
                    timerData = timerData.copy(remainingTime = newRemainingTime)
                    updateDataFlow()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling configuration change", e)
        }
    }
}