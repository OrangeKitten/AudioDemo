package com.example.audiodemo

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(private val context: Context) {
    
    companion object {
        private const val TAG = "MusicPlayer"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // 这段代码定义了当前播放进度的状态流
    // _currentPosition 是一个可变的 StateFlow，用于存储当前播放进度（单位：毫秒）
    private val _currentPosition = MutableStateFlow(0)
    // currentPosition 是只读的 StateFlow，供外部观察当前播放进度
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    init {
        Log.d(TAG, "初始化MusicPlayer")
        initMediaPlayer()
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
                    mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    Log.d(TAG, "使用resourceId加载成功: ${song.title}, 资源长度: ${afd.length}")
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
            mediaPlayer?.setOnPreparedListener { mp ->
                Log.d(TAG, "MediaPlayer准备完成，开始播放: ${song.title}")
                mp.start()
                _isPlaying.value = true
                _duration.value = mp.duration
                Log.d(TAG, "歌曲时长: ${mp.duration}ms, 歌曲: ${song.title}")
                _currentSong.value = song
                startProgressTracking()
            }
            
            // 添加错误监听器，更详细地报告错误
            mediaPlayer?.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer播放错误: 歌曲=${song.title}, what=$what, extra=$extra")
                when (what) {
                    MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.e(TAG, "未知错误")
                    MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.e(TAG, "服务器挂了")
                    else -> Log.e(TAG, "其他错误")
                }
                when (extra) {
                    MediaPlayer.MEDIA_ERROR_IO -> Log.e(TAG, "IO错误")
                    MediaPlayer.MEDIA_ERROR_MALFORMED -> Log.e(TAG, "格式错误")
                    MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> Log.e(TAG, "不支持的格式")
                    MediaPlayer.MEDIA_ERROR_TIMED_OUT -> Log.e(TAG, "超时")
                    else -> Log.e(TAG, "其他扩展错误")
                }
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
            // 这里的it是Kotlin的语法特性：在let、apply、run等作用域函数中，默认的lambda参数名为it，代表调用者对象
            // 在本例中，mediaPlayer?.let { ... } 里的it就是mediaPlayer实例
            mediaPlayer?.let { media ->
                // 这里将it重命名为media，更清晰
                if (media.isPlaying) {
                    _currentPosition.value = media.currentPosition
                    // 每秒打印一次进度，避免日志过多
                    if (media.currentPosition % 1000 < 100) {
                        Log.v(TAG, "播放进度: ${media.currentPosition}ms / ${media.duration}ms")
                    }
                    handler.postDelayed(this, 100) // 100毫秒更新一次进度
                }
            }
        }
    }
} 
