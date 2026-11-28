
#include "internal.h"
#include <sys/ioctl.h>
#include <net/if.h>
#include <utmpx.h>
#include <unistd.h>
#include <sys/ps.h>
#include <builtins.h>
#include <termios.h>
#include <sys/msg.h>
#if defined(__clang__)
#include "csrsic.h"
#else
#include "//'SYS1.SAMPLIB(CSRSIC)'"
#endif

#define CVT_PTR           0x10
#define PSA_PTR           0x00
#define CSD_OFFSET        0x294

#define RCTLACS_OFFSET    0xC4

#define CSD_NUMBER_ONLINE_CPUS        0xD4

#define CVTOPCTP_OFFSET   0x25C

#define RMCTRCT_OFFSET    0xE4

#define CVTRCEP_OFFSET    0x490

#define RCEPOOL_OFFSET    0x004

#define RCEAFC_OFFSET     0x088

#define CPCMODEL_LENGTH   16

#define PSAAOLD           0x224

#define ASCBRSME          0x16C

#define RAXFMCT           0x2C

#define PGTH_CURRENT  1
#define PGTH_LEN      26
#define PGTHAPATH     0x20
#pragma linkage(BPX4GTH, OS)
#pragma linkage(BPX1GTH, OS)

#define TOD_RES 4.096

typedef unsigned data_area_ptr_assign_type;

typedef union {
  struct {
#if defined(_LP64)
    data_area_ptr_assign_type lower;
#endif
    data_area_ptr_assign_type assign;
  };
  char* deref;
} data_area_ptr;


void uv_loadavg(double avg[3]) {
  avg[0] = 0;
  avg[1] = 0;
  avg[2] = 0;
}


int uv__platform_loop_init(uv_loop_t* loop) {
  uv__os390_epoll* ep;

  ep = epoll_create1(0);
  loop->ep = ep;
  if (ep == NULL)
    return UV__ERR(errno);

  return 0;
}


void uv__platform_loop_delete(uv_loop_t* loop) {
  if (loop->ep != NULL) {
    epoll_queue_close(loop->ep);
    loop->ep = NULL;
  }
}


uint64_t uv__hrtime(uv_clocktype_t type) {
  unsigned long long timestamp;
  __stckf(&timestamp);
  return timestamp / TOD_RES;
}


static int getexe(const int pid, char* buf, size_t len) {
  struct {
    int pid;
    int thid[2];
    char accesspid;
    char accessthid;
    char asid[2];
    char loginname[8];
    char flag;
    char len;
  } Input_data;

  union {
    struct {
      char gthb[4];
      int pid;
      int thid[2];
      char accesspid;
      char accessthid[3];
      int lenused;
      int offsetProcess;
      int offsetConTTY;
      int offsetPath;
      int offsetCommand;
      int offsetFileData;
      int offsetThread;
    } Output_data;
    char buf[2048];
  } Output_buf;

  struct Output_path_type {
    char gthe[4];
    short int len;
    char path[1024];
  };

  int Input_length;
  int Output_length;
  void* Input_address;
  void* Output_address;
  struct Output_path_type* Output_path;
  int rv;
  int rc;
  int rsn;

  Input_length = PGTH_LEN;
  Output_length = sizeof(Output_buf);
  Output_address = &Output_buf;
  Input_address = &Input_data;
  memset(&Input_data, 0, sizeof Input_data);
  Input_data.flag |= PGTHAPATH;
  Input_data.pid = pid;
  Input_data.accesspid = PGTH_CURRENT;

#ifdef _LP64
  BPX4GTH(&Input_length,
          &Input_address,
          &Output_length,
          &Output_address,
          &rv,
          &rc,
          &rsn);
#else
  BPX1GTH(&Input_length,
          &Input_address,
          &Output_length,
          &Output_address,
          &rv,
          &rc,
          &rsn);
#endif

  if (rv == -1) {
    errno = rc;
    return -1;
  }

  assert(((Output_buf.Output_data.offsetPath >>24) & 0xFF) == 'A');

  Output_path = (char*)(&Output_buf) +
                (Output_buf.Output_data.offsetPath & 0x00FFFFFF);

  if (Output_path->len >= len) {
    errno = ENOBUFS;
    return -1;
  }

  strncpy(buf, Output_path->path, len);

  return 0;
}


