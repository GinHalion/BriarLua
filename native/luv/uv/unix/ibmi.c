
#include "uv.h"
#include "internal.h"

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <errno.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <sys/time.h>
#include <unistd.h>
#include <fcntl.h>
#include <utmp.h>
#include <libgen.h>

#include <sys/protosw.h>
#include <procinfo.h>
#include <sys/proc.h>
#include <sys/procfs.h>

#include <ctype.h>

#include <sys/mntctl.h>
#include <sys/vmount.h>
#include <limits.h>
#include <strings.h>
#include <sys/vnode.h>

uint64_t uv_get_free_memory(void) {
  return (uint64_t) sysconf(_SC_PAGESIZE) * sysconf(_SC_AVPHYS_PAGES);
}


uint64_t uv_get_total_memory(void) {
  return (uint64_t) sysconf(_SC_PAGESIZE) * sysconf(_SC_PHYS_PAGES);
}


void uv_loadavg(double avg[3]) {
    avg[0] = avg[1] = avg[2] = 0;
    return;
}


int uv_resident_set_memory(size_t* rss) {
  return UV_ENOSYS;
}


int uv_uptime(double* uptime) {
  return UV_ENOSYS;
}


int uv_cpu_info(uv_cpu_info_t** cpu_infos, int* count) {
  unsigned int numcpus, idx = 0;
  uv_cpu_info_t* cpu_info;

  *cpu_infos = NULL;
  *count = 0;

  numcpus = sysconf(_SC_NPROCESSORS_ONLN);

  *cpu_infos = uv__malloc(numcpus * sizeof(uv_cpu_info_t));
  if (!*cpu_infos) {
    return UV_ENOMEM;
  }

  cpu_info = *cpu_infos;
  for (idx = 0; idx < numcpus; idx++) {
    cpu_info->speed = 0;
    cpu_info->model = uv__strdup("unknown");
    cpu_info->cpu_times.user = 0;
    cpu_info->cpu_times.sys = 0;
    cpu_info->cpu_times.idle = 0;
    cpu_info->cpu_times.irq = 0;
    cpu_info->cpu_times.nice = 0;
    cpu_info++;
  }
  *count = numcpus;

  return 0;
}