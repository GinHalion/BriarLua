
#include <assert.h>
#include <direct.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <wchar.h>

#include "uv.h"
#include "internal.h"

#include <winsock2.h>
#include <winperf.h>
#include <iphlpapi.h>
#include <psapi.h>
#include <tlhelp32.h>
#include <windows.h>
#include <userenv.h>
#include <math.h>

#define MAX_TITLE_LENGTH 8192

#define UV__NANOSEC 1000000000

#ifndef UNLEN
# define UNLEN 256
#endif

#ifndef MAXHOSTNAMELEN
# define MAXHOSTNAMELEN 256
#endif

#define MAX_ENV_VAR_LENGTH 32767

static char *process_title;
static CRITICAL_SECTION process_title_lock;

static double hrtime_interval_ = 0;


void uv__util_init(void) {
  LARGE_INTEGER perf_frequency;

  InitializeCriticalSection(&process_title_lock);

  if (QueryPerformanceFrequency(&perf_frequency)) {
    hrtime_interval_ = 1.0 / perf_frequency.QuadPart;
  } else {
    hrtime_interval_= 0;
  }
}


int uv_exepath(char* buffer, size_t* size_ptr) {
  int utf8_len, utf16_buffer_len, utf16_len;
  WCHAR* utf16_buffer;
  int err;

  if (buffer == NULL || size_ptr == NULL || *size_ptr == 0) {
    return UV_EINVAL;
  }

  if (*size_ptr > 32768) {
    utf16_buffer_len = 32768;
  } else {
    utf16_buffer_len = (int) *size_ptr;
  }

  utf16_buffer = (WCHAR*) uv__malloc(sizeof(WCHAR) * utf16_buffer_len);
  if (!utf16_buffer) {
    return UV_ENOMEM;
  }

  utf16_len = GetModuleFileNameW(NULL, utf16_buffer, utf16_buffer_len);
  if (utf16_len <= 0) {
    err = GetLastError();
    goto error;
  }

  utf16_buffer[utf16_len] = L'\0';

  utf8_len = WideCharToMultiByte(CP_UTF8,
                                 0,
                                 utf16_buffer,
                                 -1,
                                 buffer,
                                 (int) *size_ptr,
                                 NULL,
                                 NULL);
  if (utf8_len == 0) {
    err = GetLastError();
    goto error;
  }

  uv__free(utf16_buffer);

  *size_ptr = utf8_len - 1;
  return 0;

 error:
  uv__free(utf16_buffer);
  return uv_translate_sys_error(err);
}


int uv_cwd(char* buffer, size_t* size) {
  DWORD utf16_len;
  WCHAR utf16_buffer[MAX_PATH];
  int r;

  if (buffer == NULL || size == NULL) {
    return UV_EINVAL;
  }

  utf16_len = GetCurrentDirectoryW(MAX_PATH, utf16_buffer);
  if (utf16_len == 0) {
    return uv_translate_sys_error(GetLastError());
  } else if (utf16_len > MAX_PATH) {
    return UV_EIO;
  }

  utf16_buffer[utf16_len] = L'\0';

  if (utf16_buffer[utf16_len - 1] == L'\\' &&
      !(utf16_len == 3 && utf16_buffer[1] == L':')) {
    utf16_len--;
    utf16_buffer[utf16_len] = L'\0';
  }

  r = WideCharToMultiByte(CP_UTF8,
                          0,
                          utf16_buffer,
                          -1,
                          NULL,
                          0,
                          NULL,
                          NULL);
  if (r == 0) {
    return uv_translate_sys_error(GetLastError());
  } else if (r > (int) *size) {
    *size = r;
    return UV_ENOBUFS;
  }

  r = WideCharToMultiByte(CP_UTF8,
                          0,
                          utf16_buffer,
                          -1,
                          buffer,
                          *size > INT_MAX ? INT_MAX : (int) *size,
                          NULL,
                          NULL);
  if (r == 0) {
    return uv_translate_sys_error(GetLastError());
  }

  *size = r - 1;
  return 0;
}


