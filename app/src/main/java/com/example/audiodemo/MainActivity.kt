package com.example.audiodemo



import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

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
    
    private lateinit var songAdapter: SongAdapter
    private lateinit var musicPlayer: MusicPlayer
    
    private val songList = mutableListOf<Song>()
    private var currentSongIndex = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate开始")
        setContentView(R.layout.activity_main)
        
        try {
            initViews()
            setupListeners()
            
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
//            val song3 = Song(
//                id = 3,
//                title = "16channel",
//                artist = "本地音频",
//                duration = 0,
//                resourceId = R.raw.sine16chanstest // 直接提供资源ID
//            )
//            songs.add(song3)
//            Log.d(TAG, "添加第三首歌曲: ${song3.title}, 资源ID: ${song3.resourceId}, 格式: WAV (16通道)")
            
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
            musicPlayer.playSong(song)
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
        musicPlayer.release()
    }
}