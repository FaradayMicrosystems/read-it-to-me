package com.faradaymicrosystems.nobodygenerator.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed interface JobState {
    data object Idle : JobState
    data class Running(val title: String, val stage: String, val done: Int, val total: Int) : JobState
    data class Finished(val title: String, val file: File, val skipped: Int) : JobState
    data class Failed(val message: String) : JobState
}

object Jobs {
    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state

    fun set(state: JobState) {
        _state.value = state
    }

    fun dismiss() {
        if (_state.value !is JobState.Running) _state.value = JobState.Idle
    }
}
