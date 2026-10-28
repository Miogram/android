package cn.ellerotta.core.ui.components.maps

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar

@Composable
fun MapPin(modifier: Modifier = Modifier, lifted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val lift by animateDpAsState(if (lifted) (-12).dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pin")
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.size(10.dp, 4.dp).offset(y = 2.dp).background(colors.scrim.copy(alpha = 0.3f), CircleShape))
        Column(Modifier.offset(y = lift), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary, shadowElevation = 3.dp, border = androidx.compose.foundation.BorderStroke(2.dp, colors.surface)) {
                Icon(painterResource(R.drawable.ic_location_on), null, Modifier.padding(8.dp).size(24.dp))
            }
            Box(Modifier.size(3.dp, 12.dp).background(colors.primary))
        }
    }
}

@Composable
fun AvatarMarker(name: String, photo: ByteArray?, heading: Int?, modifier: Modifier = Modifier, seed: Long = name.hashCode().toLong()) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (heading != null) {
            val cone = colors.primary
            Canvas(Modifier.size(72.dp).rotate(heading.toFloat())) {
                drawArc(
                    Brush.radialGradient(listOf(cone.copy(alpha = 0.75f), cone.copy(alpha = 0f)), center = center, radius = size.minDimension / 2),
                    startAngle = -90f - 30f,
                    sweepAngle = 60f,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                )
            }
        }
        Surface(shape = CircleShape, color = colors.surface, shadowElevation = 3.dp, modifier = Modifier.border(2.dp, colors.primary, CircleShape)) {
            Avatar(name, photo, size = 40, seed = seed, modifier = Modifier.padding(3.dp))
        }
    }
}

@Composable
fun MyLocationDot(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(20.dp).background(colors.surface, CircleShape).padding(3.dp).background(colors.primary, CircleShape))
}
