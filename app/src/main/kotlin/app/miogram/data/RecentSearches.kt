package app.miogram.data

import android.content.Context
import cn.ellerotta.core.model.withRecentSearch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentSearches @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())

    val queries: StateFlow<List<String>> = state.asStateFlow()

    fun add(query: String) = change { it.withRecentSearch(query) }

    fun remove(query: String) = change { list -> list.filter { it != query } }

    fun clear() = change { emptyList() }

    private fun change(transform: (List<String>) -> List<String>) {
        state.update(transform)
        prefs.edit().putString(KEY, state.value.joinToString("\n")).apply()
    }

    private companion object {
        const val PREFS = "recent_searches"
        const val KEY = "queries"
    }
}
