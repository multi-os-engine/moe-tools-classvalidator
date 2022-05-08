package org.moe.tools.classvalidator.proguard

import org.moe.common.utils.classAndJarInputIterator
import org.moe.tools.classvalidator.ChildFirstClassLoader
import org.moe.tools.classvalidator.ContextClassLoaderHolder
import org.objectweb.asm.ClassReader
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object ProguardCollector {
    @JvmStatic
    fun process(
        inputFiles: Set<File>,
        outputFile: Path,
        classpath: Set<File>,
    ) {
        val toAdd = HashSet<String>()
        ContextClassLoaderHolder(
            ChildFirstClassLoader((classpath + inputFiles).map { it.toURI().toURL() }.toTypedArray())
        ).use {

            inputFiles.classAndJarInputIterator { _, inputStream ->
                val cr = ClassReader(inputStream)
                cr.accept(CollectProguardKeeps(
                     toAdd = toAdd,
                ), 0)
            }
        }
        val string = toAdd.joinToString(System.lineSeparator())
        Files.write(outputFile, string.toByteArray(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }
}