int uv_exepath(char* buffer, size_t* size) {
  int res;
  char args[PATH_MAX];
  char abspath[PATH_MAX];
  size_t abspath_size;
  int pid;

  if (buffer == NULL || size == NULL || *size == 0)
    return UV_EINVAL;

  pid = getpid();
  res = getexe(pid, args, sizeof(args));
  if (res < 0)
    return UV_EINVAL;


  if (strchr(args, '/') != NULL) {
    if (realpath(args, abspath) != abspath)
      return UV__ERR(errno);

    abspath_size = strlen(abspath);

    *size -= 1;
    if (*size > abspath_size)
      *size = abspath_size;

    memcpy(buffer, abspath, *size);
    buffer[*size] = '\0';

    return 0;
  } else {
    char trypath[PATH_MAX];
    char* clonedpath = NULL;
    char* token = NULL;
    char* path = getenv("PATH");

    if (path == NULL)
      return UV_EINVAL;

    clonedpath = uv__strdup(path);
    if (clonedpath == NULL)
      return UV_ENOMEM;

    token = strtok(clonedpath, ":");
    while (token != NULL) {
      snprintf(trypath, sizeof(trypath) - 1, "%s/%s", token, args);
      if (realpath(trypath, abspath) == abspath) {
        if (access(abspath, X_OK) == 0) {
          abspath_size = strlen(abspath);

          *size -= 1;
          if (*size > abspath_size)
            *size = abspath_size;

          memcpy(buffer, abspath, *size);
          buffer[*size] = '\0';

          uv__free(clonedpath);
          return 0;
        }
      }
      token = strtok(NULL, ":");
    }
    uv__free(clonedpath);

    return UV_EINVAL;
  }
}


uint64_t uv_get_free_memory(void) {
  uint64_t freeram;

  data_area_ptr cvt = {0};
  data_area_ptr rcep = {0};
  cvt.assign = *(data_area_ptr_assign_type*)(CVT_PTR);
  rcep.assign = *(data_area_ptr_assign_type*)(cvt.deref + CVTRCEP_OFFSET);
  freeram = *((uint64_t*)(rcep.deref + RCEAFC_OFFSET)) * 4;
  return freeram;
}


uint64_t uv_get_total_memory(void) {
  uint64_t totalram;

  data_area_ptr cvt = {0};
  data_area_ptr rcep = {0};
  cvt.assign = *(data_area_ptr_assign_type*)(CVT_PTR);
  rcep.assign = *(data_area_ptr_assign_type*)(cvt.deref + CVTRCEP_OFFSET);
  totalram = *((uint64_t*)(rcep.deref + RCEPOOL_OFFSET)) * 4;
  return totalram;
}


int uv_resident_set_memory(size_t* rss) {
  char* psa;
  char* ascb;
  char* rax;
  size_t nframes;

  psa = PSA_PTR;
  ascb  = *(char* __ptr32 *)(psa + PSAAOLD);
  rax = *(char* __ptr32 *)(ascb + ASCBRSME);
  nframes = *(unsigned int*)(rax + RAXFMCT);

  *rss = nframes * sysconf(_SC_PAGESIZE);
  return 0;
}


int uv_uptime(double* uptime) {
  struct utmpx u ;
  struct utmpx *v;
  time64_t t;

  u.ut_type = BOOT_TIME;
  v = getutxid(&u);
  if (v == NULL)
    return -1;
  *uptime = difftime64(time64(&t), v->ut_tv.tv_sec);
  return 0;
}


