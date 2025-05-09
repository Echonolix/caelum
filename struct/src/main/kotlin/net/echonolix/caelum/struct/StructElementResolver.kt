package net.echonolix.caelum.struct

import com.squareup.kotlinpoet.ClassName
import net.echonolix.caelum.NGroup
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.NStruct
import net.echonolix.caelum.NUnion
import net.echonolix.caelum.codegen.api.CBasicType
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.ElementResolver
import net.echonolix.caelum.codegen.api.ctx.resolveTypedElement
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.superclasses

class StructElementResolver : ElementResolver.Base() {
    private val basicTypeLookup = CBasicType.ENTRIES.filter {
        it.caelumCoreTypeName is ClassName
    }.associate {
        (it.caelumCoreTypeName as ClassName).canonicalName to it.cType
    }

    private val KClass<*>.packageName: String
        get() = this.java.packageName

    private fun resolveMember(property: KProperty<*>): CType.Group.Member {
        val rType = property.returnType
        val classifier = rType.classifier as KClass<*>
        var memberType: CType? = basicTypeLookup[classifier.qualifiedName]
        when {
            classifier == NPointer::class -> {
                memberType = CType.Pointer {
                    resolveTypedElement<CType>(
                        (rType.arguments.first().type!!.classifier!! as KClass<*>).qualifiedName!!
                    )
                }
            }
            classifier.isSubclassOf(NGroup::class) -> {
                memberType = resolveTypedElement<CType.Group>(
                    classifier.qualifiedName!!
                )
            }
        }
        memberType ?: error("Unsupported type: $rType")
        memberType.tags.set(PackageNameTag(classifier.packageName))
        return CType.Group.Member(property.name, memberType)
    }

    override fun resolveElementImpl(input: String): CElement {
        val thisClass = Class.forName(input).kotlin
        basicTypeLookup[thisClass.qualifiedName]?.let {
            return it
        }
        val members = thisClass.declaredMemberProperties.map {
            resolveMember(it)
        }
        val groupType = when (thisClass.superclasses.first()) {
            NStruct::class -> CType.Struct(thisClass.simpleName!!, members)
            NUnion::class -> CType.Union(thisClass.simpleName!!, members)
            else -> error("Unsupported type: $thisClass")
        }
        groupType.tags.set(PackageNameTag(thisClass.packageName))
        return groupType
    }
}