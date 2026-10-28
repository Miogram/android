package cn.ellerotta.core.ui.components.chat

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Xml
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import org.xmlpull.v1.XmlPullParser

internal class TgvShape(val path: Path, val strokeWidth: Float?, val alpha: Float)

internal class TgvImage(val width: Float, val height: Float, val shapes: List<TgvShape>) {
    fun draw(canvas: Canvas, scale: Float, dx: Float, dy: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        for (shape in shapes) {
            paint.style = if (shape.strokeWidth != null) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = shape.strokeWidth ?: 0f
            paint.alpha = (shape.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawPath(shape.path, paint)
        }
        canvas.restore()
    }
}

internal object Tgv {
    fun isGzip(bytes: ByteArray) = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    fun parse(bytes: ByteArray): TgvImage {
        val svg = if (isGzip(bytes)) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(svg), null)
        return Reader(parser).read()
    }

    private data class Look(
        val fill: Boolean = true,
        val stroke: Boolean = false,
        val strokeWidth: Float = 1f,
        val opacity: Float = 1f,
        val fillOpacity: Float = 1f,
        val strokeOpacity: Float = 1f,
        val evenOdd: Boolean = false,
    )

    private class Frame(val matrix: Matrix, val look: Look)

    private class Reader(private val parser: XmlPullParser) {
        private val shapes = ArrayList<TgvShape>()
        private val classes = HashMap<String, Map<String, String>>()
        private var width = 0f
        private var height = 0f

