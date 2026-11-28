

#ifndef UV_OS390_SYSCALL_H_
#define UV_OS390_SYSCALL_H_

#include "uv.h"
#include "internal.h"
#include <dirent.h>
#include <poll.h>
#include <pthread.h>

#define EPOLL_CTL_ADD             1
#define EPOLL_CTL_DEL             2
#define EPOLL_CTL_MOD             3
#define MAX_EPOLL_INSTANCES       256
#define MAX_ITEMS_PER_EPOLL       1024

#define UV__O_CLOEXEC             0x80000
#define UV__EPOLL_CLOEXEC         UV__O_CLOEXEC
#define UV__EPOLL_CTL_ADD         EPOLL_CTL_ADD
#define UV__EPOLL_CTL_DEL         EPOLL_CTL_DEL
#define UV__EPOLL_CTL_MOD         EPOLL_CTL_MOD

struct epoll_event {
  int events;
  int fd;
};

typedef struct {
  QUEUE member;
  struct pollfd* items;
  unsigned long size;
  int msg_queue;
} uv__os390_epoll;

uv__os390_epoll* epoll_create1(int flags);
int epoll_ctl(uv__os390_epoll* ep, int op, int fd, struct epoll_event *event);
int epoll_wait(uv__os390_epoll* ep, struct epoll_event *events, int maxevents, int timeout);
int epoll_file_close(int fd);

int nanosleep(const struct timespec* req, struct timespec* rem);
int scandir(const char* maindir, struct dirent*** namelist,
            int (*filter)(const struct dirent *),
            int (*compar)(const struct dirent **,
            const struct dirent **));
char *mkdtemp(char* path);
ssize_t os390_readlink(const char* path, char* buf, size_t len);
size_t strnlen(const char* str, size_t maxlen);

#endif
