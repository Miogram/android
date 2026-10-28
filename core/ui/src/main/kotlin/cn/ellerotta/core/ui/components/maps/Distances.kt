package cn.ellerotta.core.ui.components.maps

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.DistanceAmount
import cn.ellerotta.core.model.DistanceUnit
import cn.ellerotta.core.model.DistanceUnits
import cn.ellerotta.core.ui.R

@Composable
fun distanceUnits(): DistanceUnits = DistanceUnits.of(LocalConfiguration.current.locales[0].country)

@Composable
fun distanceLabel(metres: Int): String = distanceLabel(metres.toDouble())

@Composable
fun distanceLabel(metres: Double): String {
    val amount = DistanceAmount.of(metres, distanceUnits())
    val format = when (amount.unit) {
        DistanceUnit.Metres -> R.string.location_metres
        DistanceUnit.Kilometres -> R.string.location_kilometres
        DistanceUnit.Feet -> R.string.location_feet
        DistanceUnit.Miles -> R.string.location_miles
    }
    return stringResource(format, amount.text)
}

@Composable
fun updatedLabel(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.map_updated_now)
    seconds < 3600 -> (seconds / 60).toInt().let { pluralStringResource(R.plurals.map_updated_minutes, it, it) }
    else -> (seconds / 3600).toInt().let { pluralStringResource(R.plurals.map_updated_hours, it, it) }
}

@Composable
fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.location_period_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.location_period_hours, minutes / 60)
    else -> stringResource(R.string.route_hours_minutes, minutes / 60, minutes % 60)
}