int uv_chdir(const char* dir) {
  WCHAR utf16_buffer[MAX_PATH];
  size_t utf16_len;
  WCHAR drive_letter, env_var[4];

  if (dir == NULL) {
    return UV_EINVAL;
  }

  if (MultiByteToWideChar(CP_UTF8,
                          0,
                          dir,
                          -1,
                          utf16_buffer,
                          MAX_PATH) == 0) {
    DWORD error = GetLastError();
    if (error == ERROR_INSUFFICIENT_BUFFER) {
      return UV_ENAMETOOLONG;
    } else {
      return uv_translate_sys_error(error);
    }
  }

  if (!SetCurrentDirectoryW(utf16_buffer)) {
    return uv_translate_sys_error(GetLastError());
  }

  utf16_len = GetCurrentDirectoryW(MAX_PATH, utf16_buffer);
  if (utf16_len == 0) {
    return uv_translate_sys_error(GetLastError());
  } else if (utf16_len > MAX_PATH) {
    return UV_EIO;
  }

  if (utf16_buffer[utf16_len - 1] == L'\\' &&
      !(utf16_len == 3 && utf16_buffer[1] == L':')) {
    utf16_len--;
    utf16_buffer[utf16_len] = L'\0';
  }

  if (utf16_len < 2 || utf16_buffer[1] != L':') {
    drive_letter = 0;
  } else if (utf16_buffer[0] >= L'A' && utf16_buffer[0] <= L'Z') {
    drive_letter = utf16_buffer[0];
  } else if (utf16_buffer[0] >= L'a' && utf16_buffer[0] <= L'z') {
    drive_letter = utf16_buffer[0] - L'a' + L'A';
  } else {
    drive_letter = 0;
  }

  if (drive_letter != 0) {
    env_var[0] = L'=';
    env_var[1] = drive_letter;
    env_var[2] = L':';
    env_var[3] = L'\0';

    if (!SetEnvironmentVariableW(env_var, utf16_buffer)) {
      return uv_translate_sys_error(GetLastError());
    }
  }

  return 0;
}


void uv_loadavg(double avg[3]) {
  avg[0] = avg[1] = avg[2] = 0;
}


uint64_t uv_get_free_memory(void) {
  MEMORYSTATUSEX memory_status;
  memory_status.dwLength = sizeof(memory_status);

  if (!GlobalMemoryStatusEx(&memory_status)) {
     return -1;
  }

  return (uint64_t)memory_status.ullAvailPhys;
}


uint64_t uv_get_total_memory(void) {
  MEMORYSTATUSEX memory_status;
  memory_status.dwLength = sizeof(memory_status);

  if (!GlobalMemoryStatusEx(&memory_status)) {
    return -1;
  }

  return (uint64_t)memory_status.ullTotalPhys;
}


uv_pid_t uv_os_getpid(void) {
  return GetCurrentProcessId();
}


uv_pid_t uv_os_getppid(void) {
  int parent_pid = -1;
  HANDLE handle;
  PROCESSENTRY32 pe;
  DWORD current_pid = GetCurrentProcessId();

  pe.dwSize = sizeof(PROCESSENTRY32);
  handle = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);

  if (Process32First(handle, &pe)) {
    do {
      if (pe.th32ProcessID == current_pid) {
        parent_pid = pe.th32ParentProcessID;
        break;
      }
    } while( Process32Next(handle, &pe));
  }

  CloseHandle(handle);
  return parent_pid;
}


char** uv_setup_args(int argc, char** argv) {
  return argv;
}


int uv_set_process_title(const char* title) {
  int err;
  int length;
  WCHAR* title_w = NULL;

  uv__once_init();

  length = MultiByteToWideChar(CP_UTF8, 0, title, -1, NULL, 0);
  if (!length) {
    err = GetLastError();
    goto done;
  }

  title_w = (WCHAR*)uv__malloc(sizeof(WCHAR) * length);
  if (!title_w) {
    uv_fatal_error(ERROR_OUTOFMEMORY, "uv__malloc");
  }

  length = MultiByteToWideChar(CP_UTF8, 0, title, -1, title_w, length);
  if (!length) {
    err = GetLastError();
    goto done;
  }

  if (length > MAX_TITLE_LENGTH) {
    title_w[MAX_TITLE_LENGTH - 1] = L'\0';
  }

  if (!SetConsoleTitleW(title_w)) {
    err = GetLastError();
    goto done;
  }

  EnterCriticalSection(&process_title_lock);
  uv__free(process_title);
  process_title = uv__strdup(title);
  LeaveCriticalSection(&process_title_lock);

  err = 0;

done:
  uv__free(title_w);
  return uv_translate_sys_error(err);
}


static int uv__get_process_title(void) {
  WCHAR title_w[MAX_TITLE_LENGTH];

  if (!GetConsoleTitleW(title_w, sizeof(title_w) / sizeof(WCHAR))) {
    return -1;
  }

  if (uv__convert_utf16_to_utf8(title_w, -1, &process_title) != 0)
    return -1;

  return 0;
}


int uv_get_process_title(char* buffer, size_t size) {
  size_t len;

  if (buffer == NULL || size == 0)
    return UV_EINVAL;

  uv__once_init();

  EnterCriticalSection(&process_title_lock);
  if (!process_title && uv__get_process_title() == -1) {
    LeaveCriticalSection(&process_title_lock);
    return uv_translate_sys_error(GetLastError());
  }

  assert(process_title);
  len = strlen(process_title) + 1;

  if (size < len) {
    LeaveCriticalSection(&process_title_lock);
    return UV_ENOBUFS;
  }

  memcpy(buffer, process_title, len);
  LeaveCriticalSection(&process_title_lock);

  return 0;
}


