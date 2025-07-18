package com.example.audiodemo

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Build

class MusicPlayer(private val context: Context) {
    
    companion object {
        private const val TAG = "MusicPlayer"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // 外部准备完毕监听器
    private var externalOnPreparedListener: MediaPlayer.OnPreparedListener? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // 这段代码定义了当前播放进度的状态流
    // _currentPosition 是一个可变的 StateFlow，用于存储当前播放进度（单位：毫秒）
    private val _currentPosition = MutableStateFlow(0)
    // currentPosition 是只读的 StateFlow，供外部观察当前播放进度
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    // 这段代码定义了一个用于存储音频时长的可变状态流 _duration（单位：毫秒），
    // 并通过 asStateFlow() 暴露为只读的 StateFlow<Int> 供外部观察。
    private val _duration = MutableStateFlow(0)
    // duration 是通过 _duration.asStateFlow() 得到的 StateFlow<Int>，它本身不会主动赋值。
    // 真正赋值 _duration.value 的地方，通常是在播放歌曲时（如 playSong 方法中），
    // 当 MediaPlayer 成功加载音频资源后，会通过 _duration.value = mediaPlayer?.duration ?: 0 进行赋值。
    // 这样外部通过 duration 观察到的就是最新的音频时长。
    val duration: StateFlow<Int> = _duration.asStateFlow()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    init {
        Log.d(TAG, "初始化MusicPlayer")
        initMediaPlayer()
    }

    /**
     * 设置外部OnPreparedListener
     */
    fun setOnPreparedListener(listener: MediaPlayer.OnPreparedListener) {
        this.externalOnPreparedListener = listener
    }

    private fun initMediaPlayer() {
        Log.d(TAG, "初始化MediaPlayer")
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnCompletionListener {
                Log.d(TAG, "播放完成")
                _isPlaying.value = false
                // 如果需要自动播放下一首歌曲，这里可以添加回调
            }
            
            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer错误: what=$what, extra=$extra")
                false
            }
        }
        Log.d(TAG, "MediaPlayer初始化完成")
    }
    
    fun playSong(song: Song) {
        Log.d(TAG, "准备播放歌曲: ${song.title}")
        try {
            mediaPlayer?.reset()
            Log.d(TAG, "MediaPlayer已重置")
            
            // 使用资源ID方式加载
            Log.d(TAG, "使用resourceId加载: ${song.resourceId}, 歌曲: ${song.title}")
            val afd = context.resources.openRawResourceFd(song.resourceId)
            if (afd != null) {
                try {
                    // 获取文件长度信息
                    val fileLength = afd.length
                    Log.d(TAG, "文件长度: $fileLength 字节")
                    
                    mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    Log.d(TAG, "使用resourceId加载成功: ${song.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "设置数据源失败: ${song.title}, 错误: ${e.message}", e)
                    return
                } finally {
                    afd.close()
                }
            } else {
                Log.e(TAG, "无法打开资源ID: ${song.resourceId}, 歌曲: ${song.title}")
                return
            }
            
            // 注意：应先设置OnPreparedListener，再调用prepareAsync()，否则可能出现"准备已完成但监听器未设置"的竞态问题
            // 这是Kotlin中的高阶函数（Lambda表达式）写法，用于设置MediaPlayer的OnPreparedListener监听器。
            // 语法解释如下：
            //
            // mediaPlayer?.setOnPreparedListener { mp -> ... }
            //
            // 等价于传统Java写法：
            // mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            //     @Override
            //     public void onPrepared(MediaPlayer mp) {
            //         // 处理逻辑
            //     }
            // });
            //
            // 在Kotlin中，setOnPreparedListener方法可以直接传入一个Lambda表达式，
            // 其中 { mp -> ... } 表示当MediaPlayer准备完成时会回调此Lambda，mp为回调参数（即已准备好的MediaPlayer实例）。
            //
            // 下面是原有的Kotlin Lambda写法，功能与Java匿名内部类一致，但更简洁。
            mediaPlayer?.setOnPreparedListener { mp ->
                Log.d(TAG, "MediaPlayer准备完成，开始播放: ${song.title}")

                // 主动调用 MediaPlayer 的 getDuration() API 获取时长
                val mpDuration = try {
                    mp.getDuration()
                } catch (e: Exception) {
                    Log.e(TAG, "主动获取duration失败: ${e.message}")
                    0
                }

                // 打印MediaPlayer返回的播放时长
                Log.d(TAG, "=== MediaPlayer播放时长信息 ===")
                Log.d(TAG, "歌曲: ${song.title}")
                Log.d(TAG, "主动API获取时长(毫秒): $mpDuration")
                Log.d(TAG, "格式化时长: ${formatTime(mpDuration.toLong())}")
                Log.d(TAG, "=============================")

                mp.start()
                _isPlaying.value = true
                _duration.value = mpDuration
                _currentSong.value = song
                startProgressTracking()
                
                // 调用外部设置的OnPreparedListener
                externalOnPreparedListener?.onPrepared(mp)
            }
            
            // 播放完成监听器
            mediaPlayer?.setOnCompletionListener { mp ->
                val finalPosition = mp.currentPosition
                Log.d(TAG, "播放完成 - 歌曲: ${song.title}")
                Log.d(TAG, "最终播放位置: ${finalPosition}ms (${formatTime(finalPosition.toLong())})")
                Log.d(TAG, "总时长: ${mp.duration}ms (${formatTime(mp.duration.toLong())})")
                _isPlaying.value = false
            }
            
            // 添加错误监听器
            mediaPlayer?.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer播放错误: 歌曲=${song.title}, what=$what, extra=$extra")
                false
            }
            
            mediaPlayer?.prepareAsync()
            Log.d(TAG, "异步准备中...: ${song.title}")
        } catch (e: Exception) {
            Log.e(TAG, "播放歌曲出错: ${song.title}, 错误: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    fun togglePlayPause() {
        Log.d(TAG, "切换播放/暂停")
        mediaPlayer?.let {
            if (it.isPlaying) {
                Log.d(TAG, "当前正在播放，执行暂停")
                it.pause()
                _isPlaying.value = false
                handler.removeCallbacks(updateProgressRunnable)
            } else {
                Log.d(TAG, "当前已暂停，开始播放")
                it.start()
                _isPlaying.value = true
                startProgressTracking()
            }
        } ?: Log.w(TAG, "MediaPlayer为null，无法切换播放状态")
    }
    
    fun seekTo(position: Int) {
        Log.d(TAG, "拖动进度条到: ${position}ms")
        mediaPlayer?.seekTo(position)
    }
    
    fun release() {
        Log.d(TAG, "释放MediaPlayer资源")
        handler.removeCallbacks(updateProgressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }
    
    private fun startProgressTracking() {
        Log.d(TAG, "开始跟踪播放进度")
        handler.post(updateProgressRunnable)
    }
    
    /**
     * updateProgressRunnable 的触发流程说明：
     * 1. 在 startProgressTracking() 方法中，通过 handler.post(updateProgressRunnable) 首次触发。
     *    - startProgressTracking() 会在歌曲开始播放（如 onPrepared 回调、togglePlayPause 恢复播放时）被调用。
     * 2. run() 方法每次执行后，如果 MediaPlayer 仍在播放，则通过 handler.postDelayed(this, 100) 自行递归触发，实现定时更新。
     * 3. 当暂停或释放时（如 togglePlayPause 暂停、release），通过 handler.removeCallbacks(updateProgressRunnable) 停止触发。
     */
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { media ->
                if (media.isPlaying) {
                    val position = media.currentPosition
                    val duration = media.duration
                    _currentPosition.value = position
                    
                    // 每秒打印一次播放进度
                    if (position % 1000 < 100) {
                        val song = _currentSong.value
                        val songTitle = song?.title ?: "未知"
                        val posFormatted = formatTime(position.toLong())
                        val durFormatted = formatTime(duration.toLong())
                        Log.d(TAG, "播放进度: $songTitle - $posFormatted / $durFormatted (${position}ms / ${duration}ms)")
                    }
                    
                    handler.postDelayed(this, 100) // 100毫秒更新一次进度
                }
            }
        }
    }
    
    /**
     * 获取当前MediaPlayer实例
     * 用于音频可视化功能
     */
    fun getMediaPlayer(): MediaPlayer? {
        return mediaPlayer
    }
    
    /**
     * 格式化时间为 mm:ss 格式
     */
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
} 
