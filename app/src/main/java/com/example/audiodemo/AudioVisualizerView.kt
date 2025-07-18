package com.example.audiodemo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 自定义视图，用于显示音频可视化效果
 * 支持波形和频谱两种可视化模式
 * 增强版：添加平滑动画、渐变色和更多视觉效果
 */
class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_BYTES_COUNT = 128 // FFT数据点数
        private const val BAR_MAX_POINTS = 128 // 最大柱状图数量
        private const val ANIMATION_DURATION = 150 // 动画时长（毫秒）
    }

    // 波形绘制参数
    private val waveformPaint = Paint().apply {
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    // 频谱绘制参数
    private val fftPaint = Paint().apply {
        isAntiAlias = true
    }
    
    // 背景渐变画笔
    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
    }
    
    // 波形路径
    private val waveformPath = Path()
    
    // 圆形波形路径
    private val circleWavePath = Path()
    
    // 原始数据
    private var bytes = ByteArray(DEFAULT_BYTES_COUNT) // 存储音频数据
    private var fftBytes = ByteArray(DEFAULT_BYTES_COUNT) // 存储FFT频谱数据
    
    // 动画数据
    private var animatedBytes = FloatArray(DEFAULT_BYTES_COUNT) // 波形动画数据
    private var animatedFft = FloatArray(DEFAULT_BYTES_COUNT) // 频谱动画数据
    private var previousFft = FloatArray(DEFAULT_BYTES_COUNT) // 上一帧频谱数据
    private var globalAngle = 0f // 全局旋转角度
    
    // 动画器
    private var animator: ValueAnimator? = null
    private var rotateAnimator: ValueAnimator? = null
    
    private var drawingMode = DrawingMode.BOTH // 默认同时显示波形和频谱

    // 频谱柱状图参数
    private val barWidth = 4f // 频谱柱状图宽度
    private val barSpace = 1f // 频谱柱状图间距
    
    // 颜色设置
    private val waveformStartColor = Color.parseColor("#4CAF50") // 波形起始颜色
    private val waveformEndColor = Color.parseColor("#00BCD4") // 波形结束颜色
    private val fftStartColor = Color.parseColor("#FF5722") // 频谱起始颜色
    private val fftEndColor = Color.parseColor("#FFEB3B") // 频谱结束颜色
    private val bgGradientColors = intArrayOf(
        Color.parseColor("#00574B"),
        Color.parseColor("#003840"),
        Color.parseColor("#002030")
    )

    enum class DrawingMode {
        WAVEFORM, // 只显示波形
        FFT,      // 只显示频谱
        BOTH      // 同时显示波形和频谱
    }

    init {
        // 初始化动画数据
        for (i in animatedBytes.indices) {
            animatedBytes[i] = 0f
            animatedFft[i] = 0f
            previousFft[i] = 0f
        }
        
        // 启动旋转动画
        startRotateAnimation()
    }

    /**
     * 更新波形数据
     */
    fun updateWaveform(data: ByteArray) {
        bytes = data
        startAnimations()
    }

    /**
     * 更新FFT频谱数据
     */
    fun updateFft(data: ByteArray) {
        fftBytes = data
        startAnimations()
    }

    /**
     * 设置绘制模式
     */
    fun setDrawingMode(mode: DrawingMode) {
        drawingMode = mode
        invalidate()
    }
    
    /**
     * 启动平滑动画
     */
    private fun startAnimations() {
        animator?.cancel()
        
        for (i in bytes.indices.take(animatedBytes.size)) {
            previousFft[i] = animatedFft[i]
        }
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                
                // 更新波形动画数据
                for (i in bytes.indices.take(animatedBytes.size)) {
                    val target = bytes[i].toFloat()
                    animatedBytes[i] = animatedBytes[i] + (target - animatedBytes[i]) * fraction
                }
                
                // 更新频谱动画数据
                for (i in fftBytes.indices.take(animatedFft.size)) {
                    val target = Math.abs(fftBytes[i].toInt()) / 255.0f
                    animatedFft[i] = previousFft[i] + (target - previousFft[i]) * fraction
                }
                
                invalidate()
            }
            start()
        }
    }
    
    /**
     * 启动旋转动画
     */
    private fun startRotateAnimation() {
        rotateAnimator?.cancel()
        
        rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 10000 // 10秒旋转一周
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                globalAngle = animation.animatedValue as Float
                // 只有在有音频数据时才刷新视图
                if (bytes.isNotEmpty() && fftBytes.isNotEmpty()) {
                    invalidate()
                }
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        
        // 绘制渐变背景
        drawBackground(canvas, width, height)
        
        // 根据模式绘制不同效果
        when (drawingMode) {
            DrawingMode.WAVEFORM -> {
                drawCircleWave(canvas, centerX, centerY, min(width, height) / 2)
                drawWaveform(canvas, width, height, centerY)
            }
            DrawingMode.FFT -> drawFFT(canvas, width, height)
            DrawingMode.BOTH -> {
                drawCircleWave(canvas, centerX, centerY, min(width, height) / 3)
                drawFFT(canvas, width, height)
            }
        }
    }
    
    /**
     * 绘制炫酷背景
     */
    private fun drawBackground(canvas: Canvas, width: Float, height: Float) {
        // 创建径向渐变背景
        val gradient = RadialGradient(
            width / 2, height / 2,
            width * 0.8f,
            bgGradientColors,
            null,
            Shader.TileMode.CLAMP
        )
        
        backgroundPaint.shader = gradient
        canvas.drawRect(0f, 0f, width, height, backgroundPaint)
    }
    
    /**
     * 绘制圆形波浪
     */
    private fun drawCircleWave(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        if (bytes.isEmpty()) return
        
        // 设置波形渐变色
        waveformPaint.shader = LinearGradient(
            centerX - radius, centerY,
            centerX + radius, centerY,
            waveformStartColor, waveformEndColor,
            Shader.TileMode.CLAMP
        )
        
        canvas.save()
        canvas.rotate(globalAngle, centerX, centerY)
        
        circleWavePath.reset()
        val angleStep = 360f / min(64, bytes.size)
        var firstPoint = true
        
        for (i in 0 until min(64, bytes.size)) {
            val magnitude = 0.3f + (animatedBytes[i] / 128f) * 0.7f
            val angle = i * angleStep
            val x = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * radius * magnitude
            val y = centerY + sin(Math.toRadians(angle.toDouble())).toFloat() * radius * magnitude
            
            if (firstPoint) {
                circleWavePath.moveTo(x, y)
                firstPoint = false
            } else {
                circleWavePath.lineTo(x, y)
            }
        }
        
        circleWavePath.close()
        canvas.drawPath(circleWavePath, waveformPaint)
        canvas.restore()
    }

    private fun drawWaveform(canvas: Canvas, width: Float, height: Float, centerY: Float) {
        if (bytes.isEmpty()) return
        
        // 设置波形渐变色
        waveformPaint.shader = LinearGradient(
            0f, 0f, width, 0f,
            waveformStartColor, waveformEndColor,
            Shader.TileMode.CLAMP
        )
        
        val barStep = width / bytes.size
        waveformPath.reset()
        var firstPoint = true
        
        // 绘制波形路径
        for (i in bytes.indices.take(animatedBytes.size)) {
            val x = i * barStep
            // 将byte值映射到视图高度
            val amplitude = animatedBytes[i] / 128.0f
            val y = centerY + amplitude * (height / 3)
            
            if (firstPoint) {
                waveformPath.moveTo(x, y)
                firstPoint = false
            } else {
                waveformPath.lineTo(x, y)
            }
        }
        
        canvas.drawPath(waveformPath, waveformPaint)
        
        // 绘制对称波形（下半部分）
        waveformPath.reset()
        firstPoint = true
        
        for (i in bytes.indices.take(animatedBytes.size)) {
            val x = i * barStep
            val amplitude = animatedBytes[i] / 128.0f
            val y = centerY - amplitude * (height / 3)
            
            if (firstPoint) {
                waveformPath.moveTo(x, y)
                firstPoint = false
            } else {
                waveformPath.lineTo(x, y)
            }
        }
        
        canvas.drawPath(waveformPath, waveformPaint)
    }

    private fun drawFFT(canvas: Canvas, width: Float, height: Float, startY: Float = 0f) {
        if (fftBytes.isEmpty()) return
        
        val usableWidth = width.toInt()
        val barCount = min(fftBytes.size, BAR_MAX_POINTS)
        val barStep = usableWidth / barCount
        val actualBarWidth = min(barWidth, barStep - barSpace)
        
        // 设置频谱渐变色
        fftPaint.shader = LinearGradient(
            0f, startY + height, 0f, startY,
            fftStartColor, fftEndColor,
            Shader.TileMode.CLAMP
        )
        
        for (i in 0 until barCount) {
            // 平滑动画效果
            val magnitude = animatedFft[i]
            
            // 将FFT值映射到视图高度，并添加指数放大效果以增强低振幅信号可见度
            val barHeight = Math.pow(magnitude.toDouble(), 1.5).toFloat() * height
            
            // 计算柱状图的位置
            val left = i * barStep + barSpace.toFloat()
            val top = startY + height - barHeight
            val right = left + actualBarWidth
            val bottom = startY + height
            
            // 绘制圆角柱形
            canvas.drawRoundRect(
                left, top, right, bottom,
                3f, 3f, fftPaint
            )
        }
    }

    /**
     * 设置波形颜色
     */
    fun setWaveformColors(startColor: Int, endColor: Int) {
        invalidate()
    }

    /**
     * 设置频谱颜色
     */
    fun setFftColors(startColor: Int, endColor: Int) {
        invalidate()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        animator?.cancel()
        rotateAnimator?.cancel()
        animator = null
        rotateAnimator = null
    }
} 