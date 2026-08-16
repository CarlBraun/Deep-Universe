package com.deepuniverse.app.data

import android.content.Context
import com.deepuniverse.core.game.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The single save slot, stored as JSON in the app's private files directory.
 *
 * Writes go to a temporary file which is then renamed over the real one. A save that is interrupted
 * halfway — the player force-quits, the battery dies — must not be able to leave a truncated file
 * behind, because the next launch would read it and the player would lose their character.
 */
class SaveStore(context: Context) {

    private val directory = File(context.filesDir, "save")
    private val file = File(directory, "game.json")
    private val mutex = Mutex()

    suspend fun load(): GameState? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                if (!file.exists()) return@runCatching null
                GameState.decode(file.readText())
            }.getOrNull()
        }
    }

    suspend fun save(state: GameState): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                directory.mkdirs()
                val temporary = File(directory, "game.json.tmp")
                temporary.writeText(GameState.encode(state))
                if (!temporary.renameTo(file)) {
                    // Rename can fail if the destination exists on some filesystems.
                    file.delete()
                    temporary.renameTo(file)
                }
            }
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete() }
    }
}
