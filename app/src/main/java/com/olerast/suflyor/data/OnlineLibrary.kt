package com.olerast.suflyor.data

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.olerast.suflyor.App
import com.olerast.suflyor.Settings
import com.olerast.suflyor.doc.DocumentImporter
import com.olerast.suflyor.doc.MarkdownImporter
import com.olerast.suflyor.session.SessionEngine
import java.util.concurrent.Executors

/** App-scoped synchronization survives Activity recreation; state changes are published on the main thread. */
class OnlineLibrary(private val settings: Settings, private val scripts: ScriptRepository) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    var url by mutableStateOf(settings.onlineLibraryUrl)
        private set
    var syncing by mutableStateOf(false)
        private set
    var syncedCount by mutableStateOf<Int?>(null)
        private set
    var error by mutableStateOf<OnlineLibraryException?>(null)
        private set

    fun configure(value: String) {
        if (syncing) return
        val next = if (value.isBlank()) "" else OnlineCatalog.normalizeUrl(value)
        settings.onlineLibraryUrl = next
        url = next
        error = null
        syncedCount = null
        if (next.isNotEmpty()) sync()
    }

    fun sync() {
        if (syncing || url.isEmpty()) return
        val source = url
        syncing = true
        error = null
        worker.execute {
            val result = runCatching {
                val docs = OnlineCatalog.fetch(source).map { entry ->
                    val doc = DocumentImporter.checkSize(
                        MarkdownImporter.parse(entry.content, entry.title).copy(format = "ONLINE", speechLang = entry.speechLang),
                    )
                    if (doc.isEmpty) throw OnlineLibraryException(OnlineProblem.ENTRY)
                    entry.id to doc
                }
                if (scripts.writeOnline(source, docs) != docs.size) throw OnlineLibraryException(OnlineProblem.STORAGE)
                docs.map { OnlineCatalog.localId(source, it.first) }
            }
            main.post {
                scripts.refresh()
                result.onSuccess { imported ->
                    syncedCount = imported.size
                    // Never reset a running prompter when its cached script is updated.
                    val current = scripts.currentId
                    if (current != null && current in imported && App.instance.engine.state.mode == SessionEngine.Mode.IDLE) {
                        scripts.select(current)
                    }
                }.onFailure {
                    error = it as? OnlineLibraryException ?: OnlineLibraryException(OnlineProblem.FORMAT)
                }
                syncing = false
            }
        }
    }
}