int uv_cpu_info(uv_cpu_info_t** cpu_infos, int* count) {
  uv_cpu_info_t* cpu_info;
  int idx;
  siv1v2 info;
  data_area_ptr cvt = {0};
  data_area_ptr csd = {0};
  data_area_ptr rmctrct = {0};
  data_area_ptr cvtopctp = {0};
  int cpu_usage_avg;

  cvt.assign = *(data_area_ptr_assign_type*)(CVT_PTR);

  csd.assign = *((data_area_ptr_assign_type *) (cvt.deref + CSD_OFFSET));
  cvtopctp.assign = *((data_area_ptr_assign_type *) (cvt.deref + CVTOPCTP_OFFSET));
  rmctrct.assign = *((data_area_ptr_assign_type *) (cvtopctp.deref + RMCTRCT_OFFSET));

  *count = *((int*) (csd.deref + CSD_NUMBER_ONLINE_CPUS));
  cpu_usage_avg = *((unsigned short int*) (rmctrct.deref + RCTLACS_OFFSET));

  *cpu_infos = uv__malloc(*count * sizeof(uv_cpu_info_t));
  if (!*cpu_infos)
    return UV_ENOMEM;

  cpu_info = *cpu_infos;
  idx = 0;
  while (idx < *count) {
    cpu_info->speed = *(int*)(info.siv1v2si22v1.si22v1cpucapability);
    cpu_info->model = uv__malloc(CPCMODEL_LENGTH + 1);
    memset(cpu_info->model, '\0', CPCMODEL_LENGTH + 1);
    memcpy(cpu_info->model, info.siv1v2si11v1.si11v1cpcmodel, CPCMODEL_LENGTH);
    cpu_info->cpu_times.user = cpu_usage_avg;
    cpu_info->cpu_times.sys = 0;
    cpu_info->cpu_times.idle = 0;
    cpu_info->cpu_times.irq = 0;
    cpu_info->cpu_times.nice = 0;
    ++cpu_info;
    ++idx;
  }

  return 0;
}


void uv_free_cpu_info(uv_cpu_info_t* cpu_infos, int count) {
  for (int i = 0; i < count; ++i)
    uv__free(cpu_infos[i].model);
  uv__free(cpu_infos);
}


static int uv__interface_addresses_v6(uv_interface_address_t** addresses,
                                      int* count) {
  uv_interface_address_t* address;
  int sockfd;
  int maxsize;
  __net_ifconf6header_t ifc;
  __net_ifconf6entry_t* ifr;
  __net_ifconf6entry_t* p;
  __net_ifconf6entry_t flg;

  *count = 0;
  maxsize = 16384;

  if (0 > (sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP)))
    return UV__ERR(errno);

  ifc.__nif6h_version = 1;
  ifc.__nif6h_buflen = maxsize;
  ifc.__nif6h_buffer = uv__calloc(1, maxsize);;

  if (ioctl(sockfd, SIOCGIFCONF6, &ifc) == -1) {
    uv__close(sockfd);
    return UV__ERR(errno);
  }


  *count = 0;
  ifr = (__net_ifconf6entry_t*)(ifc.__nif6h_buffer);
  while ((char*)ifr < (char*)ifc.__nif6h_buffer + ifc.__nif6h_buflen) {
    p = ifr;
    ifr = (__net_ifconf6entry_t*)((char*)ifr + ifc.__nif6h_entrylen);

    if (!(p->__nif6e_addr.sin6_family == AF_INET6 ||
          p->__nif6e_addr.sin6_family == AF_INET))
      continue;

    if (!(p->__nif6e_flags & _NIF6E_FLAGS_ON_LINK_ACTIVE))
      continue;

    ++(*count);
  }

  *addresses = uv__malloc(*count * sizeof(uv_interface_address_t));
  if (!(*addresses)) {
    uv__close(sockfd);
    return UV_ENOMEM;
  }
  address = *addresses;

  ifr = (__net_ifconf6entry_t*)(ifc.__nif6h_buffer);
  while ((char*)ifr < (char*)ifc.__nif6h_buffer + ifc.__nif6h_buflen) {
    p = ifr;
    ifr = (__net_ifconf6entry_t*)((char*)ifr + ifc.__nif6h_entrylen);

    if (!(p->__nif6e_addr.sin6_family == AF_INET6 ||
          p->__nif6e_addr.sin6_family == AF_INET))
      continue;

    if (!(p->__nif6e_flags & _NIF6E_FLAGS_ON_LINK_ACTIVE))
      continue;


    address->name = uv__strdup(p->__nif6e_name);

    if (p->__nif6e_addr.sin6_family == AF_INET6)
      address->address.address6 = *((struct sockaddr_in6*) &p->__nif6e_addr);
    else
      address->address.address4 = *((struct sockaddr_in*) &p->__nif6e_addr);


    address->is_internal = flg.__nif6e_flags & _NIF6E_FLAGS_LOOPBACK ? 1 : 0;

    address++;
  }

  uv__close(sockfd);
  return 0;
}


