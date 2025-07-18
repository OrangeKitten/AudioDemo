package com.example.audiodemo



import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import android.util.Log

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "AudioDemo"
        private const val REQUEST_PERMISSION_CODE = 123
    }
    
    private lateinit var recyclerViewSongs: RecyclerView
    private lateinit var textNowPlaying: TextView
    private lateinit var textCurrentTime: TextView
    private lateinit var textDuration: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var buttonPlayPause: ImageButton
    private lateinit var buttonNext: ImageButton
    private lateinit var buttonPrevious: ImageButton
    private lateinit var audioVisualizerView: AudioVisualizerView
    
    private lateinit var songAdapter: SongAdapter
    private lateinit var musicPlayer: MusicPlayer
    
    // 音频可视化器
    private var audioVisualizer: AudioVisualizer? = null
    
    private val songList = mutableListOf<Song>()
    private var currentSongIndex = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate开始")
        setContentView(R.layout.activity_main)
        
        try {
            initViews()
            setupListeners()
            
            // 请求必要的权限
            requestAudioPermissions()
            
            Log.d(TAG, "初始化MusicPlayer")
            musicPlayer = MusicPlayer(this)
            
            Log.d(TAG, "初始化适配器")
            songAdapter = SongAdapter(emptyList()) { song -> 
                Log.d(TAG, "选择播放歌曲: ${song.title}")
                playSong(song)
            }
            
            Log.d(TAG, "设置RecyclerView")
            recyclerViewSongs.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = songAdapter
            }
            
            observeMusicPlayerState()
            
            textNowPlaying.text = "正在加载音乐..."
            
            // 加载raw目录下的音频文件
            loadRawAudioFiles()
            
            // 检查RecyclerView状态
            Log.d(TAG, "RecyclerView高度: ${recyclerViewSongs.height}")
            Log.d(TAG, "RecyclerView可见性: ${recyclerViewSongs.visibility}")
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化发生错误", e)
            textNowPlaying.text = "应用初始化错误: ${e.message}"
            Toast.makeText(this, "初始化错误: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initViews() {
        recyclerViewSongs = findViewById(R.id.recyclerViewSongs)
        textNowPlaying = findViewById(R.id.textNowPlaying)
        textCurrentTime = findViewById(R.id.textCurrentTime)
        textDuration = findViewById(R.id.textDuration)
        seekBar = findViewById(R.id.seekBar)
        buttonPlayPause = findViewById(R.id.buttonPlayPause)
        buttonNext = findViewById(R.id.buttonNext)
        buttonPrevious = findViewById(R.id.buttonPrevious)
        audioVisualizerView = findViewById(R.id.audioVisualizer)
        
        Log.d(TAG, "视图初始化完成")
    }
    
    private fun setupListeners() {
        buttonPlayPause.setOnClickListener {
            Log.d(TAG, "点击播放/暂停按钮 - 当前状态: ${if (musicPlayer.isPlaying.value) "播放中" else "已暂停"}")
            musicPlayer.togglePlayPause()
            Log.d(TAG, "播放/暂停操作后状态: ${if (musicPlayer.isPlaying.value) "播放中" else "已暂停"}")
        }
        
        buttonNext.setOnClickListener {
            Log.d(TAG, "点击下一首按钮 - 当前索引: $currentSongIndex, 总歌曲数: ${songList.size}")
            playNextSong()
            Log.d(TAG, "切换到下一首歌曲 - 新索引: $currentSongIndex")
        }
        
        buttonPrevious.setOnClickListener {
            Log.d(TAG, "点击上一首按钮 - 当前索引: $currentSongIndex, 总歌曲数: ${songList.size}")
            playPreviousSong()
            Log.d(TAG, "切换到上一首歌曲 - 新索引: $currentSongIndex")
        }
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicPlayer.seekTo(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }
    
    private fun observeMusicPlayerState() {
        lifecycleScope.launch {
            musicPlayer.isPlaying.collectLatest { isPlaying ->
                buttonPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }
        }
        
        lifecycleScope.launch {
            musicPlayer.currentPosition.collectLatest { position ->
                seekBar.progress = position
                textCurrentTime.text = formatTime(position.toLong())
            }
        }
        
        lifecycleScope.launch {
            musicPlayer.duration.collectLatest { duration ->
                seekBar.max = duration
                textDuration.text = formatTime(duration.toLong())
            }
        }
        
        lifecycleScope.launch {
            musicPlayer.currentSong.collectLatest { song ->
                song?.let {
                    textNowPlaying.text = "${it.title} - ${it.artist}"
                    currentSongIndex = songList.indexOf(it)
                }
            }
        }
    }
    
    private fun loadRawAudioFiles() {
        Log.d(TAG, "开始加载raw目录下的音频文件")
        try {
            val songs = mutableListOf<Song>()
            
            // 添加第一个音频文件
            val song1 = Song(
                id = 1,
                title = "携手游人间",
                artist = "本地音频",
                duration = 0,
                resourceId = R.raw.a // 直接提供资源ID
            )
            songs.add(song1)
            Log.d(TAG, "添加第一首歌曲: ${song1.title}, 资源ID: ${song1.resourceId}, 格式: AC3")
            
            // 添加第二个音频文件
            val song2 = Song(
                id = 2,
                title = "独自去偷欢",
                artist = "本地音频",
                duration = 0,
                resourceId = R.raw.v // 直接提供资源ID
            )
            songs.add(song2)
            Log.d(TAG, "添加第二首歌曲: ${song2.title}, 资源ID: ${song2.resourceId}, 格式: AAC")

            // 添加第三个音频文件
            val song3 = Song(
                id = 3,
                title = "dolby",
                artist = "本地音频",
                duration = 0,
                resourceId = R.raw.b // 直接提供资源ID
            )
            songs.add(song3)
            Log.d(TAG, "添加第三首歌曲: ${song3.title}, 资源ID: ${song3.resourceId}, 格式: EC3")
            
            // 检查每个音频文件的实际时长
            Log.d(TAG, "========= 资源文件时长检测 =========")
            
            // 检查所有音频文件
            val resourceIds = arrayOf(R.raw.a, R.raw.v, R.raw.b)
            val fileNames = arrayOf("a.ac3", "v.aac", "b.ec3")
            
            for (i in resourceIds.indices) {
                val resId = resourceIds[i]
                val fileName = fileNames[i]
                
                // 使用MediaMetadataRetriever检查时长
                try {
                    val afd = resources.openRawResourceFd(resId)
                    if (afd != null) {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        
                        // 获取元数据时长
                        val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                        val minutes = duration / 1000 / 60
                        val seconds = duration / 1000 % 60
                        
                        Log.d(TAG, "文件 $fileName 元数据时长: ${minutes}分${seconds}秒 (${duration}ms)")
                        retriever.release()
                        afd.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "无法获取 $fileName 的元数据: ${e.message}")
                }
                
                // 使用MediaPlayer检查时长
                try {
                    val tempPlayer = android.media.MediaPlayer()
                    val tempAfd = resources.openRawResourceFd(resId)
                    tempPlayer.setDataSource(tempAfd.fileDescriptor, tempAfd.startOffset, tempAfd.length)
                    tempPlayer.prepare() // 同步准备
                    val playerDuration = tempPlayer.duration
                    val minutes = playerDuration / 1000 / 60
                    val seconds = playerDuration / 1000 % 60
                    
                    Log.d(TAG, "文件 $fileName MediaPlayer时长: ${minutes}分${seconds}秒 (${playerDuration}ms)")
                    tempPlayer.release()
                    tempAfd.close()
                } catch (e: Exception) {
                    Log.e(TAG, "无法使用MediaPlayer获取 $fileName 的时长: ${e.message}")
                }
            }
            
            Log.d(TAG, "===================================")
            
            Log.d(TAG, "加载完成，共 ${songs.size} 首歌曲")
            
            // 检查歌曲列表是否正确
            songs.forEachIndexed { index, song ->
                Log.d(TAG, "歌曲[$index]: ${song.title}, 资源ID: ${song.resourceId}")
            }
            
            songList.clear()
            songList.addAll(songs)
            songAdapter.updateSongs(songList)
            Log.d(TAG, "歌曲列表已更新，适配器项目数: ${songAdapter.itemCount}")
            
            textNowPlaying.text = "请选择歌曲播放"
            Log.d(TAG, "音乐加载完成，等待用户选择")
            
        } catch (e: Exception) {
            Log.e(TAG, "加载音频文件出错: ${e.message}", e)
            textNowPlaying.text = "加载音频文件出错: ${e.message}"
            Toast.makeText(this, "加载音频文件出错: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun playSong(song: Song) {
        Log.d(TAG, "开始播放歌曲: ${song.title}, 资源ID: ${song.resourceId}")
        try {
            // 通过UI提示用户正在准备播放
            textNowPlaying.text = "正在准备播放: ${song.title}"
            
            // 先释放旧的可视化器资源
            audioVisualizer?.release()
            
            // 为MediaPlayer设置OnPrepared监听器，确保在准备好后初始化可视化器
            musicPlayer.setOnPreparedListener { mp ->
                Log.d(TAG, "MediaPlayer准备就绪，初始化音频可视化器")
                
                // 检查是否有录音权限
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                    == PackageManager.PERMISSION_GRANTED) {
                    
                    // 初始化并启动可视化器，传递当前歌曲名称
                    audioVisualizer = AudioVisualizer(mp, audioVisualizerView, song.title, this)
                    audioVisualizer?.setupAndStart()
                    
                    // 开始录制波形数据
                    audioVisualizer?.startRecording()
                    
                    Log.d(TAG, "音频可视化器已设置并启动，开始录制波形数据")
                } else {
                    Log.e(TAG, "缺少录音权限，无法初始化音频可视化器")
                    requestAudioPermissions()
                }
            }
            
            // 播放歌曲
            musicPlayer.playSong(song)
            
            // 监听播放状态来控制可视化器
            lifecycleScope.launch {
                musicPlayer.isPlaying.collectLatest { isPlaying ->
                    if (isPlaying) {
                        Log.d(TAG, "音乐正在播放，恢复可视化器")
                        audioVisualizer?.resume()
                        // 恢复播放时，重新开始录制
                        audioVisualizer?.startRecording()
                        Log.d(TAG, "恢复播放，重新开始录制波形数据")
                    } else {
                        Log.d(TAG, "音乐已暂停，暂停可视化器")
                        audioVisualizer?.pause()
                        // 暂停播放时，停止录制
                        audioVisualizer?.stopRecording()
                        Log.d(TAG, "播放暂停，停止录制波形数据")
                    }
                }
            }
            
            Log.d(TAG, "歌曲播放请求已发送: ${song.title}")
        } catch (e: Exception) {
            Log.e(TAG, "播放歌曲失败: ${song.title}", e)
            textNowPlaying.text = "播放失败: ${song.title} - ${e.message}"
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun playNextSong() {
        if (songList.isNotEmpty()) {
            Log.d(TAG, "计算下一首歌曲索引: 当前=$currentSongIndex, 总数=${songList.size}")
            currentSongIndex = (currentSongIndex + 1) % songList.size
            Log.d(TAG, "播放下一首歌曲，新索引: $currentSongIndex")
            val nextSong = songList[currentSongIndex]
            Log.d(TAG, "下一首歌曲信息: ${nextSong.title}")
            playSong(nextSong)
        } else {
            Log.w(TAG, "无法播放下一首 - 歌曲列表为空")
        }
    }
    
    private fun playPreviousSong() {
        if (songList.isNotEmpty()) {
            Log.d(TAG, "计算上一首歌曲索引: 当前=$currentSongIndex, 总数=${songList.size}")
            currentSongIndex = (currentSongIndex - 1 + songList.size) % songList.size
            Log.d(TAG, "播放上一首歌曲，新索引: $currentSongIndex")
            val prevSong = songList[currentSongIndex]
            Log.d(TAG, "上一首歌曲信息: ${prevSong.title}")
            playSong(prevSong)
        } else {
            Log.w(TAG, "无法播放上一首 - 歌曲列表为空")
        }
    }
    

    
    private fun formatTime(timeMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        
        // 释放音频可视化器资源
        audioVisualizer?.release()
        audioVisualizerView.release()
        
        // 释放音乐播放器资源
        musicPlayer.release()
    }

    /**
     * 请求音频录制和存储权限
     */
    private fun requestAudioPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            
            // 检查录音权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }
            
            // 检查存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            
            if (permissions.isNotEmpty()) {
                Log.d(TAG, "请求权限: ${permissions.joinToString()}")
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    REQUEST_PERMISSION_CODE
                )
            } else {
                Log.d(TAG, "已经拥有所有必要权限")
            }
        }
    }
    
    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_PERMISSION_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                Log.d(TAG, "所有必要权限已授予")
                // 如果用户已经在播放音乐，重新初始化可视化器
                val mediaPlayer = musicPlayer.getMediaPlayer()
                val currentSong = musicPlayer.currentSong.value
                if (mediaPlayer != null && musicPlayer.isPlaying.value && currentSong != null) {
                    audioVisualizer?.release()
                    audioVisualizer = AudioVisualizer(mediaPlayer, audioVisualizerView, currentSong.title, this)
                    audioVisualizer?.setupAndStart()
                    audioVisualizer?.startRecording()
                    Log.d(TAG, "权限授予后重新初始化可视化器并开始录制")
                }
            } else {
                Log.e(TAG, "部分权限被拒绝，音频可视化或数据保存功能可能不可用")
                Toast.makeText(this, "需要录音和存储权限来显示音频可视化效果并保存数据", Toast.LENGTH_SHORT).show()
            }
        }
    }
}