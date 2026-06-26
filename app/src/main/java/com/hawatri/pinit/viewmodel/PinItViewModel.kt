package com.hawatri.pinit.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hawatri.pinit.data.AppPreferences
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PinItViewModel(
    private val dao: NoteDao,
    private val appContext: Context
) : ViewModel() {

    // Read notes directly from the Room database.
    // stateIn converts Room's Flow into a StateFlow that Compose can easily observe.
    val notes: StateFlow<List<Note>> = dao.getAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Bumps the "you have unsaved changes" sentinel that drives the
    // unbacked-changes banner on Home and the inline warning in Settings.
    // Restore paths must NOT call this — they bump lastBackupAt instead.
    private fun markDirty() {
        AppPreferences.setLastModifiedNow(appContext)
    }

    fun addNote(note: Note) {
        viewModelScope.launch {
            dao.insertNote(note)
            markDirty()
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            dao.updateNote(note)
            markDirty()
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            dao.deleteNote(noteId)
            markDirty()
        }
    }

    // Notice this now takes the full Note object instead of just the ID,
    // making it easier to use Room's @Update annotation.
    fun togglePin(note: Note) {
        viewModelScope.launch {
            dao.updateNote(note.copy(isPinned = !note.isPinned))
            markDirty()
        }
    }

    fun toggleArchive(note: Note) {
        viewModelScope.launch {
            dao.updateNote(note.copy(isArchived = !note.isArchived))
            markDirty()
        }
    }

    fun renameLabel(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
        viewModelScope.launch {
            var changed = false
            notes.value.forEach { note ->
                if (oldName in note.labels) {
                    val updated = note.labels.map { if (it == oldName) trimmed else it }.distinct()
                    dao.updateNote(note.copy(labels = updated))
                    changed = true
                }
            }
            if (changed) markDirty()
        }
    }

    fun deleteLabel(name: String) {
        viewModelScope.launch {
            var changed = false
            notes.value.forEach { note ->
                if (name in note.labels) {
                    dao.updateNote(note.copy(labels = note.labels - name))
                    changed = true
                }
            }
            if (changed) markDirty()
        }
    }
}

// Factory to tell Android how to create our ViewModel with the NoteDao dependency
class PinItViewModelFactory(
    private val dao: NoteDao,
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PinItViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PinItViewModel(dao, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}