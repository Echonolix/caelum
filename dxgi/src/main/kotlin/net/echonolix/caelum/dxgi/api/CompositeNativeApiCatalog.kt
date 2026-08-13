package net.echonolix.caelum.dxgi.api

import java.lang.foreign.FunctionDescriptor

/**
 * An ordered native API profile with one primary catalog and audited dependency
 * catalogs. The primary owns functions/interfaces; dependencies only resolve
 * referenced named types. Duplicate type names must have identical models.
 */
public class CompositeNativeApiCatalog(
    public val primary: NativeApiCatalog,
    public val dependencies: List<NativeApiCatalog> = emptyList(),
) {
    public val catalogs: List<NativeApiCatalog> = listOf(primary) + dependencies

    init {
        val duplicateApis = catalogs.groupingBy { it.api }.eachCount().filterValues { it > 1 }.keys
        require(duplicateApis.isEmpty()) { "Composite native catalog contains duplicate APIs: $duplicateApis" }
        validateCompatibleTypes("record") { it.records }
        validateCompatibleTypes("enum") { it.enums }
        validateCompatibleTypes("typedef") { it.typedefs }
    }

    public val types: NativeTypeParser = primary.nativeTypes(dependencies)

    public fun interfaceDeclaration(name: String): NativeInterfaceDeclaration? = primary.interfaceDeclaration(name)

    public fun function(name: String): NativeFunctionDeclaration? = primary.function(name)

    public fun functionDescriptor(name: String): FunctionDescriptor =
        primary.requireFunction(name).functionDescriptor(types)

    public fun comMethodDescriptor(interfaceName: String, methodName: String): FunctionDescriptor =
        primary.requireInterface(interfaceName).requireMethod(methodName).functionDescriptor(types)

    public fun invoker(): NativeApiInvoker = NativeApiInvoker(primary, types = types)

    private fun <T> validateCompatibleTypes(
        kind: String,
        declarations: (NativeApiCatalog) -> Map<String, T>,
    ) {
        val seen = mutableMapOf<String, Pair<String, T>>()
        for (catalog in catalogs) {
            for ((name, declaration) in declarations(catalog)) {
                val previous = seen.putIfAbsent(name, catalog.api to declaration)
                if (previous != null && !canonicallyCompatible(previous.second, declaration)) {
                    throw NativeSchemaException(
                        "Conflicting $kind '$name' in composite APIs '${previous.first}' and '${catalog.api}'",
                    )
                }
            }
        }
    }

    private fun <T> canonicallyCompatible(left: T, right: T): Boolean = when {
        left is NativeRecordDeclaration && right is NativeRecordDeclaration ->
            left.copy(header = null, sourceLine = null) == right.copy(header = null, sourceLine = null)
        left is NativeEnumDeclaration && right is NativeEnumDeclaration ->
            left.copy(header = null, sourceLine = null) == right.copy(header = null, sourceLine = null)
        left is NativeTypedefDeclaration && right is NativeTypedefDeclaration ->
            left.copy(header = null, sourceLine = null) == right.copy(header = null, sourceLine = null)
        else -> left == right
    }
}
