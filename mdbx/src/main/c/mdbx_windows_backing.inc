    IO_STATUS_BLOCK status = {0};
    status.Status = STATUS_PENDING;
    HANDLE event = nullptr;
    if (asynchronous) {
      /* Lock operations on the same file may still be completing after cancellation. */
      event = CreateEventW(nullptr, TRUE, FALSE, nullptr);
      if (unlikely(!event))
        return (int)GetLastError();
    }

    rc = imports.NtFsControlFile(handle, event, nullptr, nullptr, &status, FSCTL_GET_EXTERNAL_BACKING, nullptr, 0,
                                 &GetExternalBacking_OutputBuffer, sizeof(GetExternalBacking_OutputBuffer));
    if (rc == STATUS_PENDING) {
      /* Completion owns the status and output buffers until the wait succeeds. */
      if (unlikely(!event))
        panic("Synchronous FSCTL_GET_EXTERNAL_BACKING returned pending");
      const DWORD wait = WaitForSingleObject(event, INFINITE);
      if (unlikely(wait != WAIT_OBJECT_0))
        panic_fmt(nullptr, "FSCTL_GET_EXTERNAL_BACKING wait failed: %d", osal_waitstatus2errcode(wait));
      rc = status.Status;
      if (unlikely(rc == STATUS_PENDING))
        panic("FSCTL_GET_EXTERNAL_BACKING signaled without completing");
    }
    const int close_error = event && !CloseHandle(event) ? (int)GetLastError() : 0;
    if (unlikely(close_error))
      ERROR("FSCTL_GET_EXTERNAL_BACKING event close failed (%d), query status 0x%08lX", close_error, (unsigned long)rc);

    if (NT_SUCCESS(rc) && (status.Information < sizeof(WOF_EXTERNAL_INFO) ||
                           status.Information > sizeof(GetExternalBacking_OutputBuffer)))
      return ERROR_INVALID_DATA;