uint64_t uv_hrtime(void) {
  uv__once_init();
  return uv__hrtime(UV__NANOSEC);
}

uint64_t uv__hrtime(double scale) {
  LARGE_INTEGER counter;

  if (hrtime_interval_ == 0) {
    return 0;
  }

  if (!QueryPerformanceCounter(&counter)) {
    return 0;
  }

  return (uint64_t) ((double) counter.QuadPart * hrtime_interval_ * scale);
}


int uv_resident_set_memory(size_t* rss) {
  HANDLE current_process;
  PROCESS_MEMORY_COUNTERS pmc;

  current_process = GetCurrentProcess();

  if (!GetProcessMemoryInfo(current_process, &pmc, sizeof(pmc))) {
    return uv_translate_sys_error(GetLastError());
  }

  *rss = pmc.WorkingSetSize;

  return 0;
}


int uv_uptime(double* uptime) {
  BYTE stack_buffer[4096];
  BYTE* malloced_buffer = NULL;
  BYTE* buffer = (BYTE*) stack_buffer;
  size_t buffer_size = sizeof(stack_buffer);
  DWORD data_size;

  PERF_DATA_BLOCK* data_block;
  PERF_OBJECT_TYPE* object_type;
  PERF_COUNTER_DEFINITION* counter_definition;

  DWORD i;

  for (;;) {
    LONG result;

    data_size = (DWORD) buffer_size;
    result = RegQueryValueExW(HKEY_PERFORMANCE_DATA,
                              L"2",
                              NULL,
                              NULL,
                              buffer,
                              &data_size);
    if (result == ERROR_SUCCESS) {
      break;
    } else if (result != ERROR_MORE_DATA) {
      *uptime = 0;
      return uv_translate_sys_error(result);
    }

    buffer_size *= 2;
    if (buffer_size > 1 << 20) {
      goto internalError;
    }

    uv__free(malloced_buffer);

    buffer = malloced_buffer = (BYTE*) uv__malloc(buffer_size);
    if (malloced_buffer == NULL) {
      *uptime = 0;
      return UV_ENOMEM;
    }
  }

  if (data_size < sizeof(*data_block))
    goto internalError;

  data_block = (PERF_DATA_BLOCK*) buffer;

  if (wmemcmp(data_block->Signature, L"PERF", 4) != 0)
    goto internalError;

  if (data_size < data_block->HeaderLength + sizeof(*object_type))
    goto internalError;

  object_type = (PERF_OBJECT_TYPE*) (buffer + data_block->HeaderLength);

  if (object_type->NumInstances != PERF_NO_INSTANCES)
    goto internalError;

  counter_definition = (PERF_COUNTER_DEFINITION*) (buffer +
      data_block->HeaderLength + object_type->HeaderLength);
  for (i = 0; i < object_type->NumCounters; i++) {
    if ((BYTE*) counter_definition + sizeof(*counter_definition) >
        buffer + data_size) {
      break;
    }

    if (counter_definition->CounterNameTitleIndex == 674 &&
        counter_definition->CounterSize == sizeof(uint64_t)) {
      if (counter_definition->CounterOffset + sizeof(uint64_t) > data_size ||
          !(counter_definition->CounterType & PERF_OBJECT_TIMER)) {
        goto internalError;
      } else {
        BYTE* address = (BYTE*) object_type + object_type->DefinitionLength +
                        counter_definition->CounterOffset;
        uint64_t value = *((uint64_t*) address);
        *uptime = floor((double) (object_type->PerfTime.QuadPart - value) /
                        (double) object_type->PerfFreq.QuadPart);
        uv__free(malloced_buffer);
        return 0;
      }
    }

    counter_definition = (PERF_COUNTER_DEFINITION*)
        ((BYTE*) counter_definition + counter_definition->ByteLength);
  }

  uv__free(malloced_buffer);
  *uptime = 0;
  return UV_ENOSYS;

 internalError:
  uv__free(malloced_buffer);
  *uptime = 0;
  return UV_EIO;
}


