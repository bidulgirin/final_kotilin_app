package com.final_pj.voice.core

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import com.final_pj.voice.core.AppDatabase
import com.final_pj.voice.feature.blocklist.BlocklistCache
import com.final_pj.voice.feature.blocklist.BlocklistRepository
import com.final_pj.voice.feature.report.VoicePhishingRepository
import com.final_pj.voice.feature.setting.fragment.SettingFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Retrofit

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


        phishingNumber = com.final_pj.voice.feature.report.DefaultVoicePhishingRepository(
            db.phishingDao(),   // 여기 중요: Room이 만든 DAO 구현체
            api
        )
        appScope.launch {
            val all = repo.loadAllToSet()
            BlocklistCache.replaceAll(all)
        }

        // 설정관련
        val prefs = getSharedPreferences(SettingFragment.SettingKeys.PREF_NAME, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean(SettingFragment.SettingKeys.DARK_MODE, false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}