#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#define MDBX_BUILD_TIMESTAMP "2026-10-12T00:00:00Z"

static DWORD WINAPI controlled_wait(HANDLE event, DWORD milliseconds);
static HANDLE WINAPI controlled_event(LPSECURITY_ATTRIBUTES attributes, BOOL manual_reset, BOOL initial_state,
                                      LPCWSTR name);
static BOOL WINAPI controlled_close(HANDLE handle);

#define WaitForSingleObject controlled_wait
#define CreateEventW controlled_event
#define CloseHandle controlled_close
#include "mdbx.c"
#undef CloseHandle
#undef CreateEventW
#undef WaitForSingleObject

enum test_case {
  pending_unsupported,
  pending_backed,
  pending_short,
  immediate_failure,
  immediate_not_backed,
  immediate_short,
  immediate_oversize,
  event_failure,
  close_failure_after_allow,
  close_failure_after_query_failure,
  wait_failure,
  incomplete_signal,
  synchronous_pending
};

static enum test_case scenario;
static IO_STATUS_BLOCK *pending_status;
static WOF_EXTERNAL_INFO *pending_output;
static HANDLE query_event;
static unsigned query_count, wait_count;

static void expect(bool condition, const char *message) {
  if (!condition) {
    fprintf(stderr, "%s\n", message);
    ExitProcess(1);
  }
}

static HANDLE WINAPI controlled_event(LPSECURITY_ATTRIBUTES attributes, BOOL manual_reset, BOOL initial_state,
                                      LPCWSTR name) {
  expect(manual_reset && !initial_state, "the query event must start nonsignaled");
  if (scenario == event_failure) {
    SetLastError(ERROR_NOT_ENOUGH_MEMORY);
    return nullptr;
  }
  return CreateEventW(attributes, manual_reset, initial_state, name);
}

static BOOL WINAPI controlled_close(HANDLE handle) {
  const BOOL closed = CloseHandle(handle);
  if (handle == query_event && (scenario == close_failure_after_allow || scenario == close_failure_after_query_failure)) {
    expect(closed, "failed to dispose test event before simulating close failure");
    SetLastError(ERROR_INVALID_HANDLE);
    return FALSE;
  }
  return closed;
}

static DWORD WINAPI controlled_wait(HANDLE event, DWORD milliseconds) {
  expect(event && event == query_event && milliseconds == INFINITE, "wait must use the private query event");
  expect(++wait_count == 1 && pending_status, "only the pending query should be waited on");
  if (scenario == wait_failure) {
    SetLastError(ERROR_INVALID_HANDLE);
    return WAIT_FAILED;
  }
  if (scenario == incomplete_signal)
    return WAIT_OBJECT_0;
  if (scenario == pending_unsupported) {
    pending_status->Status = STATUS_INVALID_DEVICE_REQUEST;
  } else {
    pending_output->Version = WOF_CURRENT_VERSION;
    pending_output->Provider = 2; /* WOF_PROVIDER_FILE */
    pending_status->Information = scenario == pending_short ? sizeof(*pending_output) - 1 : sizeof(*pending_output);
    pending_status->Status = 0;
  }
  return WAIT_OBJECT_0;
}