int uv_interface_addresses(uv_interface_address_t** addresses, int* count) {
  uv_interface_address_t* address;
  int sockfd;
  int maxsize;
  struct ifconf ifc;
  struct ifreq flg;
  struct ifreq* ifr;
  struct ifreq* p;
  int count_v6;

  uv_interface_address_t* addresses_v6;
  uv__interface_addresses_v6(&addresses_v6, &count_v6);

  *count = 0;

  maxsize = 16384;

  sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
  if (0 > sockfd)
    return UV__ERR(errno);

  ifc.ifc_req = uv__calloc(1, maxsize);
  ifc.ifc_len = maxsize;
  if (ioctl(sockfd, SIOCGIFCONF, &ifc) == -1) {
    uv__close(sockfd);
    return UV__ERR(errno);
  }

#define MAX(a,b) (((a)>(b))?(a):(b))
#define ADDR_SIZE(p) MAX((p).sa_len, sizeof(p))

  ifr = ifc.ifc_req;
  while ((char*)ifr < (char*)ifc.ifc_req + ifc.ifc_len) {
    p = ifr;
    ifr = (struct ifreq*)
      ((char*)ifr + sizeof(ifr->ifr_name) + ADDR_SIZE(ifr->ifr_addr));

    if (!(p->ifr_addr.sa_family == AF_INET6 ||
          p->ifr_addr.sa_family == AF_INET))
      continue;

    memcpy(flg.ifr_name, p->ifr_name, sizeof(flg.ifr_name));
    if (ioctl(sockfd, SIOCGIFFLAGS, &flg) == -1) {
      uv__close(sockfd);
      return UV__ERR(errno);
    }

    if (!(flg.ifr_flags & IFF_UP && flg.ifr_flags & IFF_RUNNING))
      continue;

    (*count)++;
  }

  *addresses = uv__malloc((*count + count_v6) *
                          sizeof(uv_interface_address_t));

  if (!(*addresses)) {
    uv__close(sockfd);
    return UV_ENOMEM;
  }
  address = *addresses;

  memcpy(address, addresses_v6, count_v6 * sizeof(uv_interface_address_t));
  address += count_v6;
  *count += count_v6;
  uv__free(addresses_v6);

  ifr = ifc.ifc_req;
  while ((char*)ifr < (char*)ifc.ifc_req + ifc.ifc_len) {
    p = ifr;
    ifr = (struct ifreq*)
      ((char*)ifr + sizeof(ifr->ifr_name) + ADDR_SIZE(ifr->ifr_addr));

    if (!(p->ifr_addr.sa_family == AF_INET6 ||
          p->ifr_addr.sa_family == AF_INET))
      continue;

    memcpy(flg.ifr_name, p->ifr_name, sizeof(flg.ifr_name));
    if (ioctl(sockfd, SIOCGIFFLAGS, &flg) == -1) {
      uv__close(sockfd);
      return UV_ENOSYS;
    }

    if (!(flg.ifr_flags & IFF_UP && flg.ifr_flags & IFF_RUNNING))
      continue;


    address->name = uv__strdup(p->ifr_name);

    if (p->ifr_addr.sa_family == AF_INET6) {
      address->address.address6 = *((struct sockaddr_in6*) &p->ifr_addr);
    } else {
      address->address.address4 = *((struct sockaddr_in*) &p->ifr_addr);
    }

    address->is_internal = flg.ifr_flags & IFF_LOOPBACK ? 1 : 0;
    address++;
  }

#undef ADDR_SIZE
#undef MAX

  uv__close(sockfd);
  return 0;
}


void uv_free_interface_addresses(uv_interface_address_t* addresses,
                                 int count) {
  int i;
  for (i = 0; i < count; ++i)
    uv__free(addresses[i].name);
  uv__free(addresses);
}


