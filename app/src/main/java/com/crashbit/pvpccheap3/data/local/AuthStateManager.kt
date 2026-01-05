package com.crashbit.pvpccheap3.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor centralitzat de l'estat d'autenticació.
 * Permet notificar a tota l'app quan cal fer re-login.
 */
@Singleton
class AuthStateManager @Inject constructor() {

    private val _authEvents = MutableSharedFlow<AuthEvent>(replay = 0)
    val authEvents: SharedFlow<AuthEvent> = _authEvents.asSharedFlow()

    suspend fun emitEvent(event: AuthEvent) {
        _authEvents.emit(event)
    }

    fun tryEmitEvent(event: AuthEvent) {
        _authEvents.tryEmit(event)
    }
}

sealed class AuthEvent {
    data object TokenRefreshed : AuthEvent()
    data object AuthenticationRequired : AuthEvent()
    data class AuthError(val message: String) : AuthEvent()
}
