package net.echonolix.caelum.dxgi.com

/** Phantom root type for COM interface pointers. */
public interface IUnknown

/** Metadata needed to safely interpret a COM interface pointer. */
public class ComInterface<T : IUnknown>(
    public val name: String,
    public val iid: Guid,
    public val vtableSize: Int,
    public val methods: List<ComMethod> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "COM interface name must not be blank" }
        require(vtableSize >= IUNKNOWN_VTABLE_SIZE) {
            "$name vtable must include the $IUNKNOWN_VTABLE_SIZE IUnknown slots"
        }
        require(methods.all { it.slot in 0 until vtableSize }) {
            "$name contains a method outside its $vtableSize-slot vtable"
        }
        require(methods.map(ComMethod::slot).distinct().size == methods.size) {
            "$name contains duplicate method slots"
        }
    }

    public fun method(slot: Int): ComMethod? = methods.firstOrNull { it.slot == slot }

    override fun toString(): String = "$name($iid, $vtableSize slots)"

    public companion object {
        public const val IUNKNOWN_VTABLE_SIZE: Int = 3
    }
}

/** Optional extension point for generated, fully typed method catalogs. */
public class ComMethod(
    public val name: String,
    public val slot: Int,
)

public object ComInterfaces {
    public val IUNKNOWN: ComInterface<IUnknown> = ComInterface(
        name = "IUnknown",
        iid = Guid.parse("00000000-0000-0000-c000-000000000046"),
        vtableSize = ComInterface.IUNKNOWN_VTABLE_SIZE,
        methods = listOf(
            ComMethod("QueryInterface", 0),
            ComMethod("AddRef", 1),
            ComMethod("Release", 2),
        ),
    )
}