        fun read(): TgvImage {
            val stack = ArrayDeque<Frame>()
            var root = false
            var skipping = 0
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.substringAfter(':')
                        val parent = stack.lastOrNull() ?: Frame(Matrix(), Look())
                        if (skipping > 0) {
                            skipping++
                        } else if (name in SKIPPED || attribute("id") == "bounds" || attribute("display") == "none") {
                            skipping = 1
                        } else if (name == "style") {
                            parseCss(readText())
                            event = parser.eventType
                            continue
                        } else {
                            val matrix = Matrix(parent.matrix)
                            if (name == "svg" && !root) {
                                root = true
                                frame(matrix)
                            }
                            attribute("transform")?.let { matrix.preConcat(transformOf(it)) }
                            val look = lookOf(parent.look)
                            stack.addLast(Frame(matrix, look))
                            shapeOf(name)?.let { add(it, matrix, look) }
                        }
                    }
                    XmlPullParser.END_TAG -> if (skipping > 0) skipping-- else stack.removeLastOrNull()
                }
                event = parser.next()
            }
            require(root) { "not an svg" }
            return TgvImage(width, height, shapes)
        }

        private fun frame(matrix: Matrix) {
            val box = attribute("viewBox")?.let(::numbers)?.takeIf { it.size == 4 && it[2] > 0f && it[3] > 0f }
            val w = attribute("width")?.let(::length)
            val h = attribute("height")?.let(::length)
            width = w ?: box?.get(2) ?: DEFAULT_SIZE
            height = h ?: box?.get(3) ?: DEFAULT_SIZE
            if (box != null) {
                matrix.preScale(width / box[2], height / box[3])
                matrix.preTranslate(-box[0], -box[1])
            }
        }

        private fun add(path: Path, matrix: Matrix, paint: Look) {
            if (paint.evenOdd) path.fillType = Path.FillType.EVEN_ODD
            path.transform(matrix)
            if (paint.fill) shapes += TgvShape(path, null, paint.opacity * paint.fillOpacity)
            if (paint.stroke) {
                val scaled = matrix.mapRadius(paint.strokeWidth)
                shapes += TgvShape(path, scaled, paint.opacity * paint.strokeOpacity)
            }
        }

        private fun shapeOf(name: String): Path? = when (name) {
            "path" -> attribute("d")?.let { d -> runCatching { PathParser().parsePathString(d).toPath().asAndroidPath() }.getOrNull() }
            "rect" -> {
                val w = float("width")
                val h = float("height")
                if (w <= 0f || h <= 0f) {
                    null
                } else {
                    val rx = attribute("rx")?.let(::length) ?: attribute("ry")?.let(::length) ?: 0f
                    val ry = attribute("ry")?.let(::length) ?: rx
                    Path().apply { addRoundRect(RectF(float("x"), float("y"), float("x") + w, float("y") + h), rx, ry, Path.Direction.CW) }
                }
            }
            "circle" -> float("r").takeIf { it > 0f }?.let { r -> Path().apply { addCircle(float("cx"), float("cy"), r, Path.Direction.CW) } }
            "ellipse" -> {
                val rx = float("rx")
                val ry = float("ry")
                if (rx <= 0f || ry <= 0f) null else Path().apply { addOval(RectF(float("cx") - rx, float("cy") - ry, float("cx") + rx, float("cy") + ry), Path.Direction.CW) }
            }
            "line" -> Path().apply {
                moveTo(float("x1"), float("y1"))
                lineTo(float("x2"), float("y2"))
            }
            "polyline", "polygon" -> attribute("points")?.let(::numbers)?.takeIf { it.size >= 4 }?.let { points ->
                Path().apply {
                    moveTo(points[0], points[1])
                    for (i in 2 until points.size - 1 step 2) lineTo(points[i], points[i + 1])
                    if (name == "polygon") close()
                }
            }
            else -> null
        }

        private fun lookOf(parent: Look): Look {
            val declared = HashMap<String, String>()
            attribute("class")?.split(' ')?.forEach { name -> classes[name]?.let(declared::putAll) }
            PAINT_ATTRIBUTES.forEach { key -> attribute(key)?.let { declared[key] = it } }
            attribute("style")?.let { declared.putAll(declarations(it)) }
            var paint = parent
            declared["fill"]?.let { paint = paint.copy(fill = it.trim() != "none") }
            declared["stroke"]?.let { paint = paint.copy(stroke = it.trim() != "none") }
            declared["stroke-width"]?.let { value -> length(value)?.let { paint = paint.copy(strokeWidth = it) } }
            declared["opacity"]?.trim()?.toFloatOrNull()?.let { paint = paint.copy(opacity = parent.opacity * it) }
            declared["fill-opacity"]?.trim()?.toFloatOrNull()?.let { paint = paint.copy(fillOpacity = it) }
            declared["stroke-opacity"]?.trim()?.toFloatOrNull()?.let { paint = paint.copy(strokeOpacity = it) }
            declared["fill-rule"]?.let { paint = paint.copy(evenOdd = it.trim() == "evenodd") }
            return paint
        }

        private fun parseCss(css: String) {
            Regex("""([^{}]+)\{([^}]*)\}""").findAll(css).forEach { rule ->
                val body = declarations(rule.groupValues[2])
                rule.groupValues[1].split(',').map { it.trim() }.filter { it.startsWith('.') }.forEach { selector ->
                    classes[selector.drop(1)] = classes[selector.drop(1)].orEmpty() + body
                }
            }
        }

        private fun declarations(style: String): Map<String, String> = style.split(';').mapNotNull { part ->
            val colon = part.indexOf(':')
            if (colon < 0) null else part.substring(0, colon).trim() to part.substring(colon + 1).trim()
        }.toMap()

        private fun readText(): String {
            val text = StringBuilder()
            var event = parser.next()
            while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) text.append(parser.text)
                event = parser.next()
            }
            parser.next()
            return text.toString()
        }

        private fun attribute(name: String): String? = parser.getAttributeValue(null, name)

        private fun float(name: String): Float = attribute(name)?.let(::length) ?: 0f
    }

    fun transformOf(value: String): Matrix {
        val matrix = Matrix()
        Regex("""(matrix|translate|scale|rotate|skewX|skewY)\s*\(([^)]*)\)""").findAll(value).forEach { match ->
            val v = numbers(match.groupValues[2])
            val step = Matrix()
            when (match.groupValues[1]) {
                "matrix" -> if (v.size == 6) step.setValues(floatArrayOf(v[0], v[2], v[4], v[1], v[3], v[5], 0f, 0f, 1f))
                "translate" -> step.setTranslate(v.getOrElse(0) { 0f }, v.getOrElse(1) { 0f })
                "scale" -> step.setScale(v.getOrElse(0) { 1f }, v.getOrElse(1) { v.getOrElse(0) { 1f } })
                "rotate" -> if (v.size >= 3) step.setRotate(v[0], v[1], v[2]) else step.setRotate(v.getOrElse(0) { 0f })
                "skewX" -> step.setSkew(kotlin.math.tan(Math.toRadians(v.getOrElse(0) { 0f }.toDouble())).toFloat(), 0f)
                "skewY" -> step.setSkew(0f, kotlin.math.tan(Math.toRadians(v.getOrElse(0) { 0f }.toDouble())).toFloat())
            }
            matrix.preConcat(step)
        }
        return matrix
    }

    private val NUMBER = Regex("""[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")

    private fun numbers(value: String): List<Float> = NUMBER.findAll(value).mapNotNull { it.value.toFloatOrNull() }.toList()

    private fun length(value: String): Float? = value.trim().removeSuffix("px").toFloatOrNull()

    private val SKIPPED = setOf("defs", "clipPath", "mask", "symbol", "title", "desc", "metadata", "linearGradient", "radialGradient", "pattern", "filter", "text")
    private val PAINT_ATTRIBUTES = listOf("fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity", "fill-rule")
    private const val DEFAULT_SIZE = 100f
}