int uv_cpu_info(uv_cpu_info_t** cpu_infos_ptr, int* cpu_count_ptr) {
  uv_cpu_info_t* cpu_infos;
  SYSTEM_PROCESSOR_PERFORMANCE_INFORMATION* sppi;
  DWORD sppi_size;
  SYSTEM_INFO system_info;
  DWORD cpu_count, i;
  NTSTATUS status;
  ULONG result_size;
  int err;
  uv_cpu_info_t* cpu_info;

  cpu_infos = NULL;
  cpu_count = 0;
  sppi = NULL;

  uv__once_init();

  GetSystemInfo(&system_info);
  cpu_count = system_info.dwNumberOfProcessors;

  cpu_infos = uv__calloc(cpu_count, sizeof *cpu_infos);
  if (cpu_infos == NULL) {
    err = ERROR_OUTOFMEMORY;
    goto error;
  }

  sppi_size = cpu_count * sizeof(*sppi);
  sppi = uv__malloc(sppi_size);
  if (sppi == NULL) {
    err = ERROR_OUTOFMEMORY;
    goto error;
  }

  status = pNtQuerySystemInformation(SystemProcessorPerformanceInformation,
                                     sppi,
                                     sppi_size,
                                     &result_size);
  if (!NT_SUCCESS(status)) {
    err = pRtlNtStatusToDosError(status);
    goto error;
  }

  assert(result_size == sppi_size);

  for (i = 0; i < cpu_count; i++) {
    WCHAR key_name[128];
    HKEY processor_key;
    DWORD cpu_speed;
    DWORD cpu_speed_size = sizeof(cpu_speed);
    WCHAR cpu_brand[256];
    DWORD cpu_brand_size = sizeof(cpu_brand);
    size_t len;

    len = _snwprintf(key_name,
                     ARRAY_SIZE(key_name),
                     L"HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\%d",
                     i);

    assert(len > 0 && len < ARRAY_SIZE(key_name));

    err = RegOpenKeyExW(HKEY_LOCAL_MACHINE,
                        key_name,
                        0,
                        KEY_QUERY_VALUE,
                        &processor_key);
    if (err != ERROR_SUCCESS) {
      goto error;
    }

    err = RegQueryValueExW(processor_key,
                           L"~MHz",
                           NULL,
                           NULL,
                           (BYTE*)&cpu_speed,
                           &cpu_speed_size);
    if (err != ERROR_SUCCESS) {
      RegCloseKey(processor_key);
      goto error;
    }

    err = RegQueryValueExW(processor_key,
                           L"ProcessorNameString",
                           NULL,
                           NULL,
                           (BYTE*)&cpu_brand,
                           &cpu_brand_size);
    if (err != ERROR_SUCCESS) {
      RegCloseKey(processor_key);
      goto error;
    }

    RegCloseKey(processor_key);

    cpu_info = &cpu_infos[i];
    cpu_info->speed = cpu_speed;
    cpu_info->cpu_times.user = sppi[i].UserTime.QuadPart / 10000;
    cpu_info->cpu_times.sys = (sppi[i].KernelTime.QuadPart -
        sppi[i].IdleTime.QuadPart) / 10000;
    cpu_info->cpu_times.idle = sppi[i].IdleTime.QuadPart / 10000;
    cpu_info->cpu_times.irq = sppi[i].InterruptTime.QuadPart / 10000;
    cpu_info->cpu_times.nice = 0;

    uv__convert_utf16_to_utf8(cpu_brand,
                              cpu_brand_size / sizeof(WCHAR),
                              &(cpu_info->model));
  }

  uv__free(sppi);

  *cpu_count_ptr = cpu_count;
  *cpu_infos_ptr = cpu_infos;

  return 0;

 error:
  for (i = 0; i < cpu_count; i++)
    uv__free(cpu_infos[i].model);

  uv__free(cpu_infos);
  uv__free(sppi);

  return uv_translate_sys_error(err);
}


void uv_free_cpu_info(uv_cpu_info_t* cpu_infos, int count) {
  int i;

  for (i = 0; i < count; i++) {
    uv__free(cpu_infos[i].model);
  }

  uv__free(cpu_infos);
}


static int is_windows_version_or_greater(DWORD os_major,
                                         DWORD os_minor,
                                         WORD service_pack_major,
                                         WORD service_pack_minor) {
  OSVERSIONINFOEX osvi;
  DWORDLONG condition_mask = 0;
  int op = VER_GREATER_EQUAL;

  ZeroMemory(&osvi, sizeof(OSVERSIONINFOEX));
  osvi.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);
  osvi.dwMajorVersion = os_major;
  osvi.dwMinorVersion = os_minor;
  osvi.wServicePackMajor = service_pack_major;
  osvi.wServicePackMinor = service_pack_minor;

  VER_SET_CONDITION(condition_mask, VER_MAJORVERSION, op);
  VER_SET_CONDITION(condition_mask, VER_MINORVERSION, op);
  VER_SET_CONDITION(condition_mask, VER_SERVICEPACKMAJOR, op);
  VER_SET_CONDITION(condition_mask, VER_SERVICEPACKMINOR, op);

  return (int) VerifyVersionInfo(
    &osvi,
    VER_MAJORVERSION | VER_MINORVERSION |
    VER_SERVICEPACKMAJOR | VER_SERVICEPACKMINOR,
    condition_mask);
}


