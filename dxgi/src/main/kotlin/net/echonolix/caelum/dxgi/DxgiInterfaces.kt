package net.echonolix.caelum.dxgi

import net.echonolix.caelum.dxgi.com.ComInterface
import net.echonolix.caelum.dxgi.com.Guid
import net.echonolix.caelum.dxgi.com.IUnknown

public interface IDXGIObject : IUnknown
public interface IDXGIDeviceSubObject : IDXGIObject
public interface IDXGIResource : IDXGIDeviceSubObject
public interface IDXGIKeyedMutex : IDXGIDeviceSubObject
public interface IDXGISurface : IDXGIDeviceSubObject
public interface IDXGISurface1 : IDXGISurface
public interface IDXGIAdapter : IDXGIObject
public interface IDXGIOutput : IDXGIObject
public interface IDXGISwapChain : IDXGIDeviceSubObject
public interface IDXGIFactory : IDXGIObject
public interface IDXGIDevice : IDXGIObject
public interface IDXGIFactory1 : IDXGIFactory
public interface IDXGIAdapter1 : IDXGIAdapter
public interface IDXGIDevice1 : IDXGIDevice
public interface IDXGIDisplayControl : IUnknown
public interface IDXGIOutputDuplication : IDXGIObject
public interface IDXGISurface2 : IDXGISurface1
public interface IDXGIResource1 : IDXGIResource
public interface IDXGIDevice2 : IDXGIDevice1
public interface IDXGISwapChain1 : IDXGISwapChain
public interface IDXGIFactory2 : IDXGIFactory1
public interface IDXGIAdapter2 : IDXGIAdapter1
public interface IDXGIOutput1 : IDXGIOutput
public interface IDXGIDevice3 : IDXGIDevice2
public interface IDXGISwapChain2 : IDXGISwapChain1
public interface IDXGIOutput2 : IDXGIOutput1
public interface IDXGIFactory3 : IDXGIFactory2
public interface IDXGIDecodeSwapChain : IUnknown
public interface IDXGIFactoryMedia : IUnknown
public interface IDXGISwapChainMedia : IUnknown
public interface IDXGIOutput3 : IDXGIOutput2
public interface IDXGISwapChain3 : IDXGISwapChain2
public interface IDXGIOutput4 : IDXGIOutput3
public interface IDXGIFactory4 : IDXGIFactory3
public interface IDXGIAdapter3 : IDXGIAdapter2
public interface IDXGIOutput5 : IDXGIOutput4
public interface IDXGISwapChain4 : IDXGISwapChain3
public interface IDXGIDevice4 : IDXGIDevice3
public interface IDXGIFactory5 : IDXGIFactory4
public interface IDXGIAdapter4 : IDXGIAdapter3
public interface IDXGIOutput6 : IDXGIOutput5
public interface IDXGIFactory6 : IDXGIFactory5
public interface IDXGIFactory7 : IDXGIFactory6
public interface IDXGIInfoQueue : IUnknown
public interface IDXGIDebug : IUnknown
public interface IDXGIDebug1 : IDXGIDebug

/**
 * DXGI 1.0 through 1.6 interface metadata from Windows SDK 10.0.22621.0.
 * Vtable sizes include inherited methods and the three IUnknown slots.
 */
