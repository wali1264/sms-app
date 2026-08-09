package com.example.ui.messages

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.MessageRecord
import com.example.repository.AppRepository
import com.example.whatsapp.WhatsappHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessagesViewModel(private val repository: AppRepository) : ViewModel() {

    val messages: StateFlow<List<MessageRecord>> = repository.allMessagesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun retryMessage(messageId: Long) {
        viewModelScope.launch {
            repository.retryMessage(messageId)
        }
    }

    fun openWhatsappForMessage(context: Context, message: MessageRecord) {
        WhatsappHelper.sendWhatsappMessage(context, message.phoneNumber, message.messageText)
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessagesViewModel(repository) as T
        }
    }
}
