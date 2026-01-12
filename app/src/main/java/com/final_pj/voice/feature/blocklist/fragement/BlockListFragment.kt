package com.final_pj.voice.feature.blocklist.fragement

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.core.App
import com.final_pj.voice.feature.blocklist.BlocklistCache
import com.final_pj.voice.feature.blocklist.adapter.BlockListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockListFragment : Fragment(R.layout.fragment_block_list) {

    private lateinit var adapter: BlockListAdapter
    private lateinit var empty: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_block_list)
        empty = view.findViewById(R.id.empty)

        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = BlockListAdapter(mutableListOf()) { number ->
            confirmUnblock(number)
        }
        rv.adapter = adapter

        loadBlocked()
    }

    private fun loadBlocked() {
        val app = requireActivity().application as App

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val list = app.repo.loadAllToSet().toList().sorted()
            withContext(Dispatchers.Main) {
                adapter.replaceAll(list)
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun confirmUnblock(number: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("차단 해제")
            .setMessage("$number 차단을 해제할까요?")
            .setPositiveButton("예") { d, _ ->
                unblock(number)
                d.dismiss()
            }
            .setNegativeButton("아니오") { d, _ -> d.dismiss() }
            .show()
    }

    private fun unblock(number: String) {
        val app = requireActivity().application as App

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = app.repo.remove(number)
            if (ok) {
                BlocklistCache.remove(number) // 캐시도 같이 갱신
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    if (ok) "차단 해제됨" else "해제 실패",
                    Toast.LENGTH_SHORT
                ).show()
                loadBlocked()
            }
        }
    }
}