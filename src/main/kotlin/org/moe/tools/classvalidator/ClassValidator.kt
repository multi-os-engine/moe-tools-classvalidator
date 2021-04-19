package org.moe.tools.classvalidator

import org.moe.common.utils.classAndJarInputIterator
import org.moe.tools.classvalidator.natj.AddMissingNatJRegister
import org.moe.tools.classvalidator.substrate.CollectReflectionConfig
import org.moe.tools.classvalidator.substrate.ReflectionConfig
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import java.io.File
import java.nio.file.Path

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
            val reflectionConfig = ReflectionConfig()

            inputFiles.classAndJarInputIterator {
                val cr = ClassReader(it)

                val byteCode = processClass(cr) { next ->
                    next
                        .let { CollectReflectionConfig(reflectionConfig, it) }
                        .let(::AddMissingNatJRegister)
                }

                classSaver.save(byteCode)
            }

            reflectionConfig.save(outputDir.resolve(OUTPUT_REFLECTION).toFile())
        }
    }

    private inline fun processClass(reader: ClassReader, chain: (ClassVisitor) -> ClassVisitor): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)

        val header = chain(writer)
        reader.accept(header, 0)

        return writer.toByteArray()
    }

    const val OUTPUT_CLASSES = "classes"
    const val OUTPUT_REFLECTION = "reflection-config.json"
}