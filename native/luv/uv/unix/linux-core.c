

#include "uv.h"
#include "internal.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <errno.h>

#include <net/if.h>
#include <sys/param.h>
#include <sys/prctl.h>
#include <sys/sysinfo.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>

#define HAVE_IFADDRS_H 1

#ifdef __UCLIBC__
# if __UCLIBC_MAJOR__ < 0 && __UCLIBC_MINOR__ < 9 && __UCLIBC_SUBLEVEL__ < 32
#  undef HAVE_IFADDRS_H
# endif
#endif

#ifdef HAVE_IFADDRS_H
# if defined(__ANDROID__)
#  include "uv/android-ifaddrs.h"
# else
#  include <ifaddrs.h>
# endif
# include <sys/socket.h>
# include <net/ethernet.h>
# include <netpacket/packet.h>
#endif

#ifndef CLOCK_MONOTONIC_COARSE
# define CLOCK_MONOTONIC_COARSE 6
#endif

#ifndef CLOCK_BOOTTIME
# define CLOCK_BOOTTIME 7
#endif

static int read_models(unsigned int numcpus, uv_cpu_info_t* ci);
static int read_times(FILE* statfile_fp,
                      unsigned int numcpus,
                      uv_cpu_info_t* ci);
static void read_speeds(unsigned int numcpus, uv_cpu_info_t* ci);
static unsigned long read_cpufreq(unsigned int cpunum);


int uv__platform_loop_init(uv_loop_t* loop) {
  int fd;

  fd = uv__epoll_create1(UV__EPOLL_CLOEXEC);

  if (fd == -1 && (errno == ENOSYS || errno == EINVAL)) {
    fd = uv__epoll_create(256);

    if (fd != -1)
      uv__cloexec(fd, 1);
  }

  loop->backend_fd = fd;
  loop->inotify_fd = -1;
  loop->inotify_watchers = NULL;

  if (fd == -1)
    return UV__ERR(errno);

  return 0;
}


int uv__io_fork(uv_loop_t* loop) {
  int err;
  void* old_watchers;

  old_watchers = loop->inotify_watchers;

  uv__close(loop->backend_fd);
  loop->backend_fd = -1;
  uv__platform_loop_delete(loop);

  err = uv__platform_loop_init(loop);
  if (err)
    return err;

  return uv__inotify_fork(loop, old_watchers);
}


void uv__platform_loop_delete(uv_loop_t* loop) {
  if (loop->inotify_fd == -1) return;
  uv__io_stop(loop, &loop->inotify_read_watcher, POLLIN);
  uv__close(loop->inotify_fd);
  loop->inotify_fd = -1;
}


void uv__platform_invalidate_fd(uv_loop_t* loop, int fd) {
  struct uv__epoll_event* events;
  struct uv__epoll_event dummy;
  uintptr_t i;
  uintptr_t nfds;

  assert(loop->watchers != NULL);

  events = (struct uv__epoll_event*) loop->watchers[loop->nwatchers];
  nfds = (uintptr_t) loop->watchers[loop->nwatchers + 1];
  if (events != NULL)
    for (i = 0; i < nfds; i++)
      if ((int) events[i].data == fd)
        events[i].data = -1;

  if (loop->backend_fd >= 0) {
    memset(&dummy, 0, sizeof(dummy));
    uv__epoll_ctl(loop->backend_fd, UV__EPOLL_CTL_DEL, fd, &dummy);
  }
}


int uv__io_check_fd(uv_loop_t* loop, int fd) {
  struct uv__epoll_event e;
  int rc;

  e.events = POLLIN;
  e.data = -1;

  rc = 0;
  if (uv__epoll_ctl(loop->backend_fd, UV__EPOLL_CTL_ADD, fd, &e))
    if (errno != EEXIST)
      rc = UV__ERR(errno);

  if (rc == 0)
    if (uv__epoll_ctl(loop->backend_fd, UV__EPOLL_CTL_DEL, fd, &e))
      abort();

  return rc;
}


