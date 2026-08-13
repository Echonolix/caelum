package net.echonolix.caelum.codegen.cpp

import java.nio.file.Path

public data class CppDiagnostic(
    val declaration: String,
    val reason: String,
)

public enum class CppPrimitive(public val cName: String, public val kotlinName: String, public val layout: String) {
    BOOL("bool", "Boolean", "JAVA_BYTE"),
    CHAR("char", "Byte", "JAVA_BYTE"),
    INT8("signed char", "Byte", "JAVA_BYTE"),
    UINT8("unsigned char", "Byte", "JAVA_BYTE"),
    INT16("short", "Short", "JAVA_SHORT"),
    UINT16("unsigned short", "Short", "JAVA_SHORT"),
    INT32("int", "Int", "JAVA_INT"),
    UINT32("unsigned int", "Int", "JAVA_INT"),
    INT64("long long", "Long", "JAVA_LONG"),
    UINT64("unsigned long long", "Long", "JAVA_LONG"),
    FLOAT("float", "Float", "JAVA_FLOAT"),
    DOUBLE("double", "Double", "JAVA_DOUBLE"),
}

public sealed interface CppType {
    public data object Void : CppType
    public data class Primitive(val value: CppPrimitive) : CppType
    public data class Pointer(val pointee: String, val isConst: Boolean = false) : CppType
    public data class Enum(val qualifiedName: String) : CppType
}

public data class CppParameter(val name: String, val type: CppType)

public data class CppFunction(
    val namespace: List<String>,
    val owner: String?,
    val name: String,
    val returnType: CppType,
    val parameters: List<CppParameter>,
    val isStatic: Boolean = false,
    val isConst: Boolean = false,
    val kind: Kind = Kind.FUNCTION,
) {
    public enum class Kind { FUNCTION, METHOD, CONSTRUCTOR, DESTRUCTOR }
    public val qualifiedName: String
        get() = (namespace + listOfNotNull(owner, name)).joinToString("::")
}

public data class CppEnum(
    val namespace: List<String>,
    val name: String,
    val values: List<Pair<String, Long>>,
) {
    public val qualifiedName: String get() = (namespace + name).joinToString("::")
}

public data class CppClass(
    val namespace: List<String>,
    val name: String,
    val constructors: List<CppFunction>,
    val destructor: CppFunction?,
    val methods: List<CppFunction>,
) {
    public val qualifiedName: String get() = (namespace + name).joinToString("::")
}

public data class CppModule(
    val functions: List<CppFunction>,
    val enums: List<CppEnum>,
    val classes: List<CppClass>,
    val diagnostics: List<CppDiagnostic>,
)

public data class CppCodegenConfig(
    val moduleName: String,
    val kotlinPackage: String,
    val kotlinObjectName: String = moduleName.toPascalCase() + "Native",
)

public data class CppGeneratedFiles(
    val header: Path,
    val source: Path,
    val kotlin: Path,
    val diagnostics: Path,
)

internal fun String.toPascalCase(): String = split(Regex("[^A-Za-z0-9]+"))
    .filter(String::isNotEmpty)
    .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    .ifEmpty { "Cpp" }
