package net.echonolix.caelum.sdl3.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SDLTypeLayoutsTest {
    @Test
    fun `Windows x64 ABI snapshot covers every concrete public SDL record`() {
        val records = SDLWindowsX64Layouts.records

        assertEquals(122, records.size)
        assertEquals(records.size, records.map(SDLRecordLayout::name).distinct().size)
        assertEquals(records.sortedBy(SDLRecordLayout::name), records)
        assertEquals(
            setOf("SDL_Event", "SDL_HapticEffect"),
            records.filter { it.kind == SDLRecordKind.UNION }.mapTo(sortedSetOf(), SDLRecordLayout::name),
        )
        records.forEach { record ->
            assertTrue(record.size > 0, "${record.name} must not have a zero-size ABI")
            assertTrue(record.alignment in setOf(1L, 2L, 4L, 8L), "unexpected ${record.name} alignment")
            assertEquals(0L, record.size % record.alignment, "${record.name} size must include tail padding")
            record.fields.forEach { field ->
                assertTrue(field.offset >= 0, "negative ${record.name}.${field.name} offset")
                assertTrue(field.size > 0, "empty ${record.name}.${field.name}")
                assertTrue(
                    field.offset + field.size <= record.size,
                    "${record.name}.${field.name} exceeds its record",
                )
                assertFalse(
                    Regex("[A-Za-z]:[\\\\/]").containsMatchIn(field.nativeType) ||
                        Regex("[A-Za-z]:[\\\\/]").containsMatchIn(field.unsupportedReason.orEmpty()),
                    "${record.name}.${field.name} must not capture an absolute host path",
                )
            }
        }
    }

    @Test
    fun `all records expose direct fields and unsupported storage is explicit`() {
        val records = SDLWindowsX64Layouts.records
        val fields = records.flatMap { record -> record.fields.map { record.name to it } }
        val storage = fields.filter { (_, field) -> field.carrier == SDLFieldCarrier.STORAGE }
        val partial = records.filter { record -> record.fields.any { it.carrier == SDLFieldCarrier.STORAGE } }

        assertEquals(839, fields.size)
        assertTrue(records.none { it.fields.isEmpty() }, "concrete records must not silently become storage-only")
        assertEquals(listOf("SDL_GamepadBinding"), partial.map(SDLRecordLayout::name))
        assertEquals(listOf("input", "output"), storage.map { it.second.name })
        assertTrue(storage.all { (_, field) -> !field.unsupportedReason.isNullOrBlank() })
        assertEquals(
            27,
            fields.count { (_, field) -> field.unsupportedReason != null },
            "two anonymous unions and 25 untyped function-pointer fields are inventoried",
        )
    }

    @Test
    fun `core allocation and inspection layouts match Clang`() {
        assertRecord("SDL_Rect", 16, 4, "x" to 0, "y" to 4, "w" to 8, "h" to 12)
        assertRecord("SDL_Point", 8, 4, "x" to 0, "y" to 4)
        assertRecord("SDL_FPoint", 8, 4, "x" to 0, "y" to 4)
        assertRecord("SDL_FRect", 16, 4, "x" to 0, "y" to 4, "w" to 8, "h" to 12)
        assertRecord("SDL_Color", 4, 1, "r" to 0, "g" to 1, "b" to 2, "a" to 3)
        assertRecord("SDL_FColor", 16, 4, "r" to 0, "g" to 4, "b" to 8, "a" to 12)
        assertRecord("SDL_GUID", 16, 1, "data" to 0)
        assertRecord("SDL_AudioSpec", 12, 4, "format" to 0, "channels" to 4, "freq" to 8)
        assertRecord("SDL_DialogFileFilter", 16, 8, "name" to 0, "pattern" to 8)
        assertRecord("SDL_DisplayMode", 40, 8, "displayID" to 0, "internal" to 32)
        assertRecord("SDL_Surface", 48, 8, "flags" to 0, "pixels" to 24, "reserved" to 40)
        assertRecord("SDL_Texture", 16, 4, "format" to 0, "refcount" to 12)
        assertRecord("SDL_IOStreamInterface", 56, 8, "version" to 0, "size" to 8, "close" to 48)
        assertRecord("SDL_StorageInterface", 96, 8, "version" to 0, "enumerate" to 24, "space_remaining" to 88)
        assertRecord("SDL_VirtualJoystickDesc", 136, 8, "version" to 0, "name" to 40, "Cleanup" to 128)
        assertRecord("SDL_HapticEffect", 72, 8, "type" to 0, "condition" to 0, "custom" to 0)
    }

    private fun assertRecord(
        name: String,
        size: Long,
        alignment: Long,
        vararg offsets: Pair<String, Int>,
    ) {
        val record = SDLWindowsX64Layouts.byName.getValue(name)
        assertEquals(size, record.size, "$name size")
        assertEquals(alignment, record.alignment, "$name alignment")
        val actualOffsets = record.fields.associate { it.name to it.offset }
        offsets.forEach { (field, offset) -> assertEquals(offset.toLong(), actualOffsets[field], "$name.$field") }
    }
}
