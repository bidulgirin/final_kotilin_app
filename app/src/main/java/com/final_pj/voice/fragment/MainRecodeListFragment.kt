package com.final_pj.voice.fragment

import android.content.ContentResolver
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

// 프레그 먼트에서는 함수 등을 쓰지 못할까?...
class MainRecodeListFragment : Fragment() {

    // ----------------------------
    // 오디오 관련
    // ----------------------------
    val itemList = ArrayList<AudioItem>()

    // 초기화가 필요한 리소스들을 초기화
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lateinit var binding: FragmentMainRecodeListBinding
        binding.audioList.adapter = AudioAdapter(itemList, onClick = {Log.d("test", "눌렀다")})
        binding.audioList.layoutManager = LinearLayoutManager(
            context, LinearLayoutManager.VERTICAL, false)
    }

    companion object {

    }
}