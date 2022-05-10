package org.moe.tools.classvalidator

import org.objectweb.asm.ClassReader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

internal class ClassSaver(
    private val outputDir: Path
) {
    fun save(bytecode: ByteArray?) {
        if (bytecode == null) {
            return
        }

        val cr = ClassReader(bytecode)
        // In jar replacement
        val fileSystem = FileSystems.newFileSystem(outputDir, null)
        fileSystem.use {
            val outputFile = it.getPath(cr.className + ".class")
            Files.write(outputFile, bytecode)
        }
    }
}
