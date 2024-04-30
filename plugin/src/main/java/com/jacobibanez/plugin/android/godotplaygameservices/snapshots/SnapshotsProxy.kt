package com.jacobibanez.plugin.android.godotplaygameservices.snapshots

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.SnapshotsClient.EXTRA_SNAPSHOT_METADATA
import com.google.android.gms.games.SnapshotsClient.RESOLUTION_POLICY_HIGHEST_PROGRESS
import com.google.android.gms.games.SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
import com.google.android.gms.games.SnapshotsClient.SnapshotConflict
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.gson.Gson
import com.jacobibanez.plugin.android.godotplaygameservices.BuildConfig.GODOT_PLUGIN_NAME
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SnapshotSignals.conflictEmitted
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SnapshotSignals.gameDeleted
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SnapshotSignals.gameLoaded
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SnapshotSignals.gameSaved
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SnapshotSignals.snapshotsLoaded
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin.emitSignal
import org.godotengine.godot.plugin.UsedByGodot
import java.time.LocalDateTime

class SnapshotsProxy(
    private val godot: Godot,
    private val snapshotsClient: SnapshotsClient = PlayGames.getSnapshotsClient(godot.getActivity()!!)
) {
    private val tag = SnapshotsProxy::class.java.simpleName

    private val showSavedGamesRequestCode = 9010

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == showSavedGamesRequestCode && resultCode == Activity.RESULT_OK) {
            data?.let { intent ->
                if (intent.hasExtra(EXTRA_SNAPSHOT_METADATA)) {
                    val snapshotMetadata = intent.extras
                        ?.get(EXTRA_SNAPSHOT_METADATA) as SnapshotMetadata
//                    loadGame(snapshotMetadata.uniqueName, false)
                }
            }
        }
    }

    fun showSavedGames(
        title: String,
        allowAddButton: Boolean,
        allowDelete: Boolean,
        maxSnapshots: Int
    ) {
        Log.d(tag, "Showing save games")
        snapshotsClient.getSelectSnapshotIntent(title, allowAddButton, allowDelete, maxSnapshots)
            .addOnSuccessListener { intent ->
                ActivityCompat.startActivityForResult(
                    godot.getActivity()!!, intent,
                    showSavedGamesRequestCode, null
                )
            }
    }

//    fun saveGame(
//        fileName: String,
//        description: String,
//        saveData: ByteArray,
//        playedTimeMillis: Long,
//        progressValue: Long
//    ) {
//        Log.d(tag, "Saving game data with name $fileName and description ${description}.")
//        snapshotsClient.open(fileName, true, RESOLUTION_POLICY_HIGHEST_PROGRESS)
//            .addOnSuccessListener { dataOrConflict ->
//                if (dataOrConflict.isConflict) {
//                    handleConflict(dataOrConflict.conflict)
//                    return@addOnSuccessListener
//                }
//                dataOrConflict.data?.let { snapshot ->
//                    snapshot.snapshotContents.writeBytes(saveData)
//                    val metadata = SnapshotMetadataChange.Builder().apply {
//                        setDescription(description)
//                        setPlayedTimeMillis(playedTimeMillis)
//                        setProgressValue(progressValue)
//                    }.build()
//
//                    snapshotsClient.commitAndClose(snapshot, metadata)
//                    emitSignal(
//                        godot,
//                        GODOT_PLUGIN_NAME,
//                        gameSaved,
//                        true,
//                        fileName,
//                        description
//                    )
//                }
//            }
//    }
//
//    fun loadGame(fileName: String, createIfNotFound: Boolean) {
//        Log.d(tag, "Loading snapshot with name $fileName.")
//        snapshotsClient.open(fileName, createIfNotFound, RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    val dataOrConflict = task.result
//                    if (dataOrConflict.isConflict) {
//                        handleConflict(dataOrConflict.conflict)
//                        return@addOnCompleteListener
//                    }
//                    dataOrConflict.data?.let { snapshot ->
//                        emitSignal(
//                            godot,
//                            GODOT_PLUGIN_NAME,
//                            gameLoaded,
//                            Gson().toJson(fromSnapshot(godot, snapshot))
//                        )
//                    }
//                } else {
//                    Log.e(
//                        tag,
//                        "Error while opening Snapshot $fileName for loading. Cause: ${task.exception}"
//                    )
//                }
//            }
//    }

    fun loadSnapshots(forceReload: Boolean) {
        Log.d(tag, "Loading snapshots")
        snapshotsClient.load(forceReload).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(
                    tag,
                    "Snapshots loaded successfully. Data is stale? ${task.result.isStale}"
                )
                val snapshots = task.result.get()!!
                val result: List<Dictionary> = snapshots.map { snapshotMetadata ->
                    fromSnapshotMetadata(godot, snapshotMetadata)
                }.toList()
                snapshots.release()
                emitSignal(
                    godot,
                    GODOT_PLUGIN_NAME,
                    snapshotsLoaded,
                    Gson().toJson(result)
                )
            } else {
                Log.e(
                    tag,
                    "Failed to load snapshots. Cause: ${task.exception}",
                    task.exception
                )
                emitSignal(
                    godot,
                    GODOT_PLUGIN_NAME,
                    snapshotsLoaded,
                    Gson().toJson(null)
                )
            }
        }
    }

