import net.echonolix.caelum.NComposite
import net.echonolix.caelum.NFloat
import net.echonolix.caelum.NInt
import net.echonolix.caelum.NInt32
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.NStruct
import net.echonolix.caelum.NUInt16
import net.echonolix.caelum.NUInt32
import net.echonolix.caelum.NUnion
import net.echonolix.caelum.struct.StructCodegenProcessor
import kotlin.io.path.Path
import kotlin.io.path.toPath
import kotlin.reflect.KClass

interface TestUnion : NUnion {
    val float32: NFloat
    val int32: NInt32
    val uint32: NUInt32
}

interface A : NStruct {
    val a: NInt
    val b: NFloat
    val c: NUInt16
}

interface B : NStruct {
    val a: NPointer<NInt>
    val b: NPointer<B>
}

interface C : NStruct {
    val a: NPointer<B>
    val b: NPointer<TestUnion>
    val c: A
    val d: TestUnion
}

fun main() {
    fun KClass<*>.path() =
        this.java.getResource(this.qualifiedName!!.replace(".", "/") + ".class")!!
        .toURI()
        .toPath()

    val inputs = listOf(
        A::class,
        B::class,
        C::class,
        TestUnion::class,
    ).mapTo(mutableSetOf(), KClass<*>::path)
    val outputDir = Path("struct-codegen/build/generated/ktgen")
    StructCodegenProcessor().process(inputs, outputDir)
}