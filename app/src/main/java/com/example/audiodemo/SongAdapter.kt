package com.example.audiodemo

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class SongAdapter(
    private var songs: List<Song> = emptyList(),
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
    
    companion object {
        private const val TAG = "SongAdapter"
    }

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textTitle: TextView = itemView.findViewById(android.R.id.text1)
        val textArtist: TextView = itemView.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        Log.d(TAG, "创建ViewHolder")
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        Log.d(TAG, "绑定ViewHolder: 位置=$position, 歌曲=${song.title}")
        
        holder.textTitle.text = song.title
        holder.textArtist.text = "${song.artist} • ${formatDuration(song.duration)}"
        
        // 确保响应单击事件
        holder.itemView.setOnClickListener {
            Log.d(TAG, "单击歌曲: ${song.title}, 位置=$position")
            // 通知用户已点击
            holder.itemView.isPressed = true
            // 调用回调
            onSongClick(song)
            // 确保视觉反馈后恢复
            holder.itemView.postDelayed({ holder.itemView.isPressed = false }, 200)
        }
    }

    override fun getItemCount(): Int = songs.size

    fun updateSongs(newSongs: List<Song>) {
        Log.d(TAG, "更新歌曲列表: ${newSongs.size}首")
        songs = newSongs
        notifyDataSetChanged()
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return String.format("%d:%02d", minutes, seconds)
    }
} 