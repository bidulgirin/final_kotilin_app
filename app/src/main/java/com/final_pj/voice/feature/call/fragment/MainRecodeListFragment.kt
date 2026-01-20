package com.final_pj.voice.feature.call.fragment

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.adapter.AudioAdapter
import com.final_pj.voice.databinding.FragmentMainRecodeListBinding
import com.final_pj.voice.feature.call.model.AudioItem
import com.final_pj.voice.feature.call.repository.AudioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class AudioListFragment : Fragment() {

    private var _binding: FragmentMainRecodeListBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioRepository: AudioRepository
    private lateinit var audioAdapter: AudioAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null
    private var currentAudio: AudioItem? = null
    private var isPaused = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainRecodeListBinding.inflate(inflater, container, false)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        audioRepository = AudioRepository(requireContext())
        val audioList = audioRepository.loadAudioFiles().toMutableList()

        audioAdapter = AudioAdapter(
            items = audioList,
            onPlay = { audioItem -> playAudio(audioItem) },
            onSingleDelete = { audioItem, position ->
                confirmDelete(audioItem, position)
            },
            onExport = { audioItem -> shareAudioFile(audioItem) },
            onSelectionChanged = { count, inSelectionMode ->
                renderSelectionUi(count, inSelectionMode)
            }
        )

        binding.recyclerView.adapter = audioAdapter

        // 다중 삭제 버튼
        binding.btnBulkDelete.setOnClickListener {
            val selected = audioAdapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            confirmBulkDelete(selected)
        }

        // 시크바 조작
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

        // 초기 UI
        renderSelectionUi(0, false)
        binding.tvNowPlaying.text = "재생 중: -"

        return binding.root
    }

    private fun renderSelectionUi(selectedCount: Int, inSelectionMode: Boolean) {
        binding.bulkDeleteBar.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        binding.tvSelectedCount.text = "선택됨 ${selectedCount}개"
        binding.btnBulkDelete.text = "전체 삭제(${selectedCount})"
        binding.btnBulkDelete.isEnabled = selectedCount > 0
    }

    private fun playAudio(item: AudioItem) {
        currentAudio = item
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(requireContext(), item.uri)
                prepare()
                start()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "재생 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                return@apply
            }
        }

        binding.tvTotalTime.text = formatDuration(mediaPlayer?.duration?.toLong() ?: 0L)
        binding.tvNowPlaying.text = "재생 중: ${File(item.path).name.ifBlank { item.uri.toString() }}"
        renderPlayPause(isPlaying = true)
        updateSeekBar()
    }

    private fun shareAudioFile(item: AudioItem) {
        val file = File(item.path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "녹음 파일 추출/공유"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "추출 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

    fun pauseAudio() { mediaPlayer?.pause() }
    fun resumeAudio() { mediaPlayer?.start(); updateSeekBar() }

    private fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            Toast.makeText(requireContext(), "재생할 녹음을 선택하세요", Toast.LENGTH_SHORT).show()
            return
        }

        if (mp.isPlaying) {
            mp.pause()
            isPaused = true
            updateJob?.cancel()
            renderPlayPause(isPlaying = false)
        } else {
            mp.start()
            isPaused = false
            renderPlayPause(isPlaying = true)
            updateSeekBar()
        }
    }
    private fun renderPlayPause(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setIconResource(icon)
    }
    fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        updateJob?.cancel()
        binding.seekBar.progress = 0
        binding.tvCurrentTime.text = "00:00"
        binding.tvTotalTime.text = "00:00"
        binding.tvNowPlaying.text = "재생 중: -"
        renderPlayPause(isPlaying = false)
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

    // 단일 삭제
    private fun confirmDelete(item: AudioItem, position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("녹음 삭제")
            .setMessage("이 녹음을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { d, _ ->
                deleteOne(item, position)
                d.dismiss()
            }
            .setNegativeButton("취소") { d, _ -> d.dismiss() }
            .show()
    }

    private fun deleteOne(item: AudioItem, position: Int) {
        if (currentAudio?.uri == item.uri) {
            stopAudio()
            currentAudio = null
        }

        val ok = deleteFileByItem(item)
        if (ok) {
            audioAdapter.removeAt(position)
            Toast.makeText(requireContext(), "삭제되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "삭제 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // 다중 삭제
    private fun confirmBulkDelete(selected: List<AudioItem>) {
        AlertDialog.Builder(requireContext())
            .setTitle("선택 삭제")
            .setMessage("선택한 ${selected.size}개 녹음을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { d, _ ->
                deleteSelected(selected)
                d.dismiss()
            }
            .setNegativeButton("취소") { d, _ -> d.dismiss() }
            .show()
    }

    private fun deleteSelected(selected: List<AudioItem>) {
        val playingSelected = selected.any { it.uri == currentAudio?.uri }
        if (playingSelected) {
            stopAudio()
            currentAudio = null
        }

        var successCount = 0
        selected.forEach { item ->
            if (deleteFileByItem(item)) successCount++
        }

        if (successCount > 0) {
            audioAdapter.removeItems(selected)
            Toast.makeText(requireContext(), "삭제 완료: ${successCount}개", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "삭제 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFileByItem(item: AudioItem): Boolean {
        val rows = runCatching {
            requireContext().contentResolver.delete(item.uri, null, null)
        }.getOrDefault(0)

        val ok = rows > 0 || runCatching {
            if (item.path.isNotBlank()) File(item.path).delete() else false
        }.getOrDefault(false)

        return ok
    }
}
