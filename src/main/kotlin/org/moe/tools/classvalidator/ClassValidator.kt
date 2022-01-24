package org.moe.tools.classvalidator

import org.moe.common.utils.classAndJarInputIterator
import org.moe.tools.classvalidator.natj.AddMissingAnnotations
import org.moe.tools.classvalidator.natj.AddMissingNatJRegister
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ClassValidator {
    fun process(
        inputFiles: Set<File>,
        outputDir: Path,
        classpath: Set<File>,
    ) {
        ContextClassLoaderHolder(
            ChildFirstClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray())
        ).use {
            val classSaver = ClassSaver(outputDir.resolve(OUTPUT_CLASSES))

            inputFiles.classAndJarInputIterator { _, inputStream ->
                val cr = ClassReader(inputStream)

                val chain = mutableListOf<ClassVisitor>()
                fun ClassVisitor.chain(nextBuilder: (ClassVisitor) -> ClassVisitor): ClassVisitor {
                    val next = nextBuilder(this)
                    chain.add(next)
                    return next
                }

                val byteCode = processClass(cr) { next ->
                    next
                        .chain(::AddMissingAnnotations)
                        .chain(::AddMissingNatJRegister)
                }

                // Only save modified class
                if (chain.any { it is ClassModifier && it.modified }) {
                    classSaver.save(byteCode)
                }
            }
            // TODO: This is really hacky, since r8 seems to only understand jars, not dirs
            val inputDirectory = outputDir.resolve(OUTPUT_CLASSES).toFile()
            val outputZipFile = outputDir.resolve("output.jar").toFile()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                inputDirectory.walkTopDown().forEach { file ->
                    val zipFileName = file.absolutePath.removePrefix(inputDirectory.absolutePath).removePrefix("/")
                    val entry = ZipEntry( "$zipFileName${(if (file.isDirectory) "/" else "" )}")
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().copyTo(zos)
                    }
                }
            }
        }
    }

    private inline fun processClass(reader: ClassReader, chain: (ClassVisitor) -> ClassVisitor): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)

        if (reader.className.startsWith("java/")) {
            // We don't want to process classes from java.*
            reader.accept(writer, 0)
        } else {
            val header = chain(writer)
            reader.accept(header, 0)
        }

        return writer.toByteArray()
    }

    const val OUTPUT_CLASSES = "classes"
}
