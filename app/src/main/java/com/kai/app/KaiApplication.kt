package com.kai.app

import android.app.Application
import androidx.room.Room
import com.kai.app.audio.*
import com.kai.app.data.*
import com.kai.app.feature.chat.ChatEngine
import com.kai.app.security.KeyVault
import com.kai.core.network.GeminiApi
import kotlinx.coroutines.*

class KaiApplication : Application() {
    lateinit var graph: AppGraph
        private set
    override fun onCreate() { super.onCreate(); graph = AppGraph(this) }
}

class AppGraph(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database = Room.databaseBuilder(application, KaiDatabase::class.java, "kai.db").build()
    val repository = SessionRepository(database)
    val preferences = Preferences(application)
    val vault = KeyVault(application)
    val api = GeminiApi(vault::current)
    val audio = AudioStore(application, repository.dao)
    val player = KaiPlayer(application, scope)
    val initialized: Deferred<Unit> = scope.async(Dispatchers.IO) {
        vault.load()
        repository.dao.recoverText(); repository.dao.recoverAudio()
        audio.cleanup()
    }
    val chat = ChatEngine(scope, repository, preferences, api, audio, player, initialized)
}
