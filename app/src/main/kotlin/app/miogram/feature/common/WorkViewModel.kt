package app.miogram.feature.common

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miogram.data.telegram.JoinBotCheckException
import cn.ellerotta.core.model.UiError
import cn.ellerotta.ktproto.session.RpcException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class WorkViewModel : ViewModel() {
    private val busyState = MutableStateFlow(0)
    private val errorState = MutableStateFlow<UiError?>(null)

    val busy: StateFlow<Int> = busyState.asStateFlow()
    val error: StateFlow<UiError?> = errorState.asStateFlow()

    fun dismissError() = errorState.update { null }

    protected fun fail(error: UiError) = errorState.update { error }

    protected fun work(block: suspend () -> Unit): Job = workWith(block)

    protected fun refresh(shown: Boolean, block: suspend () -> Unit): Job = refreshWith(shown, block)

    internal fun refreshWith(shown: Boolean, block: suspend () -> Unit): Job {
        if (!shown) return workWith(block)
        return viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "refresh failed with data on screen: ${e.message}")
            }
        }
    }

    internal fun workWith(block: suspend () -> Unit): Job = viewModelScope.launch {
        busyState.update { it + 1 }
        errorState.update { null }
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcException) {
            fail(UiError.Rpc(e.code, e.errorMessage))
        } catch (e: JoinBotCheckException) {
            fail(UiError.JoinBotCheck)
        } catch (e: Exception) {
            fail(UiError.Message(e.message ?: e::class.simpleName.orEmpty()))
        } finally {
            busyState.update { it - 1 }
        }
    }

    private companion object {
        const val TAG = "refresh"
    }
}
