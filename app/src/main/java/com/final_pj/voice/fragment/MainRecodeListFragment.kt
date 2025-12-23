package com.final_pj.voice.fragment

import android.content.ContentResolver
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.adapter.AudioAdapter
import com.final_pj.voice.databinding.FragmentMainRecodeListBinding
import com.final_pj.voice.model.AudioItem
import com.final_pj.voice.repository.AudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainRecodeListFragment : Fragment(R.layout.fragment_main_recode_list) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var audioAdapter: AudioAdapter
    private lateinit var audioRepository: AudioRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView
        recyclerView = view.findViewById(R.id.audioList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Repository 생성
        audioRepository = AudioRepository(requireContext().contentResolver)

        // 🔥 오디오 리스트 로드
        val audioList = audioRepository.loadAudioFiles()

        // Adapter 연결
        audioAdapter = AudioAdapter(audioList) { audioItem ->
            // 아이템 클릭 이벤트
            Log.d("AudioClick", "클릭: ${audioItem.title}")
        }

        recyclerView.adapter = audioAdapter
    }
}
