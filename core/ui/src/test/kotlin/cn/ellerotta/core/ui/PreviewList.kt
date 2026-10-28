package cn.ellerotta.core.ui

import androidx.compose.runtime.reflect.ComposableMethod
import androidx.compose.runtime.reflect.getDeclaredComposableMethod
import cn.ellerotta.core.ui.components.common.MioPreviews
import java.io.File
import java.net.JarURLConnection

internal fun mioPreviews(): List<Pair<String, ComposableMethod>> {
    val url = MioPreviews::class.java.getResource("MioPreviews.class")!!
    val names = if (url.protocol == "jar") {
        val jar = (url.openConnection() as JarURLConnection).jarFile
        jar.entries().toList().map { it.name }
    } else {
        val root = generateSequence(File(url.toURI())) { it.parentFile }.elementAt(7)
        root.walk().map { it.relativeTo(root).invariantSeparatorsPath }.toList()
    }
    return names
        .filter { it.startsWith("cn/ellerotta/core/ui/components/") && it.endsWith("PreviewsKt.class") }
        .map { it.removeSuffix(".class").replace('/', '.') }
        .sorted()
        .flatMap { name ->
            val type = Class.forName(name)
            type.declaredMethods
                .filter { it.isAnnotationPresent(MioPreviews::class.java) }
                .sortedBy { it.name }
                .map { "${type.simpleName}.${it.name}" to type.getDeclaredComposableMethod(it.name) }
        }
}
