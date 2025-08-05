package org.usvm.instrumentation.classloader

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.objectweb.asm.tree.ClassNode
import org.usvm.instrumentation.testcase.descriptor.StaticDescriptorsBuilder
import org.usvm.instrumentation.util.toByteArray
import java.lang.instrument.ClassDefinition
import java.security.SecureClassLoader

abstract class MetaClassLoader(
    parentClassLoader: ClassLoader?
): SecureClassLoader(parentClassLoader) {
    abstract val jcClasspath : JcClasspath
    abstract fun redefineClass(jClass: Class<*>, asmBody: ClassNode)
    abstract fun defineClass(name: String, classNode: ClassNode): Class<*>?

}