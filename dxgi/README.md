# caelum-dxgi ABI notes

This module uses the JDK 24 Foreign Function and Memory API and targets the
64-bit Windows COM ABI. The DXGI interface inventory is pinned to the public
interfaces in Windows SDK 10.0.22621.0 (`dxgi.h` through `dxgi1_6.h` and
`dxgidebug.h`). The vtable sizes are the field counts of the SDK's complete C
`*Vtbl` declarations; those declarations are flattened and include inherited
slots.

The following rules are part of the implementation contract and must remain
true when this module is changed:

- A native `GUID` is not a raw copy of its textual/UUID bytes. `Data1`,
  `Data2`, and `Data3` are little-endian integer fields; `Data4` remains in
  textual byte order.
- Every COM vtable begins with `QueryInterface`, `AddRef`, and `Release` in
  slots 0, 1, and 2. `ComInterface.vtableSize` is the complete flattened slot
  count, including inherited methods.
- `ComPtr.adopt` is only used for an already-owned successful out-parameter.
  It never calls `AddRef`. `copy` calls `AddRef` exactly once, and `close`
  calls `Release` at most once.
- A failed HRESULT never transfers ownership. Out-pointer cells are explicitly
  zeroed before native calls and are adopted only after success and a non-null
  result.
- `ComPtr.close`, `copy`, `queryInterface`, and `withAddress` share one
  lifecycle lock. Code that permits concurrent close must perform the complete
  native operation inside `withAddress`; an escaped raw `segment` cannot keep
  COM ownership alive.
- `WindowsLibrary.openOrNull` treats only a missing/unloadable library as
  optional. Invalid blank names, FFM wrong-thread violations, and native-access
  configuration errors are programming errors and are not swallowed.
- Structs passed by value must retain their exact `GroupLayout`; replacing one
  with an address or scalar carrier changes the ABI.

These notes are intentionally tracked with the module so native crash fixes do
not depend on local debug logs or memory.