static int address_prefix_match(int family,
                                struct sockaddr* address,
                                struct sockaddr* prefix_address,
                                int prefix_len) {
  uint8_t* address_data;
  uint8_t* prefix_address_data;
  int i;

  assert(address->sa_family == family);
  assert(prefix_address->sa_family == family);

  if (family == AF_INET6) {
    address_data = (uint8_t*) &(((struct sockaddr_in6 *) address)->sin6_addr);
    prefix_address_data =
      (uint8_t*) &(((struct sockaddr_in6 *) prefix_address)->sin6_addr);
  } else {
    address_data = (uint8_t*) &(((struct sockaddr_in *) address)->sin_addr);
    prefix_address_data =
      (uint8_t*) &(((struct sockaddr_in *) prefix_address)->sin_addr);
  }

  for (i = 0; i < prefix_len >> 3; i++) {
    if (address_data[i] != prefix_address_data[i])
      return 0;
  }

  if (prefix_len % 8)
    return prefix_address_data[i] ==
      (address_data[i] & (0xff << (8 - prefix_len % 8)));

  return 1;
}


int uv_interface_addresses(uv_interface_address_t** addresses_ptr,
    int* count_ptr) {
  IP_ADAPTER_ADDRESSES* win_address_buf;
  ULONG win_address_buf_size;
  IP_ADAPTER_ADDRESSES* adapter;

  uv_interface_address_t* uv_address_buf;
  char* name_buf;
  size_t uv_address_buf_size;
  uv_interface_address_t* uv_address;

  int count;

  int is_vista_or_greater;
  ULONG flags;

  is_vista_or_greater = is_windows_version_or_greater(6, 0, 0, 0);
  if (is_vista_or_greater) {
    flags = GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST |
      GAA_FLAG_SKIP_DNS_SERVER;
  } else {
    if (!is_windows_version_or_greater(5, 1, 1, 0))
      return UV_ENOTSUP;

    flags = GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST |
      GAA_FLAG_SKIP_DNS_SERVER | GAA_FLAG_INCLUDE_PREFIX;
  }


  win_address_buf_size = 0;
  win_address_buf = NULL;

  for (;;) {
    ULONG r;

    r = GetAdaptersAddresses(AF_UNSPEC,
                             flags,
                             NULL,
                             win_address_buf,
                             &win_address_buf_size);

    if (r == ERROR_SUCCESS)
      break;

    uv__free(win_address_buf);

    switch (r) {
      case ERROR_BUFFER_OVERFLOW:
        win_address_buf = uv__malloc(win_address_buf_size);
        if (win_address_buf == NULL)
          return UV_ENOMEM;

        continue;

      case ERROR_NO_DATA: {
        uv_address_buf = uv__malloc(1);
        if (uv_address_buf == NULL)
          return UV_ENOMEM;

        *count_ptr = 0;
        *addresses_ptr = uv_address_buf;

        return 0;
      }

      case ERROR_ADDRESS_NOT_ASSOCIATED:
        return UV_EAGAIN;

      case ERROR_INVALID_PARAMETER:
        return UV_ENOBUFS;

      default:
        assert(r != ERROR_SUCCESS);
        return uv_translate_sys_error(r);
    }
  }

  count = 0;
  uv_address_buf_size = 0;

  for (adapter = win_address_buf;
       adapter != NULL;
       adapter = adapter->Next) {
    IP_ADAPTER_UNICAST_ADDRESS* unicast_address;
    int name_size;

    if (adapter->OperStatus != IfOperStatusUp ||
        adapter->FirstUnicastAddress == NULL)
      continue;

    name_size = WideCharToMultiByte(CP_UTF8,
                                    0,
                                    adapter->FriendlyName,
                                    -1,
                                    NULL,
                                    0,
                                    NULL,
                                    FALSE);
    if (name_size <= 0) {
      uv__free(win_address_buf);
      return uv_translate_sys_error(GetLastError());
    }
    uv_address_buf_size += name_size;

    for (unicast_address = (IP_ADAPTER_UNICAST_ADDRESS*)
                           adapter->FirstUnicastAddress;
         unicast_address != NULL;
         unicast_address = unicast_address->Next) {
      count++;
      uv_address_buf_size += sizeof(uv_interface_address_t);
    }
  }

  uv_address_buf = uv__malloc(uv_address_buf_size);
  if (uv_address_buf == NULL) {
    uv__free(win_address_buf);
    return UV_ENOMEM;
  }

  uv_address = uv_address_buf;
  name_buf = (char*) (uv_address_buf + count);

  for (adapter = win_address_buf;
       adapter != NULL;
       adapter = adapter->Next) {
    IP_ADAPTER_UNICAST_ADDRESS* unicast_address;
    int name_size;
    size_t max_name_size;

    if (adapter->OperStatus != IfOperStatusUp ||
        adapter->FirstUnicastAddress == NULL)
      continue;

    max_name_size = (char*) uv_address_buf + uv_address_buf_size - name_buf;
    if (max_name_size > (size_t) INT_MAX)
      max_name_size = INT_MAX;
    name_size = WideCharToMultiByte(CP_UTF8,
                                    0,
                                    adapter->FriendlyName,
                                    -1,
                                    name_buf,
                                    (int) max_name_size,
                                    NULL,
                                    FALSE);
    if (name_size <= 0) {
      uv__free(win_address_buf);
      uv__free(uv_address_buf);
      return uv_translate_sys_error(GetLastError());
    }

    for (unicast_address = (IP_ADAPTER_UNICAST_ADDRESS*)
                           adapter->FirstUnicastAddress;
         unicast_address != NULL;
         unicast_address = unicast_address->Next) {
      struct sockaddr* sa;
      ULONG prefix_len;

      sa = unicast_address->Address.lpSockaddr;

      if (is_vista_or_greater) {
        prefix_len =
          ((IP_ADAPTER_UNICAST_ADDRESS_LH*) unicast_address)->OnLinkPrefixLength;
      } else {
        IP_ADAPTER_PREFIX* prefix;
        prefix_len = 0;

        for (prefix = adapter->FirstPrefix; prefix; prefix = prefix->Next) {
          if (prefix->Address.lpSockaddr->sa_family != sa->sa_family ||
              prefix->PrefixLength <= prefix_len)
            continue;

          if (address_prefix_match(sa->sa_family, sa,
              prefix->Address.lpSockaddr, prefix->PrefixLength)) {
            prefix_len = prefix->PrefixLength;
          }
        }

        if (!prefix_len)
          prefix_len = (sa->sa_family == AF_INET6) ? 128 : 32;
      }

      memset(uv_address, 0, sizeof *uv_address);

      uv_address->name = name_buf;

      if (adapter->PhysicalAddressLength == sizeof(uv_address->phys_addr)) {
        memcpy(uv_address->phys_addr,
               adapter->PhysicalAddress,
               sizeof(uv_address->phys_addr));
      }

      uv_address->is_internal =
          (adapter->IfType == IF_TYPE_SOFTWARE_LOOPBACK);

      if (sa->sa_family == AF_INET6) {
        uv_address->address.address6 = *((struct sockaddr_in6 *) sa);

        uv_address->netmask.netmask6.sin6_family = AF_INET6;
        memset(uv_address->netmask.netmask6.sin6_addr.s6_addr, 0xff, prefix_len >> 3);
        if (prefix_len % 8) {
          uv_address->netmask.netmask6.sin6_addr.s6_addr[prefix_len >> 3] =
              0xff << (8 - prefix_len % 8);
        }

      } else {
        uv_address->address.address4 = *((struct sockaddr_in *) sa);

        uv_address->netmask.netmask4.sin_family = AF_INET;
        uv_address->netmask.netmask4.sin_addr.s_addr = (prefix_len > 0) ?
            htonl(0xffffffff << (32 - prefix_len)) : 0;
      }

      uv_address++;
    }

    name_buf += name_size;
  }

  uv__free(win_address_buf);

  *addresses_ptr = uv_address_buf;
  *count_ptr = count;

  return 0;
}