void uv__io_poll(uv_loop_t* loop, int timeout) {
  static const int max_safe_timeout = 1789569;
  static int no_epoll_pwait;
  static int no_epoll_wait;
  struct uv__epoll_event events[1024];
  struct uv__epoll_event* pe;
  struct uv__epoll_event e;
  int real_timeout;
  QUEUE* q;
  uv__io_t* w;
  sigset_t sigset;
  uint64_t sigmask;
  uint64_t base;
  int have_signals;
  int nevents;
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
    q = QUEUE_HEAD(&loop->watcher_queue);
    QUEUE_REMOVE(q);
    QUEUE_INIT(q);

    w = QUEUE_DATA(q, uv__io_t, watcher_queue);
    assert(w->pevents != 0);
    assert(w->fd >= 0);
    assert(w->fd < (int) loop->nwatchers);

    e.events = w->pevents;
    e.data = w->fd;

    if (w->events == 0)
      op = UV__EPOLL_CTL_ADD;
    else
      op = UV__EPOLL_CTL_MOD;

    if (uv__epoll_ctl(loop->backend_fd, op, w->fd, &e)) {
      if (errno != EEXIST)
        abort();

      assert(op == UV__EPOLL_CTL_ADD);

      if (uv__epoll_ctl(loop->backend_fd, UV__EPOLL_CTL_MOD, w->fd, &e))
        abort();
    }

    w->events = w->pevents;
  }

  sigmask = 0;
  if (loop->flags & UV_LOOP_BLOCK_SIGPROF) {
    sigemptyset(&sigset);
    sigaddset(&sigset, SIGPROF);
    sigmask |= 1 << (SIGPROF - 1);
  }

  assert(timeout >= -1);
  base = loop->time;
  count = 48;
  real_timeout = timeout;

  for (;;) {
    if (sizeof(int32_t) == sizeof(long) && timeout >= max_safe_timeout)
      timeout = max_safe_timeout;

    if (sigmask != 0 && no_epoll_pwait != 0)
      if (pthread_sigmask(SIG_BLOCK, &sigset, NULL))
        abort();

    if (no_epoll_wait != 0 || (sigmask != 0 && no_epoll_pwait == 0)) {
      nfds = uv__epoll_pwait(loop->backend_fd,
                             events,
                             ARRAY_SIZE(events),
                             timeout,
                             sigmask);
      if (nfds == -1 && errno == ENOSYS)
        no_epoll_pwait = 1;
    } else {
      nfds = uv__epoll_wait(loop->backend_fd,
                            events,
                            ARRAY_SIZE(events),
                            timeout);
      if (nfds == -1 && errno == ENOSYS)
        no_epoll_wait = 1;
    }

    if (sigmask != 0 && no_epoll_pwait != 0)
      if (pthread_sigmask(SIG_UNBLOCK, &sigset, NULL))
        abort();

    SAVE_ERRNO(uv__update_time(loop));

    if (nfds == 0) {
      assert(timeout != -1);

      if (timeout == 0)
        return;

      goto update_timeout;
    }

    if (nfds == -1) {
      if (errno == ENOSYS) {
        assert(no_epoll_wait == 0 || no_epoll_pwait == 0);
        continue;
      }

      if (errno != EINTR)
        abort();

      if (timeout == -1)
        continue;

      if (timeout == 0)
        return;

      goto update_timeout;
    }

    have_signals = 0;
    nevents = 0;

    assert(loop->watchers != NULL);
    loop->watchers[loop->nwatchers] = (void*) events;
    loop->watchers[loop->nwatchers + 1] = (void*) (uintptr_t) nfds;
    for (i = 0; i < nfds; i++) {
      pe = events + i;
      fd = pe->data;

      if (fd == -1)
        continue;

      assert(fd >= 0);
      assert((unsigned) fd < loop->nwatchers);

      w = loop->watchers[fd];

      if (w == NULL) {
        uv__epoll_ctl(loop->backend_fd, UV__EPOLL_CTL_DEL, fd, pe);
        continue;
      }

      pe->events &= w->pevents | POLLERR | POLLHUP;

      if (pe->events == POLLERR || pe->events == POLLHUP)
        pe->events |=
          w->pevents & (POLLIN | POLLOUT | UV__POLLRDHUP | UV__POLLPRI);

      if (pe->events != 0) {
        if (w == &loop->signal_io_watcher)
          have_signals = 1;
        else
          w->cb(loop, w, pe->events);

        nevents++;
      }
    }

    if (have_signals != 0)
      loop->signal_io_watcher.cb(loop, &loop->signal_io_watcher, POLLIN);

    loop->watchers[loop->nwatchers] = NULL;
    loop->watchers[loop->nwatchers + 1] = NULL;

    if (have_signals != 0)
      return;

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


uint64_t uv__hrtime(uv_clocktype_t type) {
  static clock_t fast_clock_id = -1;
  struct timespec t;
  clock_t clock_id;

  if (type == UV_CLOCK_FAST && fast_clock_id == -1) {
    if (clock_getres(CLOCK_MONOTONIC_COARSE, &t) == 0 &&
        t.tv_nsec <= 1 * 1000 * 1000) {
      fast_clock_id = CLOCK_MONOTONIC_COARSE;
    } else {
      fast_clock_id = CLOCK_MONOTONIC;
    }
  }

  clock_id = CLOCK_MONOTONIC;
  if (type == UV_CLOCK_FAST)
    clock_id = fast_clock_id;

  if (clock_gettime(clock_id, &t))
    return 0;

  return t.tv_sec * (uint64_t) 1e9 + t.tv_nsec;
}


int uv_resident_set_memory(size_t* rss) {
  char buf[1024];
  const char* s;
  ssize_t n;
  long val;
  int fd;
  int i;

  do
    fd = open("/proc/self/stat", O_RDONLY);
  while (fd == -1 && errno == EINTR);

  if (fd == -1)
    return UV__ERR(errno);

  do
    n = read(fd, buf, sizeof(buf) - 1);
  while (n == -1 && errno == EINTR);

  uv__close(fd);
  if (n == -1)
    return UV__ERR(errno);
  buf[n] = '\0';

  s = strchr(buf, ' ');
  if (s == NULL)
    goto err;

  s += 1;
  if (*s != '(')
    goto err;

  s = strchr(s, ')');
  if (s == NULL)
    goto err;

  for (i = 1; i <= 22; i++) {
    s = strchr(s + 1, ' ');
    if (s == NULL)
      goto err;
  }

  errno = 0;
  val = strtol(s, NULL, 10);
  if (errno != 0)
    goto err;
  if (val < 0)
    goto err;

  *rss = val * getpagesize();
  return 0;

err:
  return UV_EINVAL;
}


int uv_uptime(double* uptime) {
  static volatile int no_clock_boottime;
  struct timespec now;
  int r;

  if (no_clock_boottime) {
    retry: r = clock_gettime(CLOCK_MONOTONIC, &now);
  }
  else if ((r = clock_gettime(CLOCK_BOOTTIME, &now)) && errno == EINVAL) {
    no_clock_boottime = 1;
    goto retry;
  }

  if (r)
    return UV__ERR(errno);

  *uptime = now.tv_sec;
  return 0;
}


static int uv__cpu_num(FILE* statfile_fp, unsigned int* numcpus) {
  unsigned int num;
  char buf[1024];

  if (!fgets(buf, sizeof(buf), statfile_fp))
    return UV_EIO;

  num = 0;
  while (fgets(buf, sizeof(buf), statfile_fp)) {
    if (strncmp(buf, "cpu", 3))
      break;
    num++;
  }

  if (num == 0)
    return UV_EIO;

  *numcpus = num;
  return 0;
}


int uv_cpu_info(uv_cpu_info_t** cpu_infos, int* count) {
  unsigned int numcpus;
  uv_cpu_info_t* ci;
  int err;
  FILE* statfile_fp;

  *cpu_infos = NULL;
  *count = 0;

  statfile_fp = uv__open_file("/proc/stat");
  if (statfile_fp == NULL)
    return UV__ERR(errno);

  err = uv__cpu_num(statfile_fp, &numcpus);
  if (err < 0)
    goto out;

  err = UV_ENOMEM;
  ci = uv__calloc(numcpus, sizeof(*ci));
  if (ci == NULL)
    goto out;

  err = read_models(numcpus, ci);
  if (err == 0)
    err = read_times(statfile_fp, numcpus, ci);

  if (err) {
    uv_free_cpu_info(ci, numcpus);
    goto out;
  }

  if (ci[0].speed == 0)
    read_speeds(numcpus, ci);

  *cpu_infos = ci;
  *count = numcpus;
  err = 0;

out:

  if (fclose(statfile_fp))
    if (errno != EINTR && errno != EINPROGRESS)
      abort();

  return err;
}


static void read_speeds(unsigned int numcpus, uv_cpu_info_t* ci) {
  unsigned int num;

  for (num = 0; num < numcpus; num++)
    ci[num].speed = read_cpufreq(num) / 1000;
}


static int read_models(unsigned int numcpus, uv_cpu_info_t* ci) {
  static const char model_marker[] = "model name\t: ";
  static const char speed_marker[] = "cpu MHz\t\t: ";
  const char* inferred_model;
  unsigned int model_idx;
  unsigned int speed_idx;
  char buf[1024];
  char* model;
  FILE* fp;

  (void) &model_marker;
  (void) &speed_marker;
  (void) &speed_idx;
  (void) &model;
  (void) &buf;
  (void) &fp;

  model_idx = 0;
  speed_idx = 0;

#if defined(__arm__) || \
    defined(__i386__) || \
    defined(__mips__) || \
    defined(__x86_64__)
  fp = uv__open_file("/proc/cpuinfo");
  if (fp == NULL)
    return UV__ERR(errno);

  while (fgets(buf, sizeof(buf), fp)) {
    if (model_idx < numcpus) {
      if (strncmp(buf, model_marker, sizeof(model_marker) - 1) == 0) {
        model = buf + sizeof(model_marker) - 1;
        model = uv__strndup(model, strlen(model) - 1);
        if (model == NULL) {
          fclose(fp);
          return UV_ENOMEM;
        }
        ci[model_idx++].model = model;
        continue;
      }
    }
#if defined(__arm__) || defined(__mips__)
    if (model_idx < numcpus) {
#if defined(__arm__)
      static const char model_marker[] = "Processor\t: ";
#else
      static const char model_marker[] = "cpu model\t\t: ";
#endif
      if (strncmp(buf, model_marker, sizeof(model_marker) - 1) == 0) {
        model = buf + sizeof(model_marker) - 1;
        model = uv__strndup(model, strlen(model) - 1);
        if (model == NULL) {
          fclose(fp);
          return UV_ENOMEM;
        }
        ci[model_idx++].model = model;
        continue;
      }
    }
#else
    if (speed_idx < numcpus) {
      if (strncmp(buf, speed_marker, sizeof(speed_marker) - 1) == 0) {
        ci[speed_idx++].speed = atoi(buf + sizeof(speed_marker) - 1);
        continue;
      }
    }
#endif
  }

  fclose(fp);
#endif

  inferred_model = "unknown";
  if (model_idx > 0)
    inferred_model = ci[model_idx - 1].model;

  while (model_idx < numcpus) {
    model = uv__strndup(inferred_model, strlen(inferred_model));
    if (model == NULL)
      return UV_ENOMEM;
    ci[model_idx++].model = model;
  }

  return 0;
}


static int read_times(FILE* statfile_fp,
                      unsigned int numcpus,
                      uv_cpu_info_t* ci) {
  unsigned long clock_ticks;
  struct uv_cpu_times_s ts;
  unsigned long user;
  unsigned long nice;
  unsigned long sys;
  unsigned long idle;
  unsigned long dummy;
  unsigned long irq;
  unsigned int num;
  unsigned int len;
  char buf[1024];

  clock_ticks = sysconf(_SC_CLK_TCK);
  assert(clock_ticks != (unsigned long) -1);
  assert(clock_ticks != 0);

  rewind(statfile_fp);

  if (!fgets(buf, sizeof(buf), statfile_fp))
    abort();

  num = 0;

  while (fgets(buf, sizeof(buf), statfile_fp)) {
    if (num >= numcpus)
      break;

    if (strncmp(buf, "cpu", 3))
      break;

    {
      unsigned int n;
      int r = sscanf(buf, "cpu%u ", &n);
      assert(r == 1);
      (void) r;
      for (len = sizeof("cpu0"); n /= 10; len++);
    }

    if (6 != sscanf(buf + len,
                    "%lu %lu %lu %lu %lu %lu",
                    &user,
                    &nice,
                    &sys,
                    &idle,
                    &dummy,
                    &irq))
      abort();

    ts.user = clock_ticks * user;
    ts.nice = clock_ticks * nice;
    ts.sys  = clock_ticks * sys;
    ts.idle = clock_ticks * idle;
    ts.irq  = clock_ticks * irq;
    ci[num++].cpu_times = ts;
  }
  assert(num == numcpus);

  return 0;
}


static unsigned long read_cpufreq(unsigned int cpunum) {
  unsigned long val;
  char buf[1024];
  FILE* fp;

  snprintf(buf,
           sizeof(buf),
           "/sys/devices/system/cpu/cpu%u/cpufreq/scaling_cur_freq",
           cpunum);

  fp = uv__open_file(buf);
  if (fp == NULL)
    return 0;

  if (fscanf(fp, "%lu", &val) != 1)
    val = 0;

  fclose(fp);

  return val;
}


void uv_free_cpu_info(uv_cpu_info_t* cpu_infos, int count) {
  int i;

  for (i = 0; i < count; i++) {
    uv__free(cpu_infos[i].model);
  }

  uv__free(cpu_infos);
}

static int uv__ifaddr_exclude(struct ifaddrs *ent, int exclude_type) {
  if (!((ent->ifa_flags & IFF_UP) && (ent->ifa_flags & IFF_RUNNING)))
    return 1;
  if (ent->ifa_addr == NULL)
    return 1;
  if (ent->ifa_addr->sa_family == PF_PACKET)
    return exclude_type;
  return !exclude_type;
}

int uv_interface_addresses(uv_interface_address_t** addresses,
  int* count) {
#ifndef HAVE_IFADDRS_H
  return UV_ENOSYS;
#else
  struct ifaddrs *addrs, *ent;
  uv_interface_address_t* address;
  int i;
  struct sockaddr_ll *sll;

  if (getifaddrs(&addrs))
    return UV__ERR(errno);

  *count = 0;
  *addresses = NULL;

  for (ent = addrs; ent != NULL; ent = ent->ifa_next) {
    if (uv__ifaddr_exclude(ent, UV__EXCLUDE_IFADDR))
      continue;

    (*count)++;
  }

  if (*count == 0)
    return 0;

  *addresses = uv__malloc(*count * sizeof(**addresses));
  if (!(*addresses)) {
    freeifaddrs(addrs);
    return UV_ENOMEM;
  }

  address = *addresses;

  for (ent = addrs; ent != NULL; ent = ent->ifa_next) {
    if (uv__ifaddr_exclude(ent, UV__EXCLUDE_IFADDR))
      continue;

    address->name = uv__strdup(ent->ifa_name);

    if (ent->ifa_addr->sa_family == AF_INET6) {
      address->address.address6 = *((struct sockaddr_in6*) ent->ifa_addr);
    } else {
      address->address.address4 = *((struct sockaddr_in*) ent->ifa_addr);
    }

    if (ent->ifa_netmask->sa_family == AF_INET6) {
      address->netmask.netmask6 = *((struct sockaddr_in6*) ent->ifa_netmask);
    } else {
      address->netmask.netmask4 = *((struct sockaddr_in*) ent->ifa_netmask);
    }

    address->is_internal = !!(ent->ifa_flags & IFF_LOOPBACK);

    address++;
  }

  for (ent = addrs; ent != NULL; ent = ent->ifa_next) {
    if (uv__ifaddr_exclude(ent, UV__EXCLUDE_IFPHYS))
      continue;

    address = *addresses;

    for (i = 0; i < (*count); i++) {
      if (strcmp(address->name, ent->ifa_name) == 0) {
        sll = (struct sockaddr_ll*)ent->ifa_addr;
        memcpy(address->phys_addr, sll->sll_addr, sizeof(address->phys_addr));
      }
      address++;
    }
  }

  freeifaddrs(addrs);

  return 0;
#endif
}


void uv_free_interface_addresses(uv_interface_address_t* addresses,
  int count) {
  int i;

  for (i = 0; i < count; i++) {
    uv__free(addresses[i].name);
  }

  uv__free(addresses);
}


void uv__set_process_title(const char* title) {
#if defined(PR_SET_NAME)
  prctl(PR_SET_NAME, title);
#endif
}
