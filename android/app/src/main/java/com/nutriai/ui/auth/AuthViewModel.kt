package com.nutriai.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(val loading: Boolean = false, val error: String? = null)

/** Turns raw exceptions (e.g. "HTTP 409") into calm, human messages. */
fun friendlyAuthError(t: Throwable?): String = when (t) {
    is retrofit2.HttpException -> {
        val serverMsg = runCatching {
            t.response()?.errorBody()?.string()?.let { org.json.JSONObject(it).optString("error") }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        when (t.code()) {
            409 -> "An account with this email already exists. Try logging in instead."
            401 -> "Incorrect email or password. Please try again."
            400 -> serverMsg ?: "Please check your details and try again."
            429 -> "Too many attempts - please wait a minute and try again."
            in 500..599 -> "The server is waking up (the free server can take ~30s on first open). Please try again in a moment."
            else -> serverMsg ?: "Something went wrong. Please try again."
        }
    }
    is java.net.UnknownHostException, is java.io.IOException ->
        "No internet connection. Check your network and try again."
    else -> t?.message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP") } ?: "Something went wrong. Please try again."
}

/** Forgot-password flow: verify (email+DOB) then set a new password. */
data class ForgotState(
    val loading: Boolean = false,
    val verified: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Logs in, then routes: onNeedsProfile if the profile is missing/incomplete, else onHome. */
    fun login(email: String, password: String, onHome: () -> Unit, onNeedsProfile: () -> Unit) {
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            val result = repository.login(email.trim(), password)
            if (result.isSuccess) {
                val profile = repository.getProfile().getOrNull()
                _state.value = AuthUiState()
                if (profile?.sensitive == null) onNeedsProfile() else onHome()
            } else {
                _state.value = AuthUiState(error = friendlyAuthError(result.exceptionOrNull()))
            }
        }
    }

    fun register(email: String, password: String, first: String, last: String, onSuccess: () -> Unit) {
        execute(onSuccess) { repository.register(email.trim(), password, first.trim(), last.trim()) }
    }

    private val _forgot = MutableStateFlow(ForgotState())
    val forgot: StateFlow<ForgotState> = _forgot.asStateFlow()

    fun resetForgot() { _forgot.value = ForgotState() }

    /** Step 1 - verify the account by email + date of birth (yyyy-MM-dd). */
    fun verifyIdentity(email: String, dob: String) {
        _forgot.value = ForgotState(loading = true)
        viewModelScope.launch {
            val r = repository.forgotVerify(email, dob)
            _forgot.value = when {
                r.isSuccess && r.getOrDefault(false) -> ForgotState(verified = true)
                r.isSuccess -> ForgotState(error = "That email and date of birth don't match an account.")
                else -> ForgotState(error = friendlyAuthError(r.exceptionOrNull()))
            }
        }
    }

    /** Step 2 - set the new password (server re-verifies email + DOB). */
    fun resetPassword(email: String, dob: String, newPassword: String, onDone: () -> Unit) {
        _forgot.value = _forgot.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.resetPassword(email, dob, newPassword)
            if (r.isSuccess) {
                _forgot.value = ForgotState()
                onDone()
            } else {
                _forgot.value = _forgot.value.copy(loading = false, error = friendlyAuthError(r.exceptionOrNull()))
            }
        }
    }

    private fun execute(onSuccess: () -> Unit, block: suspend () -> Result<Unit>) {
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            val result = block()
            _state.value = if (result.isSuccess) {
                AuthUiState()
            } else {
                AuthUiState(error = friendlyAuthError(result.exceptionOrNull()))
            }
            if (result.isSuccess) onSuccess()
        }
    }
}