void uv_free_interface_addresses(uv_interface_address_t* addresses,
    int count) {
  uv__free(addresses);
}


int uv_getrusage(uv_rusage_t *uv_rusage) {
  FILETIME createTime, exitTime, kernelTime, userTime;
  SYSTEMTIME kernelSystemTime, userSystemTime;
  PROCESS_MEMORY_COUNTERS memCounters;
  IO_COUNTERS ioCounters;
  int ret;

  ret = GetProcessTimes(GetCurrentProcess(), &createTime, &exitTime, &kernelTime, &userTime);
  if (ret == 0) {
    return uv_translate_sys_error(GetLastError());
  }

  ret = FileTimeToSystemTime(&kernelTime, &kernelSystemTime);
  if (ret == 0) {
    return uv_translate_sys_error(GetLastError());
  }

  ret = FileTimeToSystemTime(&userTime, &userSystemTime);
  if (ret == 0) {
    return uv_translate_sys_error(GetLastError());
  }

  ret = GetProcessMemoryInfo(GetCurrentProcess(),
                             &memCounters,
                             sizeof(memCounters));
  if (ret == 0) {
    return uv_translate_sys_error(GetLastError());
  }

  ret = GetProcessIoCounters(GetCurrentProcess(), &ioCounters);
  if (ret == 0) {
    return uv_translate_sys_error(GetLastError());
  }

  memset(uv_rusage, 0, sizeof(*uv_rusage));

  uv_rusage->ru_utime.tv_sec = userSystemTime.wHour * 3600 +
                               userSystemTime.wMinute * 60 +
                               userSystemTime.wSecond;
  uv_rusage->ru_utime.tv_usec = userSystemTime.wMilliseconds * 1000;

  uv_rusage->ru_stime.tv_sec = kernelSystemTime.wHour * 3600 +
                               kernelSystemTime.wMinute * 60 +
                               kernelSystemTime.wSecond;
  uv_rusage->ru_stime.tv_usec = kernelSystemTime.wMilliseconds * 1000;

  uv_rusage->ru_majflt = (uint64_t) memCounters.PageFaultCount;
  uv_rusage->ru_maxrss = (uint64_t) memCounters.PeakWorkingSetSize / 1024;

  uv_rusage->ru_oublock = (uint64_t) ioCounters.WriteOperationCount;
  uv_rusage->ru_inblock = (uint64_t) ioCounters.ReadOperationCount;

  return 0;
}