static NTSTATUS NTAPI controlled_query(HANDLE file, HANDLE event, PVOID apc, PVOID context,
                                       PIO_STATUS_BLOCK status, ULONG code, PVOID input, ULONG input_size,
                                       PVOID output, ULONG output_size) {
  expect(++query_count == 1 && file && !apc && !context, "wrong FSCTL call shape");
  expect(code == FSCTL_GET_EXTERNAL_BACKING && !input && input_size == 0 && output_size >= sizeof(WOF_EXTERNAL_INFO),
         "wrong FSCTL or buffers");
  query_event = event;
  if (scenario <= pending_short || scenario == wait_failure || scenario == incomplete_signal) {
    expect(event != nullptr, "overlapped query lacks a private completion event");
    pending_status = status;
    pending_output = output;
    return STATUS_PENDING;
  }
  if (scenario == close_failure_after_allow || scenario == close_failure_after_query_failure) {
    expect(event != nullptr, "overlapped query lacks a private completion event");
    return scenario == close_failure_after_allow ? STATUS_INVALID_DEVICE_REQUEST : (NTSTATUS)0xC0000022L;
  }
  expect(event == nullptr, "synchronous query should use its direct result");
  switch (scenario) {
  case immediate_failure:
    status->Status = STATUS_INVALID_DEVICE_REQUEST;
    return (NTSTATUS)0xC0000022L; /* STATUS_ACCESS_DENIED */
  case immediate_not_backed:
    return STATUS_OBJECT_NOT_EXTERNALLY_BACKED;
  case immediate_short:
    status->Information = sizeof(WOF_EXTERNAL_INFO) - 1;
    return 0;
  case immediate_oversize:
    status->Information = output_size + 1;
    return 0;
  case synchronous_pending:
    return STATUS_PENDING;
  default:
    expect(false, "unexpected query");
    return STATUS_INVALID_DEVICE_REQUEST;
  }
}

static void run_case(HANDLE file, enum test_case test, int flags, int expected) {
  scenario = test;
  query_count = wait_count = 0;
  pending_status = nullptr;
  const bool asynchronous = test <= pending_short || test >= event_failure;
  const int actual = osal_check_fs_local_windows(file, flags, asynchronous);
  expect(actual == expected, "unexpected filesystem classification");
  expect(query_count == (test != event_failure) && wait_count == (test <= pending_short), "unexpected OS calls");
}

int main(int argc, char **argv) {
  HANDLE file = CreateFileW(L"lock-probe.lck", GENERIC_READ | GENERIC_WRITE | DELETE, 0, nullptr, CREATE_NEW,
                           FILE_ATTRIBUTE_TEMPORARY | FILE_FLAG_DELETE_ON_CLOSE, nullptr);
  expect(file != INVALID_HANDLE_VALUE, "failed to create private probe file");
  imports.GetFileInformationByHandleEx = nullptr;
  imports.GetVolumeInformationByHandleW = nullptr;
  imports.NtFsControlFile = controlled_query;

  if (argc == 2) {
    if (strcmp(argv[1], "wait-failure") == 0)
      scenario = wait_failure;
    else if (strcmp(argv[1], "incomplete-signal") == 0)
      scenario = incomplete_signal;
    else if (strcmp(argv[1], "synchronous-pending") == 0)
      scenario = synchronous_pending;
    else
      expect(false, "unrecognized fail-stop scenario");
    fprintf(stderr, "EXPECT_FAIL_STOP\n");
    fflush(stderr);
    (void)osal_check_fs_local_windows(file, 0, scenario != synchronous_pending);
    expect(false, "indeterminate completion returned to caller");
  }

  run_case(file, pending_unsupported, 0, MDBX_SUCCESS);
  run_case(file, pending_backed, 0, MDBX_EREMOTE);
  run_case(file, pending_backed, MDBX_EXCLUSIVE | MDBX_RDONLY, MDBX_SUCCESS);
  run_case(file, pending_short, 0, ERROR_INVALID_DATA);
  run_case(file, immediate_failure, 0, ERROR_ACCESS_DENIED);
  run_case(file, immediate_not_backed, 0, MDBX_SUCCESS);
  run_case(file, immediate_short, 0, ERROR_INVALID_DATA);
  run_case(file, immediate_oversize, 0, ERROR_INVALID_DATA);
  run_case(file, event_failure, 0, ERROR_NOT_ENOUGH_MEMORY);
  run_case(file, close_failure_after_allow, 0, ERROR_INVALID_HANDLE);
  run_case(file, close_failure_after_query_failure, 0, ERROR_ACCESS_DENIED);
  expect(CloseHandle(file), "failed to close private probe file");
  puts("MDBX Windows backing completion: 11 cases passed");
  return 0;
}
