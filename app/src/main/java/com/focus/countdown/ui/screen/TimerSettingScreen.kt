package com.focus.countdown.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.countdown.timer.TimerStateManager
import com.focus.countdown.ui.components.HourPicker
import com.focus.countdown.ui.components.MinutePicker

/**
 * 倒计时设置界面
 * 集成自定义滚轮组件，实现小时和分钟选择
 * 符合Focus的专注设计理念
 */
@Composable
fun TimerSettingScreen(
    onConfirm: (hours: Int, minutes: Int) -> Unit,
    onBack: () -> Unit = {},
    timerStateManager: TimerStateManager? = null,
    initialHours: Int = 0,
    initialMinutes: Int = 25
) {
    val context = LocalContext.current
    val stateManager = remember { 
        timerStateManager ?: TimerStateManager(context) 
    }
    
    // 获取实际屏幕尺寸（包括刘海区域）
    val displayMetrics = remember { context.resources.displayMetrics }
    val density = displayMetrics.density
    
    // 计算实际屏幕尺寸（像素转 dp，包括刘海区域）
    val screenWidth = (displayMetrics.widthPixels / density).dp
    val screenHeight = (displayMetrics.heightPixels / density).dp
    
    // 动态计算字体大小
    val baseDimension = minOf(screenWidth.value, screenHeight.value)
    val titleFontSize = (baseDimension * 0.045f).coerceIn(18f, 32f).sp
    val timeFontSize = (baseDimension * 0.04f).coerceIn(16f, 24f).sp
    val buttonFontSize = (baseDimension * 0.04f).coerceIn(16f, 22f).sp
    
    // 基于物理屏幕的垂直分布计算
    // 将整个屏幕高度分为几个区域，确保整体视觉居中
    val totalHeight = screenHeight.value
    
    // 顶部安全边距（考虑刘海区域）
    val topSafeMargin = (totalHeight * 0.08f).coerceAtMost(60f).dp
    
    // 底部按钮区域高度（预留足够空间）
    val buttonAreaHeight = (totalHeight * 0.12f).coerceIn(80f, 120f).dp
    
    // 滚轮区域高度 - 尽可能大，占据中间主要空间
    val pickerAreaHeight = (totalHeight * 0.55f).coerceIn(280f, 420f).dp
    
    // 中间可用空间
    val middleSpace = totalHeight.dp - topSafeMargin - buttonAreaHeight
    
    // 标题位置 - 在顶部安全区域下方
    val titleTopPadding = (totalHeight * 0.04f).coerceIn(8f, 20f).dp
    
    // 状态管理
    var selectedHours by remember { mutableStateOf(initialHours) }
    var selectedMinutes by remember { mutableStateOf(initialMinutes) }
    var isValidTime by remember { mutableStateOf(true) }
    
    // 输入验证逻辑
    val isValidInput: (hours: Int, minutes: Int) -> Boolean = { hours, minutes ->
        val totalMinutes = hours * 60 + minutes
        totalMinutes <= 24 * 60 && totalMinutes > 0
    }
    
    // 验证输入
    LaunchedEffect(selectedHours, selectedMinutes) {
        val totalMinutes = selectedHours * 60 + selectedMinutes
        isValidTime = totalMinutes > 0 && totalMinutes <= 24 * 60
    }
    
    // 主界面布局 - 基于整个物理屏幕
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部标题区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topSafeMargin + titleTopPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "设置倒计时",
                    color = Color.White,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    letterSpacing = 4.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            
            // 中间滚轮选择区域 - 占据主要空间
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                // 计算滚轮容器宽度（基于屏幕高度，确保长宽比合适）
                val pickerHeight = (screenHeight.value * 0.5f).coerceIn(200f, 360f).dp
                val pickerWidth = (pickerHeight.value * 0.55f).dp  // 更窄的宽度
                
                // 滚轮容器
                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 小时滚轮
                    Box(
                        modifier = Modifier
                            .height(pickerHeight)
                            .width(pickerWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        HourPicker(
                            modifier = Modifier.fillMaxSize(),
                            hour = selectedHours,
                            onHourChange = { newHour ->
                                selectedHours = newHour
                            }
                        )
                    }
                    
                    // 分隔符
                    Text(
                        text = ":",
                        color = Color.White,
                        fontSize = (baseDimension * 0.08f).coerceIn(24f, 40f).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    // 分钟滚轮
                    Box(
                        modifier = Modifier
                            .height(pickerHeight)
                            .width(pickerWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        MinutePicker(
                            modifier = Modifier.fillMaxSize(),
                            minute = selectedMinutes,
                            onMinuteChange = { newMinute ->
                                selectedMinutes = newMinute
                            }
                        )
                    }
                }
            }
            
            // 当前设置时间显示 - 移到滚轮下方
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${selectedHours.toString().padStart(2, '0')}时${selectedMinutes.toString().padStart(2, '0')}分",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = timeFontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    letterSpacing = 3.sp,
                    maxLines = 1
                )
            }
            
            // 底部按钮区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonAreaHeight),
                contentAlignment = Alignment.Center
            ) {
                // 使用自定义 Box 替代 Button，完全移除按压效果
                val isClickable = isValidTime
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height((screenHeight.value * 0.075f).coerceIn(48f, 64f).dp)
                        .background(
                            color = if (isClickable) Color.Black else Color.Black,
                            shape = RoundedCornerShape(28.dp)
                        )
                        .then(
                            if (isClickable) {
                                Modifier.clickable(
                                    indication = null,  // 移除涟漪效果
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (isValidInput(selectedHours, selectedMinutes)) {
                                        stateManager.startTimer(selectedHours * 60 + selectedMinutes)
                                        onConfirm(selectedHours, selectedMinutes)
                                    }
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "开始专注",
                        fontSize = buttonFontSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        maxLines = 1,
                        color = if (isClickable) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun TimerSettingScreenPreview() {
    TimerSettingScreen(
        onConfirm = { hours, minutes ->
            println("设置倒计时: ${hours}小时${minutes}分钟")
        },
        onBack = {
            println("返回上一页")
        },
        initialHours = 0,
        initialMinutes = 25
    )
}
