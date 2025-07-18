package com.example.audiodemo

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 音频可视化管理类
 * 负责连接MediaPlayer和AudioVisualizerView
 */
class AudioVisualizer(
    private val mediaPlayer: MediaPlayer,
    private val visualizerView: AudioVisualizerView,
    private val currentSongTitle: String = "unknown", // 当前播放的歌曲名称
    private val context: Context // 添加Context参数
) {
    companion object {
        private const val TAG = "AudioVisualizer"
        private const val CAPTURE_SIZE = 512 // 必须是2的幂次
    }

    private var visualizer: Visualizer? = null
    private var isSetup = false
    
    // 音频数据存储相关
    private var isRecording = false
    private var dataFile: File? = null
    private var dataOutputStream: FileOutputStream? = null
    private val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    
    /**
     * 初始化并启动可视化器
     * 需要在MediaPlayer准备好后调用
     */
    fun setupAndStart() {
        try {
            Log.d(TAG, "设置并启动可视化器")
            // 获取当前音频会话ID
            val audioSessionId = mediaPlayer.audioSessionId
            if (audioSessionId == -1) {
                Log.e(TAG, "无效的音频会话ID")
                return
            }
            
            // 如果已经初始化过，先释放之前的资源
            visualizer?.release()
            
            // 创建新的可视化器
            visualizer = Visualizer(audioSessionId).apply {
                enabled = false // 先禁用，设置好后再启用
                
                // 设置捕获大小
                captureSize = CAPTURE_SIZE
                
                // 设置数据捕获监听器
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer,
                            waveform: ByteArray,
                            samplingRate: Int
                        ) {
                            // 更新波形数据
                            visualizerView.updateWaveform(waveform)
                            
                            // 保存波形数据到文件
                            saveWaveformData(waveform)
                        }
                        
                        override fun onFftDataCapture(
                            visualizer: Visualizer,
                            fft: ByteArray,
                            samplingRate: Int
                        ) {
                            // 更新频谱数据
                            visualizerView.updateFft(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2, // 采样率，通常为10000-60000
                    true, // 开启波形捕获
                    true // 开启FFT捕获
                )
                
                // 启用可视化器
                enabled = true
            }
            
            isSetup = true
            Log.d(TAG, "可视化器设置成功，已启用")
            
        } catch (e: Exception) {
            Log.e(TAG, "设置可视化器失败", e)
        }
    }
    
    /**
     * 开始录制波形数据
     */
    fun startRecording() {
        if (isRecording) return
        
        try {
            // 使用应用程序内部存储目录
            val filesDir = context.filesDir
            Log.d(TAG, "应用内部存储路径: $filesDir")
            
            // 根据歌曲名创建文件
            val safeFileName = currentSongTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            dataFile = File(filesDir, "${safeFileName}_${timeStamp}.pcm")
            
            try {
                dataOutputStream = FileOutputStream(dataFile)
                isRecording = true
                Log.d(TAG, "开始录制波形数据到文件: ${dataFile?.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "创建数据文件失败: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化录制过程失败", e)
        }
    }
    
    /**
     * 停止录制波形数据
     */
    fun stopRecording() {
        if (!isRecording) return
        
        try {
            dataOutputStream?.flush()
            dataOutputStream?.close()
            dataOutputStream = null
            isRecording = false
            Log.d(TAG, "波形数据录制已停止，保存到: ${dataFile?.absolutePath}")
            
            // 数据保存成功后，打印路径信息供访问
            dataFile?.let {
                if (it.exists()) {
                    Log.d(TAG, "文件大小: ${it.length()} 字节")
                    Log.d(TAG, "文件路径: ${it.absolutePath}")
                    
                    // 如果希望使用adb访问，可以提供命令提示
                    Log.d(TAG, "可使用adb命令访问: adb pull ${it.absolutePath} .")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "关闭数据文件失败", e)
        }
    }
    
    /**
     * 保存波形数据到文件
     */
    private fun saveWaveformData(waveform: ByteArray) {
        if (!isRecording || dataOutputStream == null) return
        
        try {
            // 直接写入原始波形数据，不添加任何额外信息
            dataOutputStream?.write(waveform)
        } catch (e: IOException) {
            Log.e(TAG, "写入波形数据失败", e)
            stopRecording()
        }
    }
    
    /**
     * 获取已保存数据文件的路径
     */
    fun getDataFilePath(): String? {
        return dataFile?.absolutePath
    }
    
    /**
     * 暂停可视化器
     */
    fun pause() {
        try {
            visualizer?.enabled = false
            Log.d(TAG, "可视化器已暂停")
            // 同时停止录制
            stopRecording()
        } catch (e: Exception) {
            Log.e(TAG, "暂停可视化器失败", e)
        }
    }
    
    /**
     * 恢复可视化器
     */
    fun resume() {
        try {
            if (isSetup) {
                visualizer?.enabled = true
                Log.d(TAG, "可视化器已恢复")
                // 同时重新开始录制
                startRecording()
            } else {
                setupAndStart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复可视化器失败", e)
        }
    }
    
    /**
     * 释放可视化器资源
     */
    fun release() {
        try {
            // 停止录制
            stopRecording()
            
            // 释放可视化器
            visualizer?.release()
            visualizer = null
            isSetup = false
            Log.d(TAG, "可视化器资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放可视化器资源失败", e)
        }
    }
} 