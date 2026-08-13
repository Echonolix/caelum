package net.echonolix.caelum.dxgi.api

import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

public class NativeApiCatalogTest {
    @Test
    public fun `indexes declarations and builds Windows x64 descriptors`() {
        val catalog = NativeApiCatalog.parse(FIXTURE)

        assertEquals("fixture", catalog.api)
        assertEquals(3, catalog.requireInterface("ITest").vtableSize)
        assertEquals("AddRef", catalog.requireInterface("ITest").requireMethod(1).name)
        assertEquals("fixture.dll", catalog.requireFunction("CreateFixture").dll)
        assertEquals("2", catalog.constant("FIXTURE_VALUE")?.value)
        assertIs<NativeSchemaArray>(catalog.sourceSet?.get("defines"))

        val types = catalog.nativeTypes()
        assertEquals(ValueLayout.JAVA_INT, types.layout("HRESULT"))
        assertEquals(ValueLayout.ADDRESS, types.layout("ITest *"))
        assertEquals(16, types.layout("FIXTURE_PAIR[2]").byteSize())

        val method = catalog.requireInterface("ITest").requireMethod("QueryInterface")
        val descriptor = method.functionDescriptor(types)
        assertEquals(ValueLayout.JAVA_INT, descriptor.returnLayout().orElseThrow())
        assertEquals(listOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS), descriptor.argumentLayouts())
    }

    @Test
    public fun `uses schema offsets including packed fields`() {
        val catalog = NativeApiCatalog.parse(PACKED_FIXTURE)
        val layout = catalog.nativeTypes().layout("PACKED")

        assertEquals(5, layout.byteSize())
        assertEquals(1, layout.byteAlignment())
        assertEquals(0, layout.byteOffset(MemoryLayout.PathElement.groupElement("tag")))
        assertEquals(1, layout.byteOffset(MemoryLayout.PathElement.groupElement("value")))
    }

    @Test
    public fun `does not guess unknown or bit-field layouts`() {
        val catalog = NativeApiCatalog.parse(FIXTURE)
        val types = catalog.nativeTypes()

        assertFailsWith<NativeTypeResolutionException> { types.layout("SDK_TYPE_NOT_IN_SCHEMA") }
        assertTrue("SDK_TYPE_NOT_IN_SCHEMA" in types.unresolvedTypes)

        val bitFields = NativeApiCatalog.parse(BIT_FIELD_FIXTURE).nativeTypes()
        assertEquals(4, bitFields.layout("FLAGS").byteSize())
        java.lang.foreign.Arena.ofConfined().use { arena ->
            val memory = arena.allocate(bitFields.layout("FLAGS"))
            bitFields.writeUnsignedBitField(memory, "FLAGS", "enabled", 1)
            assertEquals(1, bitFields.readUnsignedBitField(memory, "FLAGS", "enabled"))
        }
    }

    @Test
    public fun `rejects duplicate names and unsupported schema versions`() {
        assertFailsWith<NativeSchemaException> { NativeApiCatalog.parse(FIXTURE.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")) }
        assertFailsWith<NativeSchemaException> {
            NativeApiCatalog.parse(FIXTURE.replace("\"functions\":[", "\"functions\":[{\"name\":\"CreateFixture\",\"returnType\":\"HRESULT\",\"params\":[],\"type\":\"HRESULT (*)(void)\"},"))
        }
    }

    @Test
    public fun `loads the checked in DXGI schema through its relative index`() {
        val index = NativeApiResourceIndex.loadResource(DxgiApiCatalog.INDEX_RESOURCE)
        val catalog = index.load("dxgi")

        assertEquals("dxgi", catalog.api)
        assertTrue(catalog.interfaces.size >= 40)
        assertTrue(catalog.requireInterface("IDXGIFactory7").vtableSize > 3)
        assertEquals(catalog.api, DxgiApiCatalog.load().api)
    }

    private companion object {
        val FIXTURE: String = """
            {
              "schemaVersion":1,
              "api":"fixture",
              "namespace":"test",
              "target":"x86_64-pc-windows-msvc",
              "sourceSet":{"defines":["CINTERFACE=1"]},
              "reviewedExclusions":[{"pattern":"Win32*","reason":"transitive"}],
              "declarations":{
                "interfaces":[{"name":"ITest","iid":"00000000-0000-0000-c000-000000000046","parent":"IUnknown","methods":[
                  {"slot":0,"name":"QueryInterface","returnType":"HRESULT","params":[{"name":"arg0","type":"ITest *"},{"name":"arg1","type":"REFIID"},{"name":"arg2","type":"void **"}],"type":"HRESULT (*)(ITest *, REFIID, void **) __attribute__((stdcall))"},
                  {"slot":1,"name":"AddRef","returnType":"ULONG","params":[{"name":"arg0","type":"ITest *"}],"type":"ULONG (*)(ITest *) __attribute__((stdcall))"},
                  {"slot":2,"name":"Release","returnType":"ULONG","params":[{"name":"arg0","type":"ITest *"}],"type":"ULONG (*)(ITest *) __attribute__((stdcall))"}
                ]}],
                "enums":[{"name":"FIXTURE_KIND","underlyingType":"int","entries":[{"name":"FIXTURE_A","value":1}]}],
                "records":[{"name":"FIXTURE_PAIR","kind":"struct","size":8,"align":4,"fields":[{"name":"x","type":"LONG","offsetBits":0,"bitWidth":null},{"name":"y","type":"LONG","offsetBits":32,"bitWidth":null}]}],
                "typedefs":[{"name":"REFIID","type":"const GUID *","canonicalType":"const GUID *"}],
                "functions":[{"name":"CreateFixture","returnType":"HRESULT","params":[{"name":"out","type":"ITest **"}],"type":"HRESULT (*)(ITest **)","dll":"fixture.dll"}],
                "constants":[{"name":"FIXTURE_VALUE","type":"macro","value":2,"valueText":"2"}]
              },
              "statistics":{"interfaces":1}
            }
        """.trimIndent()

        val PACKED_FIXTURE: String = minimalRecord(
            "{\"name\":\"PACKED\",\"kind\":\"struct\",\"size\":5,\"align\":1,\"fields\":[{\"name\":\"tag\",\"type\":\"BYTE\",\"offsetBits\":0,\"bitWidth\":null},{\"name\":\"value\",\"type\":\"UINT\",\"offsetBits\":8,\"bitWidth\":null}]}",
        )

        val BIT_FIELD_FIXTURE: String = minimalRecord(
            "{\"name\":\"FLAGS\",\"kind\":\"struct\",\"size\":4,\"align\":4,\"fields\":[{\"name\":\"enabled\",\"type\":\"UINT\",\"offsetBits\":0,\"bitWidth\":1}]}",
        )

        fun minimalRecord(record: String): String =
            """{"schemaVersion":1,"api":"fixture","declarations":{"interfaces":[],"enums":[],"records":[$record],"typedefs":[],"functions":[],"constants":[]}}"""
    }
}
