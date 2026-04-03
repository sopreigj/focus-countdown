package com.focus.countdown.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 3D滚轮选择器组件 - 修复初始位置版
 * 
 * 核心修复：使用 BoxWithConstraints 在布局阶段获取容器高度，
 * 确保初始偏移计算正确
 */
@Composable
fun WheelPicker(
    modifier: Modifier = Modifier,
    startValue: Int = 0,
    maxValue: Int,
    label: String,
    onValueChange: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 基本尺寸
    val itemHeight = with(density) { 70.dp.toPx() }
    val cycle = maxValue + 1
    
    // 5倍列表
    val values = remember(cycle) {
        List(cycle * 5) { it % cycle }
    }
    
    // 使用 BoxWithConstraints 获取容器尺寸
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 在这里计算容器高度和视觉中心
        val containerHeight = with(density) { maxHeight.toPx() }
        val visualCenter = containerHeight / 2f
        
        // 初始项索引（第3个周期 + startValue）
        val initialItemIndex = cycle * 2 + startValue.coerceIn(0, maxValue)
        
        // 初始偏移：让 initialItemIndex 位于视觉中心
        // 公式：列表偏移 = 视觉中心 - (索引 * 项高度) - 半项高度
        val initialOffset = visualCenter - (initialItemIndex * itemHeight) - itemHeight / 2f
        
        // 滚动偏移状态
        var scrollOffset by remember { mutableFloatStateOf(initialOffset) }
        val animatedOffset = remember { Animatable(initialOffset) }
        
        // 同步动画值到状态
        LaunchedEffect(animatedOffset.value) {
            scrollOffset = animatedOffset.value
        }
        
        // 计算当前选中的值
        fun computeSelectedValue(): Int {
            if (itemHeight <= 0 || containerHeight <= 0) return startValue
            val centerItemFloat = (visualCenter - scrollOffset - itemHeight / 2) / itemHeight
            val centerItemIndex = centerItemFloat.roundToInt().coerceIn(0, values.size - 1)
            return values[centerItemIndex]
        }
        
        // 获取当前值并回调
        val currentValue = computeSelectedValue()
        LaunchedEffect(currentValue) {
            onValueChange(currentValue)
        }
        
        // 触摸处理
        val velocityTracker = remember { VelocityTracker() }
        
        // 画笔
        val paint = remember {
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
        }
        
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                scope.launch { animatedOffset.stop() }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch {
                                    animatedOffset.snapTo(animatedOffset.value + dragAmount)
                                }
                            },
                            onDragEnd = {
                                val velocity = velocityTracker.calculateVelocity().y
                                
                                scope.launch {
                                    // 计算当前选中的索引
                                    val currentCenterItem = (visualCenter - animatedOffset.value - itemHeight / 2) / itemHeight
                                    var targetIndex = currentCenterItem.roundToInt()
                                    
                                    // 速度影响
                                    if (abs(velocity) > 500f) {
                                        targetIndex -= (velocity / 500f).toInt()
                                    }
                                    
                                    // 限制范围并循环
                                    targetIndex = targetIndex.coerceIn(cycle, cycle * 4 - 1)
                                    val normalizedIndex = targetIndex % cycle
                                    if (targetIndex < cycle * 2 || targetIndex >= cycle * 3) {
                                        targetIndex = cycle * 2 + normalizedIndex
                                    }
                                    
                                    // 计算目标偏移
                                    val targetOffset = visualCenter - (targetIndex * itemHeight) - itemHeight / 2
                                    
                                    animatedOffset.animateTo(
                                        targetValue = targetOffset,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    val currentCenterItem = (visualCenter - animatedOffset.value - itemHeight / 2) / itemHeight
                                    val targetIndex = currentCenterItem.roundToInt().coerceIn(cycle, cycle * 4 - 1)
                                    val targetOffset = visualCenter - (targetIndex * itemHeight) - itemHeight / 2
                                    animatedOffset.animateTo(targetOffset, tween(200))
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasHeight = size.height
                    val canvasCenterY = canvasHeight / 2f
                    val centerX = size.width / 2f
                    
                    // 定义可视范围：选中项上下各1.5个项高度
                    // 即总共显示3个项（上、中、下）
                    val visibleRange = itemHeight * 1.5f
                    
                    // 绘制所有项 - 只绘制在可视范围内的
                    values.forEachIndexed { index, value ->
                        val itemCenterY = scrollOffset + (index * itemHeight) + itemHeight / 2
                        val distanceFromCenter = itemCenterY - canvasCenterY
                        val absDistance = abs(distanceFromCenter)
                        
                        // 严格限制绘制范围：只绘制可视范围内的项
                        if (absDistance <= visibleRange) {
                            // 距离进度 (0 = 中心选中项, 1 = 边缘)
                            val progress = (absDistance / itemHeight).coerceIn(0f, 1f)
                            val isSelected = absDistance < itemHeight * 0.4f
                            
                            // 缩放：选中项1.0，边缘项0.5
                            val scale = if (isSelected) 1f else (0.85f - progress * 0.35f).coerceAtLeast(0.5f)
                            // 透明度：选中项1.0，边缘项0.2
                            val alpha = if (isSelected) 1f else (0.85f - progress * 0.65f).coerceAtLeast(0.2f)
                            
                            // 3D透视偏移 - 限制在小范围避免转一圈
                            val perspective = when {
                                distanceFromCenter < -itemHeight * 0.25f -> -6f * progress
                                distanceFromCenter > itemHeight * 0.25f -> 6f * progress
                                else -> 0f
                            }
                            
                            val fontSize = (if (isSelected) 54f else 26f) * scale * density.density
                            
                            drawIntoCanvas { canvas ->
                                val text = String.format("%02d", value)
                                val yPos = itemCenterY + perspective * density.density
                                
                                paint.apply {
                                    this.textSize = fontSize
                                    this.color = android.graphics.Color.argb(
                                        (255 * alpha).toInt(), 255, 255, 255
                                    )
                                    this.isFakeBoldText = isSelected
                                }
                                
                                val metrics = paint.fontMetrics
                                val textHeight = metrics.descent - metrics.ascent
                                val baseline = yPos - metrics.ascent - textHeight / 2f
                                
                                canvas.nativeCanvas.drawText(text, centerX, baseline, paint)
                            }
                        }
                    }
                    
                    // 选中区域指示线（两条线之间是选中区域）
                    val selectorHalfHeight = itemHeight / 2f
                    // 减小边距让选中线更靠近内容，看起来更紧凑
                    val lineMargin = centerX * 0.35f
                    
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(lineMargin, canvasCenterY - selectorHalfHeight),
                        end = Offset(size.width - lineMargin, canvasCenterY - selectorHalfHeight),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(lineMargin, canvasCenterY + selectorHalfHeight),
                        end = Offset(size.width - lineMargin, canvasCenterY + selectorHalfHeight),
                        strokeWidth = 1.5f
                    )
                    
                    // 动态遮罩 - 基于可视范围计算
                    // 可视区域：canvasCenterY ± visibleRange
                    // 遮罩从可视区域边缘开始
                    val maskTopEnd = canvasCenterY - visibleRange  // 遮罩结束位置（顶部渐变终点）
                    val maskBottomStart = canvasCenterY + visibleRange  // 遮罩开始位置（底部渐变起点）
                    
                    // 顶部遮罩：从屏幕顶部到 maskTopEnd
                    if (maskTopEnd > 0) {
                        // 纯黑色遮住最顶部
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, maskTopEnd * 0.5f)
                        )
                        // 渐变过渡到可视区域
                        if (maskTopEnd * 0.5f < maskTopEnd) {
                            drawFade(maskTopEnd * 0.5f, maskTopEnd, Color.Black, Color.Transparent)
                        }
                    }
                    
                    // 底部遮罩：从 maskBottomStart 到屏幕底部
                    if (maskBottomStart < canvasHeight) {
                        val remainingSpace = canvasHeight - maskBottomStart
                        // 渐变从可视区域开始
                        if (remainingSpace * 0.5f > 0) {
                            drawFade(maskBottomStart, maskBottomStart + remainingSpace * 0.5f, Color.Transparent, Color.Black)
                        }
                        // 纯黑色遮住最底部
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(0f, maskBottomStart + remainingSpace * 0.5f),
                            size = Size(size.width, remainingSpace * 0.5f)
                        )
                    }
                }
            }
            
            // 标签已移除，因为下方有倒计时时间显示
            // Spacer(modifier = Modifier.width(8.dp))
            // Text(
            //     text = label,
            //     color = Color.White.copy(alpha = 0.85f),
            //     fontSize = 18.sp,
            //     fontWeight = FontWeight.Medium,
            //     letterSpacing = 2.sp
            // )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFade(
    startY: Float, endY: Float, startColor: Color, endColor: Color
) {
    val height = endY - startY
    if (height <= 0) return
    
    val steps = 30
    for (i in 0 until steps) {
        val ratio = i / (steps - 1f)
        val y = startY + ratio * height
        val color = Color(
            red = startColor.red * (1 - ratio) + endColor.red * ratio,
            green = startColor.green * (1 - ratio) + endColor.green * ratio,
            blue = startColor.blue * (1 - ratio) + endColor.blue * ratio,
            alpha = startColor.alpha * (1 - ratio) + endColor.alpha * ratio
        )
        drawRect(color, topLeft = Offset(0f, y), size = Size(size.width, height / steps))
    }
}

@Composable
fun HourPicker(
    modifier: Modifier = Modifier,
    hour: Int,
    onHourChange: (Int) -> Unit
) {
    WheelPicker(
        modifier = modifier,
        startValue = hour,
        maxValue = 23,
        label = "时",
        onValueChange = onHourChange
    )
}

@Composable
fun MinutePicker(
    modifier: Modifier = Modifier,
    minute: Int,
    onMinuteChange: (Int) -> Unit
) {
    WheelPicker(
        modifier = modifier,
        startValue = minute,
        maxValue = 59,
        label = "分",
        onValueChange = onMinuteChange
    )
}

@Composable
fun TimePicker(
    modifier: Modifier = Modifier,
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HourPicker(
            modifier = Modifier.weight(1f),
            hour = hour,
            onHourChange = { newHour -> onTimeChange(newHour, minute) }
        )
        
        Text(
            text = ":",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        MinutePicker(
            modifier = Modifier.weight(1f),
            minute = minute,
            onMinuteChange = { newMinute -> onTimeChange(hour, newMinute) }
        )
    }
}
