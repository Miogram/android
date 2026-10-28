package app.miogram.feature.chat

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.ContactsRepository
import app.miogram.data.telegram.SafetyRepository
import app.miogram.feature.common.ReportFlow
import cn.ellerotta.core.model.PeerBar
import cn.ellerotta.core.model.ReportState
import cn.ellerotta.core.model.ReportTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ChatSafety(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val loads: Boolean,
    private val repository: SafetyRepository,
    private val contacts: ContactsRepository,
    private val work: (suspend () -> Unit) -> Job,
    private val contactAdded: () -> Unit,
) {
    private val blockedState = MutableStateFlow(false)
    private val barState = MutableStateFlow(PeerBar())
    private val reporting = ReportFlow(repository, work)

    val blocked: StateFlow<Boolean> = blockedState.asStateFlow()

    val bar: StateFlow<PeerBar> = barState.asStateFlow()

    val report: StateFlow<ReportState?> = reporting.ui

    fun load() {
        if (!loads || repository.isSelf(dialogKey)) return
        scope.launch {
            try {
                blockedState.value = repository.isBlocked(dialogKey)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            try {
                barState.value = repository.bar(dialogKey)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun apply(event: ChatEvent) {
        when {
            event is ChatEvent.Blocked && event.key == dialogKey -> blockedState.value = event.blocked
            event is ChatEvent.PeerBarChanged && event.key == dialogKey -> barState.value = event.bar
        }
    }

    fun block() {
        work {
            repository.block(dialogKey)
            blockedState.value = true
            barState.value = PeerBar()
        }
    }

    fun unblock() {
        work {
            repository.unblock(dialogKey)
            blockedState.value = false
        }
    }

    fun reportSpam() {
        barState.value = PeerBar()
        work {
            try {
                repository.reportSpam(dialogKey)
            } catch (e: Exception) {
                load()
                throw e
            }
        }
    }

    fun dismissBar() {
        barState.value = PeerBar()
        work { repository.hideBar(dialogKey) }
    }

    fun addContact(firstName: String, lastName: String, sharePhone: Boolean) {
        work {
            contacts.add(dialogKey, firstName, lastName, sharePhone)
            barState.update { it.copy(addContact = false) }
            contactAdded()
        }
    }

    fun reportChat() = reporting.open(ReportTarget.Peer(dialogKey))

    fun reportMessages(ids: List<Int>) {
        if (ids.isNotEmpty()) reporting.open(ReportTarget.Messages(dialogKey, ids))
    }

    fun reportChoose(id: String) = reporting.choose(id)

    fun reportComment(text: String) = reporting.comment(text)

    fun closeReport() = reporting.close()
}
