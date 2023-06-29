package org.moe.tools.classvalidator

import org.moe.common.utils.classpathIterator
import org.moe.tools.classvalidator.natj.AddMissingAnnotations
import org.moe.tools.classvalidator.natj.AddMissingNatJRegister
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import java.io.File

object ClassValidator {
    fun process(
            inputFiles: Set<File>,
            classpath: Set<File>,
    ) {
            val classSavers = mutableListOf<ClassSaver>()
            ChildFirstClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray()).use { loader ->
                ContextClassLoaderHolder(loader).use {
                    inputFiles.forEach { jar ->
                        val classSaver = ClassSaver(jar.absoluteFile.toPath())
                        jar.classpathIterator({ _, inputStream ->
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
                                classSaver.add(byteCode)
                            }
                        }, { it.endsWith(".class") })
                        classSavers.add(classSaver)
                    }            }
        }
        classSavers.forEach { it.save() }
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
}
