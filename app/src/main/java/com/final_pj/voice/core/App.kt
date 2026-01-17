package com.final_pj.voice.core

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import com.final_pj.voice.feature.blocklist.service.BlocklistCache
import com.final_pj.voice.feature.blocklist.re.BlocklistRepository
import com.final_pj.voice.feature.report.repository.DefaultVoicePhishingRepository
import com.final_pj.voice.feature.report.repository.VoicePhishingRepository
import com.final_pj.voice.feature.setting.fragment.SettingFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import com.final_pj.voice.feature.report.worker.VoicePhishingSyncScheduler

class App : Application() {

    lateinit var db: AppDatabase
        private set

    lateinit var repo: BlocklistRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    lateinit var phishingNumber: VoicePhishingRepository
        private set


    override fun onCreate() {
        super.onCreate()
        // .fallbackToDestructiveMigration() => 테스트용!!!!! 마이그레이션 하기 귀찮아서 이렇게 해놓은거임
        db = Room.databaseBuilder(this, AppDatabase::class.java, "app.db")
            .fallbackToDestructiveMigration(false).build()
        repo = BlocklistRepository(db.blockedNumberDao())

        val api = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.final_pj.voice.feature.report.network.api.VoicePhisingApi::class.java)


        phishingNumber = DefaultVoicePhishingRepository(
            db.phishingDao(),   // 여기 중요: Room이 만든 DAO 구현체
            api
        )
        appScope.launch {
            val all = repo.loadAllToSet()
            BlocklistCache.replaceAll(all)

            val result = phishingNumber.syncSnapshot()
            result.onSuccess { count ->
                Log.d("PHISH_SYNC", "snapshot synced: $count items")
            }.onFailure { e ->
                Log.e("PHISH_SYNC", "snapshot failed", e)
            }
        }

        // 하루 1회 업데이트
        VoicePhishingSyncScheduler.scheduleDaily(this)

        // 설정관련
        val prefs = getSharedPreferences(SettingFragment.SettingKeys.PREF_NAME, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean(SettingFragment.SettingKeys.DARK_MODE, false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}