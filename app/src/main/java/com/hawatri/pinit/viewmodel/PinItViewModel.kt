package com.hawatri.pinit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PinItViewModel(private val dao: NoteDao) : ViewModel() {

    // Read notes directly from the Room database.
    // stateIn converts Room's Flow into a StateFlow that Compose can easily observe.
    val notes: StateFlow<List<Note>> = dao.getAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addNote(note: Note) {
        viewModelScope.launch {
            dao.insertNote(note)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            dao.updateNote(note)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            dao.deleteNote(noteId)
        }
    }

    // Notice this now takes the full Note object instead of just the ID,
    // making it easier to use Room's @Update annotation.
    fun togglePin(note: Note) {
        viewModelScope.launch {
            dao.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun toggleArchive(note: Note) {
        viewModelScope.launch {
            dao.updateNote(note.copy(isArchived = !note.isArchived))
        }
    }

    fun renameLabel(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
        viewModelScope.launch {
            notes.value.forEach { note ->
                if (oldName in note.labels) {
                    val updated = note.labels.map { if (it == oldName) trimmed else it }.distinct()
                    dao.updateNote(note.copy(labels = updated))
                }
            }
        }
    }

    fun deleteLabel(name: String) {
        viewModelScope.launch {
            notes.value.forEach { note ->
                if (name in note.labels) {
                    dao.updateNote(note.copy(labels = note.labels - name))
                }
            }
        }
    }
}

// Factory to tell Android how to create our ViewModel with the NoteDao dependency
class PinItViewModelFactory(private val dao: NoteDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PinItViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PinItViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}