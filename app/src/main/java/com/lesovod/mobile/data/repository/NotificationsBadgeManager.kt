package com.lesovod.mobile.data.repository

import android.content.Context
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Счётчик непрочитанных уведомлений для значка на колокольчике — общий для всех экранов
 * (не завязан на конкретный NotificationsViewModel), чтобы значок был виден в любом месте
 * приложения, а не только на самом экране уведомлений.
 */
class NotificationsBadgeManager private constructor(context: Context) {
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    fun refresh() {
        scope.launch {
            repository.getUnreadNotificationsCount().onSuccess { _unreadCount.value = it }
        }
    }

    companion object {
        @Volatile
        private var instance: NotificationsBadgeManager? = null

        fun getInstance(context: Context): NotificationsBadgeManager =
            instance ?: synchronized(this) {
                instance ?: NotificationsBadgeManager(context.applicationContext).also { instance = it }
            }
    }
}