int uv_os_homedir(char* buffer, size_t* size) {
  uv_passwd_t pwd;
  size_t len;
  int r;

  r = uv_os_getenv("USERPROFILE", buffer, size);

  if (r != UV_ENOENT)
    return r;

  r = uv__getpwuid_r(&pwd);

  if (r != 0) {
    return r;
  }

  len = strlen(pwd.homedir);

  if (len >= *size) {
    *size = len + 1;
    uv_os_free_passwd(&pwd);
    return UV_ENOBUFS;
  }

  memcpy(buffer, pwd.homedir, len + 1);
  *size = len;
  uv_os_free_passwd(&pwd);

  return 0;
}


int uv_os_tmpdir(char* buffer, size_t* size) {
  wchar_t path[MAX_PATH + 1];
  DWORD bufsize;
  size_t len;

  if (buffer == NULL || size == NULL || *size == 0)
    return UV_EINVAL;

  len = GetTempPathW(MAX_PATH + 1, path);

  if (len == 0) {
    return uv_translate_sys_error(GetLastError());
  } else if (len > MAX_PATH + 1) {
    return UV_EIO;
  }

  if (path[len - 1] == L'\\' &&
      !(len == 3 && path[1] == L':')) {
    len--;
    path[len] = L'\0';
  }

  bufsize = WideCharToMultiByte(CP_UTF8, 0, path, -1, NULL, 0, NULL, NULL);

  if (bufsize == 0) {
    return uv_translate_sys_error(GetLastError());
  } else if (bufsize > *size) {
    *size = bufsize;
    return UV_ENOBUFS;
  }

  bufsize = WideCharToMultiByte(CP_UTF8,
                                0,
                                path,
                                -1,
                                buffer,
                                *size,
                                NULL,
                                NULL);

  if (bufsize == 0)
    return uv_translate_sys_error(GetLastError());

  *size = bufsize - 1;
  return 0;
}


void uv_os_free_passwd(uv_passwd_t* pwd) {
  if (pwd == NULL)
    return;

  uv__free(pwd->username);
  uv__free(pwd->homedir);
  pwd->username = NULL;
  pwd->homedir = NULL;
}


int uv__convert_utf16_to_utf8(const WCHAR* utf16, int utf16len, char** utf8) {
  DWORD bufsize;

  if (utf16 == NULL)
    return UV_EINVAL;

  bufsize = WideCharToMultiByte(CP_UTF8,
                                0,
                                utf16,
                                utf16len,
                                NULL,
                                0,
                                NULL,
                                NULL);

  if (bufsize == 0)
    return uv_translate_sys_error(GetLastError());

  *utf8 = uv__malloc(bufsize + 1);

  if (*utf8 == NULL)
    return UV_ENOMEM;

  bufsize = WideCharToMultiByte(CP_UTF8,
                                0,
                                utf16,
                                utf16len,
                                *utf8,
                                bufsize,
                                NULL,
                                NULL);

  if (bufsize == 0) {
    uv__free(*utf8);
    *utf8 = NULL;
    return uv_translate_sys_error(GetLastError());
  }

  (*utf8)[bufsize] = '\0';
  return 0;
}


int uv__convert_utf8_to_utf16(const char* utf8, int utf8len, WCHAR** utf16) {
  int bufsize;

  if (utf8 == NULL)
    return UV_EINVAL;

  bufsize = MultiByteToWideChar(CP_UTF8, 0, utf8, utf8len, NULL, 0);

  if (bufsize == 0)
    return uv_translate_sys_error(GetLastError());

  *utf16 = uv__malloc(sizeof(WCHAR) * (bufsize + 1));

  if (*utf16 == NULL)
    return UV_ENOMEM;

  bufsize = MultiByteToWideChar(CP_UTF8, 0, utf8, utf8len, *utf16, bufsize);

  if (bufsize == 0) {
    uv__free(*utf16);
    *utf16 = NULL;
    return uv_translate_sys_error(GetLastError());
  }

  (*utf16)[bufsize] = '\0';
  return 0;
}