public object DxgiInterfaces {
    public val IDXGIObject: ComInterface<net.echonolix.caelum.dxgi.IDXGIObject> =
        type("IDXGIObject", "aec22fb8-76f3-4639-9be0-28eb43a67a2e", 7)
    public val IDXGIDeviceSubObject: ComInterface<net.echonolix.caelum.dxgi.IDXGIDeviceSubObject> =
        type("IDXGIDeviceSubObject", "3d3e0379-f9de-4d58-bb6c-18d62992f1a6", 8)
    public val IDXGIResource: ComInterface<net.echonolix.caelum.dxgi.IDXGIResource> =
        type("IDXGIResource", "035f3ab4-482e-4e50-b41f-8a7f8bd8960b", 12)
    public val IDXGIKeyedMutex: ComInterface<net.echonolix.caelum.dxgi.IDXGIKeyedMutex> =
        type("IDXGIKeyedMutex", "9d8e1289-d7b3-465f-8126-250e349af85d", 10)
    public val IDXGISurface: ComInterface<net.echonolix.caelum.dxgi.IDXGISurface> =
        type("IDXGISurface", "cafcb56c-6ac3-4889-bf47-9e23bbd260ec", 11)
    public val IDXGISurface1: ComInterface<net.echonolix.caelum.dxgi.IDXGISurface1> =
        type("IDXGISurface1", "4ae63092-6327-4c1b-80ae-bfe12ea32b86", 13)
    public val IDXGIAdapter: ComInterface<net.echonolix.caelum.dxgi.IDXGIAdapter> =
        type("IDXGIAdapter", "2411e7e1-12ac-4ccf-bd14-9798e8534dc0", 10)
    public val IDXGIOutput: ComInterface<net.echonolix.caelum.dxgi.IDXGIOutput> =
        type("IDXGIOutput", "ae02eedb-c735-4690-8d52-5a8dc20213aa", 19)
    public val IDXGISwapChain: ComInterface<net.echonolix.caelum.dxgi.IDXGISwapChain> =
        type("IDXGISwapChain", "310d36a0-d2e7-4c0a-aa04-6a9d23b8886a", 18)
    public val IDXGIFactory: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactory> =
        type("IDXGIFactory", "7b7166ec-21c7-44ae-b21a-c9ae321ae369", 12)
    public val IDXGIDevice: ComInterface<net.echonolix.caelum.dxgi.IDXGIDevice> =
        type("IDXGIDevice", "54ec77fa-1377-44e6-8c32-88fd5f44c84c", 12)
    public val IDXGIFactory1: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactory1> =
        type("IDXGIFactory1", "770aae78-f26f-4dba-a829-253c83d1b387", 14)
    public val IDXGIAdapter1: ComInterface<net.echonolix.caelum.dxgi.IDXGIAdapter1> =
        type("IDXGIAdapter1", "29038f61-3839-4626-91fd-086879011a05", 11)
    public val IDXGIDevice1: ComInterface<net.echonolix.caelum.dxgi.IDXGIDevice1> =
        type("IDXGIDevice1", "77db970f-6276-48ba-ba28-070143b4392c", 14)
    public val IDXGIDisplayControl: ComInterface<net.echonolix.caelum.dxgi.IDXGIDisplayControl> =
        type("IDXGIDisplayControl", "ea9dbf1a-c88e-4486-854a-98aa0138f30c", 5)
    public val IDXGIOutputDuplication: ComInterface<net.echonolix.caelum.dxgi.IDXGIOutputDuplication> =
        type("IDXGIOutputDuplication", "191cfac3-a341-470d-b26e-a864f428319c", 15)
    public val IDXGISurface2: ComInterface<net.echonolix.caelum.dxgi.IDXGISurface2> =
        type("IDXGISurface2", "aba496dd-b617-4cb8-a866-bc44d7eb1fa2", 14)
    public val IDXGIResource1: ComInterface<net.echonolix.caelum.dxgi.IDXGIResource1> =
        type("IDXGIResource1", "30961379-4609-4a41-998e-54fe567ee0c1", 14)
    public val IDXGIDevice2: ComInterface<net.echonolix.caelum.dxgi.IDXGIDevice2> =
        type("IDXGIDevice2", "05008617-fbfd-4051-a790-144884b4f6a9", 17)
    public val IDXGISwapChain1: ComInterface<net.echonolix.caelum.dxgi.IDXGISwapChain1> =
        type("IDXGISwapChain1", "790a45f7-0d42-4876-983a-0a55cfe6f4aa", 29)
    public val IDXGIFactory2: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactory2> =
        type("IDXGIFactory2", "50c83a1c-e072-4c48-87b0-3630fa36a6d0", 25)
    public val IDXGIAdapter2: ComInterface<net.echonolix.caelum.dxgi.IDXGIAdapter2> =
        type("IDXGIAdapter2", "0aa1ae0a-fa0e-4b84-8644-e05ff8e5acb5", 12)
    public val IDXGIOutput1: ComInterface<net.echonolix.caelum.dxgi.IDXGIOutput1> =
        type("IDXGIOutput1", "00cddea8-939b-4b83-a340-a685226666cc", 23)
    public val IDXGIDevice3: ComInterface<net.echonolix.caelum.dxgi.IDXGIDevice3> =
        type("IDXGIDevice3", "6007896c-3244-4afd-bf18-a6d3beda5023", 18)
    public val IDXGISwapChain2: ComInterface<net.echonolix.caelum.dxgi.IDXGISwapChain2> =
        type("IDXGISwapChain2", "a8be2ac4-199f-4946-b331-79599fb98de7", 36)
    public val IDXGIOutput2: ComInterface<net.echonolix.caelum.dxgi.IDXGIOutput2> =
        type("IDXGIOutput2", "595e39d1-2724-4663-99b1-da969de28364", 24)
    public val IDXGIFactory3: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactory3> =
        type("IDXGIFactory3", "25483823-cd46-4c7d-86ca-47aa95b837bd", 26)
    public val IDXGIDecodeSwapChain: ComInterface<net.echonolix.caelum.dxgi.IDXGIDecodeSwapChain> =
        type("IDXGIDecodeSwapChain", "2633066b-4514-4c7a-8fd8-12ea98059d18", 12)
    public val IDXGIFactoryMedia: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactoryMedia> =
        type("IDXGIFactoryMedia", "41e7d1f2-a591-4f7b-a2e5-fa9c843e1c12", 5)
    public val IDXGISwapChainMedia: ComInterface<net.echonolix.caelum.dxgi.IDXGISwapChainMedia> =
        type("IDXGISwapChainMedia", "dd95b90b-f05f-4f6a-bd65-25bfb264bd84", 6)
    public val IDXGIOutput3: ComInterface<net.echonolix.caelum.dxgi.IDXGIOutput3> =
        type("IDXGIOutput3", "8a6bb301-7e7e-41f4-a8e0-5b32f7f99b18", 25)
    public val IDXGISwapChain3: ComInterface<net.echonolix.caelum.dxgi.IDXGISwapChain3> =
        type("IDXGISwapChain3", "94d99bdb-f1f8-4ab0-b236-7da0170edab1", 40)
    public val IDXGIOutput4: ComInterface<net.echonolix.caelum.dxgi.IDXGIOutput4> =
        type("IDXGIOutput4", "dc7dca35-2196-414d-9f53-617884032a60", 26)
    public val IDXGIFactory4: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactory4> =
        type("IDXGIFactory4", "1bc6ea02-ef36-464f-bf0c-21ca39e5168a", 28)
    public val IDXGIAdapter3: ComInterface<net.echonolix.caelum.dxgi.IDXGIAdapter3> =
        type("IDXGIAdapter3", "645967a4-1392-4310-a798-8053ce3e93fd", 18)
    public val IDXGIOutput5: ComInterface<net.echonolix.caelum.dxgi.IDXGIOutput5> =
        type("IDXGIOutput5", "80a07424-ab52-42eb-833c-0c42fd282d98", 27)
    public val IDXGISwapChain4: ComInterface<net.echonolix.caelum.dxgi.IDXGISwapChain4> =
        type("IDXGISwapChain4", "3d585d5a-bd4a-489e-b1f4-3dbcb6452ffb", 41)
    public val IDXGIDevice4: ComInterface<net.echonolix.caelum.dxgi.IDXGIDevice4> =
        type("IDXGIDevice4", "95b4f95f-d8da-4ca4-9ee6-3b76d5968a10", 20)
    public val IDXGIFactory5: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactory5> =
        type("IDXGIFactory5", "7632e1f5-ee65-4dca-87fd-84cd75f8838d", 29)
    public val IDXGIAdapter4: ComInterface<net.echonolix.caelum.dxgi.IDXGIAdapter4> =
        type("IDXGIAdapter4", "3c8d99d1-4fbf-4181-a82c-af66bf7bd24e", 19)
    public val IDXGIOutput6: ComInterface<net.echonolix.caelum.dxgi.IDXGIOutput6> =
        type("IDXGIOutput6", "068346e8-aaec-4b84-add7-137f513f77a1", 29)
    public val IDXGIFactory6: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactory6> =
        type("IDXGIFactory6", "c1b6694f-ff09-44a9-b03c-77900a0a1d17", 30)
    public val IDXGIFactory7: ComInterface<net.echonolix.caelum.dxgi.IDXGIFactory7> =
        type("IDXGIFactory7", "a4966eed-76db-44da-84c1-ee9a7afb20a8", 32)
    public val IDXGIInfoQueue: ComInterface<net.echonolix.caelum.dxgi.IDXGIInfoQueue> =
        type("IDXGIInfoQueue", "d67441c7-672a-476f-9e82-cd55b44949ce", 40)
    public val IDXGIDebug: ComInterface<net.echonolix.caelum.dxgi.IDXGIDebug> =
        type("IDXGIDebug", "119e7452-de9e-40fe-8806-88f90c12b441", 4)
    public val IDXGIDebug1: ComInterface<net.echonolix.caelum.dxgi.IDXGIDebug1> =
        type("IDXGIDebug1", "c5a05f0c-16f2-4adf-9f4d-a8c4d58ac550", 7)

