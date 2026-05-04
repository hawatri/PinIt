package com.hawatri.pinit.viewmodel

import androidx.lifecycle.ViewModel
import com.hawatri.pinit.data.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PinItViewModel : ViewModel() {

    // The master list of all your notes
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun addNote(note: Note) {
        // Add the new note to the top of the list
        _notes.value = listOf(note) + _notes.value
    }

    fun updateNote(updatedNote: Note) {
        // Find the old note by ID and replace it with the new edited one
        _notes.value = _notes.value.map {
            if (it.id == updatedNote.id) updatedNote else it
        }
    }

    fun deleteNote(noteId: String) {
        _notes.value = _notes.value.filter { it.id != noteId }
    }

    fun togglePin(noteId: String) {
        _notes.value = _notes.value.map {
            if (it.id == noteId) it.copy(isPinned = !it.isPinned) else it
        }
    }
}