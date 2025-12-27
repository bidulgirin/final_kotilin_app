package com.final_pj.voice.fragment

import android.media.MediaPlayer
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.final_pj.voice.adapter.AudioAdapter
import com.final_pj.voice.databinding.FragmentMainRecodeListBinding
import com.final_pj.voice.model.AudioItem
import com.final_pj.voice.repository.AudioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioListFragment : Fragment() {

    private var _binding: FragmentMainRecodeListBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioRepository: AudioRepository
    private lateinit var audioAdapter: AudioAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null
    private var currentAudio: AudioItem? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainRecodeListBinding.inflate(inflater, container, false)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // 내부 저장소 파일만 로드
        audioRepository = AudioRepository(requireContext())
        val audioList = audioRepository.loadAudioFiles()

        audioAdapter = AudioAdapter(audioList) { audioItem ->
            playAudio(audioItem)
        }
        binding.recyclerView.adapter = audioAdapter

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    binding.tvCurrentTime.text = formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        return binding.root
    }

    private fun playAudio(item: AudioItem) {
        currentAudio = item
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(requireContext(), item.uri)
            prepare()
            start()
        }

        binding.tvTotalTime.text = formatDuration(mediaPlayer?.duration?.toLong() ?: 0L)
        updateSeekBar()
    }

    private fun updateSeekBar() {
        updateJob?.cancel()
        mediaPlayer?.let { mp ->
            binding.seekBar.max = mp.duration
            updateJob = lifecycleScope.launch {
                while (mp.isPlaying) {
                    binding.seekBar.progress = mp.currentPosition
                    binding.tvCurrentTime.text = formatDuration(mp.currentPosition.toLong())
                    delay(500)
                }
            }
        }
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
    }

    fun resumeAudio() {
        mediaPlayer?.start()
        updateSeekBar()
    }

    fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        updateJob?.cancel()
        binding.seekBar.progress = 0
        binding.tvCurrentTime.text = "00:00"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAudio()
        _binding = null
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

