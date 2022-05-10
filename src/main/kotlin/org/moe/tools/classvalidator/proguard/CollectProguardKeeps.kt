package org.moe.tools.classvalidator.proguard

import org.moe.tools.classvalidator.natj.NatJRuntime
import org.objectweb.asm.*
import java.lang.reflect.Modifier

class CollectProguardKeeps(
        private val toAdd: MutableSet<String>,
        next: ClassVisitor? = null,
) : ClassVisitor(Opcodes.ASM5, next) {

    private var isNatJBindingClass: Boolean = false

    private lateinit var name: String
    private var superName: String? = null
    private var interfaces: Array<out String>? = null
    private var isInterface: Boolean = false
    private var isObjCProtocolBindingClass: Boolean = false

    override fun visit(version: Int, access: Int, name: String, signature: String?,
                       superName: String?, interfaces: Array<out String>?) {
        this.name = name
        this.superName = superName
        this.interfaces = interfaces
        this.isInterface = Modifier.isInterface(access)

        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        if (NatJRuntime.Annotations.OBJC_PROTOCOL_NAME_DESC == descriptor) {
            isObjCProtocolBindingClass = true
        }

        return super.visitAnnotation(descriptor, visible)
    }


    override fun visitMethod(access: Int, name: String, descriptor: String,
                             signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        // Skip native methods and abstract methods
        if (Modifier.isNative(access)) return mv
        if (isInterface && Modifier.isAbstract(access)) return mv
        return MethodInspector(
                toAdd = toAdd,
                name = name,
                methodDescriptor = descriptor,
                isProtocolClass = isNatJBindingClass && isInterface && isObjCProtocolBindingClass,
                isInterfaceDefaultMethod = isInterface && !Modifier.isStatic(access) && !Modifier.isAbstract(access),
                next = mv
        )

    }


    private class MethodInspector(
            private val toAdd: MutableSet<String>,
            private val name: String,
            private val methodDescriptor: String,
            private val isProtocolClass: Boolean,
            private val isInterfaceDefaultMethod: Boolean,
            next: MethodVisitor?,
    ) : MethodVisitor(Opcodes.ASM5, next) {

        override fun visitLdcInsn(value: Any) {
            when(value) {
                is Type -> {

                    when (value.sort) {
                        Type.OBJECT -> {
                            // We are using reflection in our code, check if it's opaque ptr.
                            if (NatJRuntime.isOpaquePtrDescendant(value.internalName)) {
                                toAdd.add("-keep class ${value.internalName.replace("/", ".")}")
                                toAdd.add("-keep class ${value.internalName.replace("/", ".")}\$Impl {*;}")
                            }
                        }
                        Type.METHOD -> {
                            // TODO: add this
                        }
                    }
                }
                is Handle -> {
                    // TODO: add this
                }
                is ConstantDynamic -> {
                    // TODO: add this
                }
            }

            super.visitLdcInsn(value)
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
            // We are calling a method, check if the return type is an opaque ptr. If so, we need to export it to reflection
            // because the value might be returned from native via reflection.

            Type.getMethodType(descriptor).returnType.let { type ->
                if (type.sort == Type.OBJECT && NatJRuntime.isOpaquePtrDescendant(type.internalName)) {
                    toAdd.add("-keep class ${type.internalName.replace("/", ".")}")
                    toAdd.add("-keep class ${type.internalName.replace("/", ".")}\$Impl {*;}")
                }
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

        override fun visitEnd() {
            if (isProtocolClass && isInterfaceDefaultMethod) {
                // Skip protocol optional default methods

            } else {
                // This method could be called from native, we inspect its parameters to see if opaque ptr
                // is used. If so, we need to export the opaque ptr impl class to reflection as well.
                Type.getMethodType(methodDescriptor).argumentTypes.forEach { type ->
                    if (type.sort == Type.OBJECT && NatJRuntime.isOpaquePtrDescendant(type.internalName)) {
                        toAdd.add("-keep class ${type.internalName.replace("/", ".")}")
                        toAdd.add("-keep class ${type.internalName.replace("/", ".")}\$Impl {*;}")
                    }
                }
            }
            super.visitEnd()
        }
    }
}