    public val ALL: List<ComInterface<*>> = listOf(
        IDXGIObject, IDXGIDeviceSubObject, IDXGIResource, IDXGIKeyedMutex, IDXGISurface, IDXGISurface1,
        IDXGIAdapter, IDXGIOutput, IDXGISwapChain, IDXGIFactory, IDXGIDevice, IDXGIFactory1, IDXGIAdapter1,
        IDXGIDevice1, IDXGIDisplayControl, IDXGIOutputDuplication, IDXGISurface2, IDXGIResource1, IDXGIDevice2,
        IDXGISwapChain1, IDXGIFactory2, IDXGIAdapter2, IDXGIOutput1, IDXGIDevice3, IDXGISwapChain2,
        IDXGIOutput2, IDXGIFactory3, IDXGIDecodeSwapChain, IDXGIFactoryMedia, IDXGISwapChainMedia,
        IDXGIOutput3, IDXGISwapChain3, IDXGIOutput4, IDXGIFactory4, IDXGIAdapter3, IDXGIOutput5,
        IDXGISwapChain4, IDXGIDevice4, IDXGIFactory5, IDXGIAdapter4, IDXGIOutput6, IDXGIFactory6, IDXGIFactory7,
        IDXGIInfoQueue, IDXGIDebug, IDXGIDebug1,
    )

    public fun named(name: String): ComInterface<*>? = ALL.firstOrNull { it.name == name }

    private fun <T : IUnknown> type(name: String, iid: String, vtableSize: Int): ComInterface<T> =
        ComInterface(name, Guid.parse(iid), vtableSize)
}