int uv__getpwuid_r(uv_passwd_t* pwd) {
  HANDLE token;
  wchar_t username[UNLEN + 1];
  wchar_t path[MAX_PATH];
  DWORD bufsize;
  int r;

  if (pwd == NULL)
    return UV_EINVAL;

  if (OpenProcessToken(GetCurrentProcess(), TOKEN_READ, &token) == 0)
    return uv_translate_sys_error(GetLastError());

  bufsize = ARRAY_SIZE(path);
  if (!GetUserProfileDirectoryW(token, path, &bufsize)) {
    r = GetLastError();
    CloseHandle(token);

    if (r == ERROR_INSUFFICIENT_BUFFER)
      return UV_ENOMEM;

    return uv_translate_sys_error(r);
  }

  CloseHandle(token);

  bufsize = ARRAY_SIZE(username);
  if (!GetUserNameW(username, &bufsize)) {
    r = GetLastError();

    if (r == ERROR_INSUFFICIENT_BUFFER)
      return UV_ENOMEM;

    return uv_translate_sys_error(r);
  }

  pwd->homedir = NULL;
  r = uv__convert_utf16_to_utf8(path, -1, &pwd->homedir);

  if (r != 0)
    return r;

  pwd->username = NULL;
  r = uv__convert_utf16_to_utf8(username, -1, &pwd->username);

  if (r != 0) {
    uv__free(pwd->homedir);
    return r;
  }

  pwd->shell = NULL;
  pwd->uid = -1;
  pwd->gid = -1;

  return 0;
}


int uv_os_get_passwd(uv_passwd_t* pwd) {
  return uv__getpwuid_r(pwd);
}


int uv_os_getenv(const char* name, char* buffer, size_t* size) {
  wchar_t var[MAX_ENV_VAR_LENGTH];
  wchar_t* name_w;
  DWORD bufsize;
  size_t len;
  int r;

  if (name == NULL || buffer == NULL || size == NULL || *size == 0)
    return UV_EINVAL;

  r = uv__convert_utf8_to_utf16(name, -1, &name_w);

  if (r != 0)
    return r;

  len = GetEnvironmentVariableW(name_w, var, MAX_ENV_VAR_LENGTH);
  uv__free(name_w);
  assert(len < MAX_ENV_VAR_LENGTH);

  if (len == 0) {
    r = GetLastError();

    if (r == ERROR_ENVVAR_NOT_FOUND)
      return UV_ENOENT;

    return uv_translate_sys_error(r);
  }

  bufsize = WideCharToMultiByte(CP_UTF8, 0, var, -1, NULL, 0, NULL, NULL);

  if (bufsize == 0) {
    return uv_translate_sys_error(GetLastError());
  } else if (bufsize > *size) {
    *size = bufsize;
    return UV_ENOBUFS;
  }

  bufsize = WideCharToMultiByte(CP_UTF8,
                                0,
                                var,
                                -1,
                                buffer,
                                *size,
                                NULL,
                                NULL);

  if (bufsize == 0)
    return uv_translate_sys_error(GetLastError());

  *size = bufsize - 1;
  return 0;
}


int uv_os_setenv(const char* name, const char* value) {
  wchar_t* name_w;
  wchar_t* value_w;
  int r;

  if (name == NULL || value == NULL)
    return UV_EINVAL;

  r = uv__convert_utf8_to_utf16(name, -1, &name_w);

  if (r != 0)
    return r;

  r = uv__convert_utf8_to_utf16(value, -1, &value_w);

  if (r != 0) {
    uv__free(name_w);
    return r;
  }

  r = SetEnvironmentVariableW(name_w, value_w);
  uv__free(name_w);
  uv__free(value_w);

  if (r == 0)
    return uv_translate_sys_error(GetLastError());

  return 0;
}


int uv_os_unsetenv(const char* name) {
  wchar_t* name_w;
  int r;

  if (name == NULL)
    return UV_EINVAL;

  r = uv__convert_utf8_to_utf16(name, -1, &name_w);

  if (r != 0)
    return r;

  r = SetEnvironmentVariableW(name_w, NULL);
  uv__free(name_w);

  if (r == 0)
    return uv_translate_sys_error(GetLastError());

  return 0;
}


int uv_os_gethostname(char* buffer, size_t* size) {
  char buf[MAXHOSTNAMELEN + 1];
  size_t len;

  if (buffer == NULL || size == NULL || *size == 0)
    return UV_EINVAL;

  uv__once_init();

  if (gethostname(buf, sizeof(buf)) != 0)
    return uv_translate_sys_error(WSAGetLastError());

  buf[sizeof(buf) - 1] = '\0';
  len = strlen(buf);

  if (len >= *size) {
    *size = len + 1;
    return UV_ENOBUFS;
  }

  memcpy(buffer, buf, len + 1);
  *size = len;
  return 0;
}
