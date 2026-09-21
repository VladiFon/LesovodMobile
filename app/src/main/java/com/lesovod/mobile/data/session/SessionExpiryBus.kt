package com.lesovod.mobile.data.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Сигнал о том, что сервер ответил 401 (сессия истекла/токен недействителен).
 * Сетевой слой не может сам перейти на экран входа — он только сообщает об этом
 * сюда, а LesovodNavGraph уже переводит пользователя на Screen.Login.
 */
object SessionExpiryBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyExpired() {
        _events.tryEmit(Unit)
    }
}