void uv__platform_invalidate_fd(uv_loop_t* loop, int fd) {
  struct epoll_event* events;
  struct epoll_event dummy;
  uintptr_t i;
  uintptr_t nfds;

  assert(loop->watchers != NULL);

  events = (struct epoll_event*) loop->watchers[loop->nwatchers];
  nfds = (uintptr_t) loop->watchers[loop->nwatchers + 1];
  if (events != NULL)
    for (i = 0; i < nfds; i++)
      if ((int) events[i].fd == fd)
        events[i].fd = -1;

  if (loop->ep != NULL)
    epoll_ctl(loop->ep, UV__EPOLL_CTL_DEL, fd, &dummy);
}


int uv__io_check_fd(uv_loop_t* loop, int fd) {
  struct pollfd p[1];
  int rv;

  p[0].fd = fd;
  p[0].events = POLLIN;

  do
    rv = poll(p, 1, 0);
  while (rv == -1 && errno == EINTR);

  if (rv == -1)
    abort();

  if (p[0].revents & POLLNVAL)
    return -1;

  return 0;
}


void uv__fs_event_close(uv_fs_event_t* handle) {
  uv_fs_event_stop(handle);
}


int uv_fs_event_init(uv_loop_t* loop, uv_fs_event_t* handle) {
  uv__handle_init(loop, (uv_handle_t*)handle, UV_FS_EVENT);
  return 0;
}


int uv_fs_event_start(uv_fs_event_t* handle, uv_fs_event_cb cb,
                      const char* filename, unsigned int flags) {
  uv__os390_epoll* ep;
  _RFIS reg_struct;
  char* path;
  int rc;

  if (uv__is_active(handle))
    return UV_EINVAL;

  ep = handle->loop->ep;
  assert(ep->msg_queue != -1);

  reg_struct.__rfis_cmd  = _RFIS_REG;
  reg_struct.__rfis_qid  = ep->msg_queue;
  reg_struct.__rfis_type = 1;
  memcpy(reg_struct.__rfis_utok, &handle, sizeof(handle));

  path = uv__strdup(filename);
  if (path == NULL)
    return UV_ENOMEM;

  rc = __w_pioctl(path, _IOCC_REGFILEINT, sizeof(reg_struct), &reg_struct);
  if (rc != 0)
    return UV__ERR(errno);

  uv__handle_start(handle);
  handle->path = path;
  handle->cb = cb;
  memcpy(handle->rfis_rftok, reg_struct.__rfis_rftok,
         sizeof(handle->rfis_rftok));

  return 0;
}


int uv_fs_event_stop(uv_fs_event_t* handle) {
  uv__os390_epoll* ep;
  _RFIS reg_struct;
  int rc;

  if (!uv__is_active(handle))
    return 0;

  ep = handle->loop->ep;
  assert(ep->msg_queue != -1);

  reg_struct.__rfis_cmd  = _RFIS_UNREG;
  reg_struct.__rfis_qid  = ep->msg_queue;
  reg_struct.__rfis_type = 1;
  memcpy(reg_struct.__rfis_rftok, handle->rfis_rftok,
         sizeof(handle->rfis_rftok));

  rc = __w_pioctl("/", _IOCC_REGFILEINT, sizeof(reg_struct), &reg_struct);
  if (rc != 0 && errno != EALREADY && errno != ENOENT)
    abort();

  uv__handle_stop(handle);

  return 0;
}


static int os390_message_queue_handler(uv__os390_epoll* ep) {
  uv_fs_event_t* handle;
  int msglen;
  int events;
  _RFIM msg;

  if (ep->msg_queue == -1)
    return 0;

  msglen = msgrcv(ep->msg_queue, &msg, sizeof(msg), 0, IPC_NOWAIT);

  if (msglen == -1 && errno == ENOMSG)
    return 0;

  if (msglen == -1)
    abort();

  events = 0;
  if (msg.__rfim_event == _RFIM_ATTR || msg.__rfim_event == _RFIM_WRITE)
    events = UV_CHANGE;
  else if (msg.__rfim_event == _RFIM_RENAME)
    events = UV_RENAME;
  else
    return 0;

  handle = *(uv_fs_event_t**)(msg.__rfim_utok);
  handle->cb(handle, uv__basename_r(handle->path), events, 0);
  return 1;
}


