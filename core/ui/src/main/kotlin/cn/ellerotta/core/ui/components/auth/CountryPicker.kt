package cn.ellerotta.core.ui.components.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ellerotta.core.ui.R
import java.text.Normalizer

@Composable
fun CountryPicker(countries: List<Country>, onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        CountryList(countries, onPick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CountryList(countries: List<Country>, onPick: (Country) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(countries, query) { countries.filter { it.matches(query) } }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.phone_search)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLargeIncreased,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("countrySearch"),
        )
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f).testTag("countries")) {
            shown.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }.forEach { (letter, group) ->
                stickyHeader(key = "header_$letter") {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            letter.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        )
                    }
                }
                items(group, key = { it.region + it.code }) { country ->
                    ListItem(
                        onClick = { onPick(country) },
                        leadingContent = { Text(country.flag, fontSize = 24.sp) },
                        trailingContent = { Text("+${country.code}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.testTag("country_${country.region}"),
                    ) {
                        Text(country.name)
                    }
                }
            }
        }
    }
}

private fun String.folded(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

private fun Country.matches(query: String): Boolean {
    val q = query.trim().removePrefix("+").folded()
    if (q.isEmpty()) return true
    return name.folded().contains(q) || region.lowercase() == q || code.toString().startsWith(q)
}
