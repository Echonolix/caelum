package net.echonolix.caelum.assimp

import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.reflect.Modifier
import net.echonolix.caelum.NStruct

/** Emits generated layout facts for comparison with Clang's official ABI dump. */
public object GeneratedLayoutProbe {
    @JvmStatic
    public fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "Expected generated struct class names" }
        for (className in args.sorted()) {
            val clazz = Class.forName(className)
            val instance = clazz.getField("INSTANCE").get(null) as NStruct.Impl<*>
            val layout = instance.layout
            val members = clazz.declaredFields.asSequence()
                .filter { Modifier.isStatic(it.modifiers) }
                .mapNotNull { field ->
                    when {
                        field.name.endsWith("_valueVarHandle") -> field.name.removeSuffix("_valueVarHandle")
                        field.name.endsWith("_byteSize") -> field.name.removeSuffix("_byteSize")
                        else -> null
                    }
                }
                .distinct()
                .toList()
            val offsets = members.joinToString(",") { member ->
                "$member:${layout.byteOffset(groupElement(member))}"
            }
            println("${clazz.simpleName}|${layout.byteSize()}|${layout.byteAlignment()}|$offsets")
        }
    }
}