void uv__io_poll(uv_loop_t* loop, int timeout) {
  static const int max_safe_timeout = 1789569;
  struct epoll_event events[1024];
  struct epoll_event* pe;
  struct epoll_event e;
  uv__os390_epoll* ep;
  int real_timeout;
  QUEUE* q;
  uv__io_t* w;
  uint64_t base;
  int count;
  int nfds;
  int fd;
  int op;
  int i;

  if (loop->nfds == 0) {
    assert(QUEUE_EMPTY(&loop->watcher_queue));
    return;
  }

  while (!QUEUE_EMPTY(&loop->watcher_queue)) {
    uv_stream_t* stream;

    q = QUEUE_HEAD(&loop->watcher_queue);
    QUEUE_REMOVE(q);
    QUEUE_INIT(q);
    w = QUEUE_DATA(q, uv__io_t, watcher_queue);

    assert(w->pevents != 0);
    assert(w->fd >= 0);

    stream= container_of(w, uv_stream_t, io_watcher);

    assert(w->fd < (int) loop->nwatchers);

    e.events = w->pevents;
    e.fd = w->fd;

    if (w->events == 0)
      op = UV__EPOLL_CTL_ADD;
    else
      op = UV__EPOLL_CTL_MOD;

    if (epoll_ctl(loop->ep, op, w->fd, &e)) {
      if (errno != EEXIST)
        abort();

      assert(op == UV__EPOLL_CTL_ADD);

      if (epoll_ctl(loop->ep, UV__EPOLL_CTL_MOD, w->fd, &e))
        abort();
    }

    w->events = w->pevents;
  }

  assert(timeout >= -1);
  base = loop->time;
  count = 48;
  real_timeout = timeout;
  int nevents = 0;

  nfds = 0;
  for (;;) {
    if (sizeof(int32_t) == sizeof(long) && timeout >= max_safe_timeout)
      timeout = max_safe_timeout;

    nfds = epoll_wait(loop->ep, events,
                      ARRAY_SIZE(events), timeout);

    base = loop->time;
    SAVE_ERRNO(uv__update_time(loop));
    if (nfds == 0) {
      assert(timeout != -1);

      if (timeout > 0) {
        timeout = real_timeout - timeout;
        continue;
      }

      return;
    }

    if (nfds == -1) {

      if (errno != EINTR)
        abort();

      if (timeout == -1)
        continue;

      if (timeout == 0)
        return;

      goto update_timeout;
    }


    assert(loop->watchers != NULL);
    loop->watchers[loop->nwatchers] = (void*) events;
    loop->watchers[loop->nwatchers + 1] = (void*) (uintptr_t) nfds;
    for (i = 0; i < nfds; i++) {
      pe = events + i;
      fd = pe->fd;

      if (fd == -1)
        continue;

      ep = loop->ep;
      if (fd == ep->msg_queue) {
        os390_message_queue_handler(ep);
        continue;
      }

      assert(fd >= 0);
      assert((unsigned) fd < loop->nwatchers);

      w = loop->watchers[fd];

      if (w == NULL) {
        epoll_ctl(loop->ep, UV__EPOLL_CTL_DEL, fd, pe);
        continue;
      }

      pe->events &= w->pevents | POLLERR | POLLHUP;

      if (pe->events == POLLERR || pe->events == POLLHUP)
        pe->events |= w->pevents & (POLLIN | POLLOUT);

      if (pe->events != 0) {
        w->cb(loop, w, pe->events);
        nevents++;
      }
    }
    loop->watchers[loop->nwatchers] = NULL;
    loop->watchers[loop->nwatchers + 1] = NULL;

    if (nevents != 0) {
      if (nfds == ARRAY_SIZE(events) && --count != 0) {
        timeout = 0;
        continue;
      }
      return;
    }

    if (timeout == 0)
      return;

    if (timeout == -1)
      continue;

update_timeout:
    assert(timeout > 0);

    real_timeout -= (loop->time - base);
    if (real_timeout <= 0)
      return;

    timeout = real_timeout;
  }
}

void uv__set_process_title(const char* title) {
}

int uv__io_fork(uv_loop_t* loop) {
  loop->ep = NULL;

  uv__platform_loop_delete(loop);
  return uv__platform_loop_init(loop);
}