//    fun deleteSnapshot(snapshotId: String) {
//        var isDeleted = false
//        snapshotsClient.load(true).addOnSuccessListener { annotatedData ->
//            annotatedData.get()?.let { buffer ->
//                buffer
//                    .toList()
//                    .firstOrNull { it.snapshotId == snapshotId }?.let { snapshotMetadata ->
//                        Log.d(tag, "Deleting snapshot with id $snapshotId")
//                        snapshotsClient.delete(snapshotMetadata).addOnCompleteListener { task ->
//                            if (task.isSuccessful) {
//                                Log.d(
//                                    tag,
//                                    "Snapshot with id $snapshotId deleted successfully."
//                                )
//                                isDeleted = true
//                            } else {
//                                Log.e(
//                                    tag,
//                                    "Failed to delete snapshot with id $snapshotId. Cause: ${task.exception}",
//                                    task.exception
//                                )
//                            }
//                        }
//                    }
//            }
//        }
//        if (!isDeleted) {
//            Log.d(tag, "Snapshot with id $snapshotId not found!")
//        }
//    }

    private fun handleConflict(conflict: SnapshotConflict?) {
        conflict?.let {
            val snapshot = it.snapshot
            val fileName = snapshot.metadata.uniqueName
            val description = snapshot.metadata.description
            Log.e(
                tag, "Conflict with id ${conflict.conflictId} during saving of data with " +
                        "name $fileName and description ${description}."
            )
            emitSignal(
                godot,
                GODOT_PLUGIN_NAME,
                conflictEmitted,
                Gson().toJson(fromConflict(godot, it))
            )
        }
    }

    @UsedByGodot
    @RequiresApi(Build.VERSION_CODES.O)
    fun saveGame(fileName: String, data: String) {
        Log.d(tag, "Saving game $fileName")
        snapshotsClient.open(fileName, true)
            .addOnSuccessListener { result ->
                val snapshot = resolveAndGetSnapshot(snapshotsClient, result)
                snapshot.snapshotContents.writeBytes(data.toByteArray())
                val meta = SnapshotMetadataChange.Builder()
                    .setDescription(LocalDateTime.now().toString())
                    .build()
                snapshotsClient.commitAndClose(snapshot, meta)

                Log.d(tag, "Successfully updated snapshot $fileName")
                emitSignal(godot, GODOT_PLUGIN_NAME, gameSaved, true)
            }
            .addOnFailureListener {
                Log.e(tag, "Failed to open snapshot $fileName")
                emitSignal(godot, GODOT_PLUGIN_NAME, gameSaved, false)
            }
    }

    @UsedByGodot
    fun loadGame(fileName: String) {
        Log.d(tag, "Loading game $fileName")
        snapshotsClient.open(fileName, false)
            .addOnSuccessListener { result ->
                val snapshot = resolveAndGetSnapshot(snapshotsClient, result)
                val data = snapshot.snapshotContents.readFully();
                Log.d(tag, "Successfully loaded snapshot $fileName")
                emitSignal(godot, GODOT_PLUGIN_NAME, gameLoaded, data.toString(Charsets.UTF_8))
            }
            .addOnFailureListener {
                Log.e(tag, "Failed to load snapshot $fileName")
                emitSignal(godot, GODOT_PLUGIN_NAME, gameLoaded, null)
            }
    }

    @UsedByGodot
    fun deleteGame(fileName: String) {
        Log.d(tag, "Deleting game $fileName")
        snapshotsClient.open(fileName, false)
            .addOnSuccessListener { result ->
                val snapshot = resolveAndGetSnapshot(snapshotsClient, result)
                snapshotsClient.delete(snapshot.metadata)
                    .addOnSuccessListener {
                        Log.d(tag, "Successfully deleted snapshot $fileName")
                        emitSignal(godot, GODOT_PLUGIN_NAME, gameDeleted, true)
                    }
                    .addOnFailureListener {
                        Log.e(tag, "Failed to delete snapshot $fileName")
                        emitSignal(godot, GODOT_PLUGIN_NAME, gameDeleted, false)
                    }
            }
            .addOnFailureListener {
                Log.e(tag, "Snapshot $fileName not found")
                emitSignal(godot, GODOT_PLUGIN_NAME, gameDeleted, false)
            }
    }

    private fun resolveAndGetSnapshot(
        client: SnapshotsClient,
        result: SnapshotsClient.DataOrConflict<Snapshot>
    ): Snapshot {
        var snapshot: Snapshot
        if (!result.isConflict) {
            snapshot = result.data!!
        } else {
            val conflict = result.conflict!!
            snapshot = conflict.snapshot
            val conflictingSnapshot = conflict.conflictingSnapshot
            if (conflictingSnapshot.metadata.lastModifiedTimestamp > snapshot.metadata.lastModifiedTimestamp) {
                snapshot = conflictingSnapshot
            }
            client.resolveConflict(conflict.conflictId, snapshot)
        }
        return snapshot
    }
}