
#include "uv.h"
#include "internal.h"

#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include <sys/sysctl.h>
#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>

#ifndef EV_OOBAND
#define EV_OOBAND  EV_FLAG1
#endif

static void uv__fs_event(uv_loop_t* loop, uv__io_t* w, unsigned int fflags);


int uv__kqueue_init(uv_loop_t* loop) {
  loop->backend_fd = kqueue();
  if (loop->backend_fd == -1)
    return UV__ERR(errno);

  uv__cloexec(loop->backend_fd, 1);

  return 0;
}


#if defined(__APPLE__)
static int uv__has_forked_with_cfrunloop;
#endif

int uv__io_fork(uv_loop_t* loop) {
  int err;
  loop->backend_fd = -1;
  err = uv__kqueue_init(loop);
  if (err)
    return err;

#if defined(__APPLE__)
  if (loop->cf_state != NULL) {
    uv__has_forked_with_cfrunloop = 1;
    uv__free(loop->cf_state);
    loop->cf_state = NULL;
  }
#endif
  return err;
}


int uv__io_check_fd(uv_loop_t* loop, int fd) {
  struct kevent ev;
  int rc;

  rc = 0;
  EV_SET(&ev, fd, EVFILT_READ, EV_ADD, 0, 0, 0);
  if (kevent(loop->backend_fd, &ev, 1, NULL, 0, NULL))
    rc = UV__ERR(errno);

  EV_SET(&ev, fd, EVFILT_READ, EV_DELETE, 0, 0, 0);
  if (rc == 0)
    if (kevent(loop->backend_fd, &ev, 1, NULL, 0, NULL))
      abort();

  return rc;
}


void uv__io_poll(uv_loop_t* loop, int timeout) {
  struct kevent events[1024];
  struct kevent* ev;
  struct timespec spec;
  unsigned int nevents;
  unsigned int revents;
  QUEUE* q;
  uv__io_t* w;
  sigset_t* pset;
  sigset_t set;
  uint64_t base;
  uint64_t diff;
  int have_signals;
  int filter;
  int fflags;
  int count;
  int nfds;
  int fd;
  int op;
  int i;

  if (loop->nfds == 0) {
    assert(QUEUE_EMPTY(&loop->watcher_queue));
    return;
  }

  nevents = 0;

  while (!QUEUE_EMPTY(&loop->watcher_queue)) {
    q = QUEUE_HEAD(&loop->watcher_queue);
    QUEUE_REMOVE(q);
    QUEUE_INIT(q);

    w = QUEUE_DATA(q, uv__io_t, watcher_queue);
    assert(w->pevents != 0);
    assert(w->fd >= 0);
    assert(w->fd < (int) loop->nwatchers);

    if ((w->events & POLLIN) == 0 && (w->pevents & POLLIN) != 0) {
      filter = EVFILT_READ;
      fflags = 0;
      op = EV_ADD;

      if (w->cb == uv__fs_event) {
        filter = EVFILT_VNODE;
        fflags = NOTE_ATTRIB | NOTE_WRITE  | NOTE_RENAME
               | NOTE_DELETE | NOTE_EXTEND | NOTE_REVOKE;
        op = EV_ADD | EV_ONESHOT;
      }

      EV_SET(events + nevents, w->fd, filter, op, fflags, 0, 0);

      if (++nevents == ARRAY_SIZE(events)) {
        if (kevent(loop->backend_fd, events, nevents, NULL, 0, NULL))
          abort();
        nevents = 0;
      }
    }

    if ((w->events & POLLOUT) == 0 && (w->pevents & POLLOUT) != 0) {
      EV_SET(events + nevents, w->fd, EVFILT_WRITE, EV_ADD, 0, 0, 0);

      if (++nevents == ARRAY_SIZE(events)) {
        if (kevent(loop->backend_fd, events, nevents, NULL, 0, NULL))
          abort();
        nevents = 0;
      }
    }

   if ((w->events & UV__POLLPRI) == 0 && (w->pevents & UV__POLLPRI) != 0) {
      EV_SET(events + nevents, w->fd, EV_OOBAND, EV_ADD, 0, 0, 0);

      if (++nevents == ARRAY_SIZE(events)) {
        if (kevent(loop->backend_fd, events, nevents, NULL, 0, NULL))
          abort();
        nevents = 0;
      }
    }

    w->events = w->pevents;
  }

  pset = NULL;
  if (loop->flags & UV_LOOP_BLOCK_SIGPROF) {
    pset = &set;
    sigemptyset(pset);
    sigaddset(pset, SIGPROF);
  }

  assert(timeout >= -1);
  base = loop->time;
  count = 48;

  for (;; nevents = 0) {
    if (timeout != -1) {
      spec.tv_sec = timeout / 1000;
      spec.tv_nsec = (timeout % 1000) * 1000000;
    }

    if (pset != NULL)
      pthread_sigmask(SIG_BLOCK, pset, NULL);

    nfds = kevent(loop->backend_fd,
                  events,
                  nevents,
                  events,
                  ARRAY_SIZE(events),
                  timeout == -1 ? NULL : &spec);

    if (pset != NULL)
      pthread_sigmask(SIG_UNBLOCK, pset, NULL);

    SAVE_ERRNO(uv__update_time(loop));

    if (nfds == 0) {
      assert(timeout != -1);
      return;
    }

    if (nfds == -1) {
      if (errno != EINTR)
        abort();

      if (timeout == 0)
        return;

      if (timeout == -1)
        continue;

      goto update_timeout;
    }

    have_signals = 0;
    nevents = 0;

    assert(loop->watchers != NULL);
    loop->watchers[loop->nwatchers] = (void*) events;
    loop->watchers[loop->nwatchers + 1] = (void*) (uintptr_t) nfds;
    for (i = 0; i < nfds; i++) {
      ev = events + i;
      fd = ev->ident;
      if (fd == -1)
        continue;
      w = loop->watchers[fd];

      if (w == NULL) {
        struct kevent events[1];

        EV_SET(events + 0, fd, ev->filter, EV_DELETE, 0, 0, 0);
        if (kevent(loop->backend_fd, events, 1, NULL, 0, NULL))
          if (errno != EBADF && errno != ENOENT)
            abort();

        continue;
      }

      if (ev->filter == EVFILT_VNODE) {
        assert(w->events == POLLIN);
        assert(w->pevents == POLLIN);
        w->cb(loop, w, ev->fflags);
        nevents++;
        continue;
      }

      revents = 0;

      if (ev->filter == EVFILT_READ) {
        if (w->pevents & POLLIN) {
          revents |= POLLIN;
          w->rcount = ev->data;
        } else {
          struct kevent events[1];
          EV_SET(events + 0, fd, ev->filter, EV_DELETE, 0, 0, 0);
          if (kevent(loop->backend_fd, events, 1, NULL, 0, NULL))
            if (errno != ENOENT)
              abort();
        }
      }

      if (ev->filter == EV_OOBAND) {
        if (w->pevents & UV__POLLPRI) {
          revents |= UV__POLLPRI;
          w->rcount = ev->data;
        } else {
          struct kevent events[1];
          EV_SET(events + 0, fd, ev->filter, EV_DELETE, 0, 0, 0);
          if (kevent(loop->backend_fd, events, 1, NULL, 0, NULL))
            if (errno != ENOENT)
              abort();
        }
      }

      if (ev->filter == EVFILT_WRITE) {
        if (w->pevents & POLLOUT) {
          revents |= POLLOUT;
          w->wcount = ev->data;
        } else {
          struct kevent events[1];
          EV_SET(events + 0, fd, ev->filter, EV_DELETE, 0, 0, 0);
          if (kevent(loop->backend_fd, events, 1, NULL, 0, NULL))
            if (errno != ENOENT)
              abort();
        }
      }

      if (ev->flags & EV_ERROR)
        revents |= POLLERR;

      if ((ev->flags & EV_EOF) && (w->pevents & UV__POLLRDHUP))
        revents |= UV__POLLRDHUP;

      if (revents == 0)
        continue;

      if (w == &loop->signal_io_watcher)
        have_signals = 1;
      else
        w->cb(loop, w, revents);

      nevents++;
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

    diff = loop->time - base;
    if (diff >= (uint64_t) timeout)
      return;

    timeout -= diff;
  }
}


void uv__platform_invalidate_fd(uv_loop_t* loop, int fd) {
  struct kevent* events;
  uintptr_t i;
  uintptr_t nfds;

  assert(loop->watchers != NULL);

  events = (struct kevent*) loop->watchers[loop->nwatchers];
  nfds = (uintptr_t) loop->watchers[loop->nwatchers + 1];
  if (events == NULL)
    return;

  for (i = 0; i < nfds; i++)
    if ((int) events[i].ident == fd)
      events[i].ident = -1;
}


static void uv__fs_event(uv_loop_t* loop, uv__io_t* w, unsigned int fflags) {
  uv_fs_event_t* handle;
  struct kevent ev;
  int events;
  const char* path;
#if defined(F_GETPATH)
  char pathbuf[MAXPATHLEN];
#endif

  handle = container_of(w, uv_fs_event_t, event_watcher);

  if (fflags & (NOTE_ATTRIB | NOTE_EXTEND))
    events = UV_CHANGE;
  else
    events = UV_RENAME;

  path = NULL;
#if defined(F_GETPATH)
  if (fcntl(handle->event_watcher.fd, F_GETPATH, pathbuf) == 0)
    path = uv__basename_r(pathbuf);
#endif
  handle->cb(handle, path, events, 0);

  if (handle->event_watcher.fd == -1)
    return;

  fflags = NOTE_ATTRIB | NOTE_WRITE  | NOTE_RENAME
         | NOTE_DELETE | NOTE_EXTEND | NOTE_REVOKE;

  EV_SET(&ev, w->fd, EVFILT_VNODE, EV_ADD | EV_ONESHOT, fflags, 0, 0);

  if (kevent(loop->backend_fd, &ev, 1, NULL, 0, NULL))
    abort();
}


int uv_fs_event_init(uv_loop_t* loop, uv_fs_event_t* handle) {
  uv__handle_init(loop, (uv_handle_t*)handle, UV_FS_EVENT);
  return 0;
}


int uv_fs_event_start(uv_fs_event_t* handle,
                      uv_fs_event_cb cb,
                      const char* path,
                      unsigned int flags) {
#if defined(__APPLE__)
  struct stat statbuf;
#endif
  int fd;

  if (uv__is_active(handle))
    return UV_EINVAL;

  fd = open(path, O_RDONLY);
  if (fd == -1)
    return UV__ERR(errno);

  uv__handle_start(handle);
  uv__io_init(&handle->event_watcher, uv__fs_event, fd);
  handle->path = uv__strdup(path);
  handle->cb = cb;

#if defined(__APPLE__)
  if (uv__has_forked_with_cfrunloop)
    goto fallback;

  handle->cf_cb = NULL;
  handle->realpath = NULL;
  handle->realpath_len = 0;
  handle->cf_flags = flags;

  if (fstat(fd, &statbuf))
    goto fallback;
  if (!(statbuf.st_mode & S_IFDIR))
    goto fallback;

  uv__close(fd);
  handle->event_watcher.fd = -1;

  return uv__fsevents_init(handle);

fallback:
#endif

  uv__io_start(handle->loop, &handle->event_watcher, POLLIN);

  return 0;
}


int uv_fs_event_stop(uv_fs_event_t* handle) {
  if (!uv__is_active(handle))
    return 0;

  uv__handle_stop(handle);

#if defined(__APPLE__)
  if (uv__has_forked_with_cfrunloop || uv__fsevents_close(handle))
#endif
  {
    uv__io_close(handle->loop, &handle->event_watcher);
  }

  uv__free(handle->path);
  handle->path = NULL;

  if (handle->event_watcher.fd != -1) {
    uv__close(handle->event_watcher.fd);
    handle->event_watcher.fd = -1;
  }

  return 0;
}


void uv__fs_event_close(uv_fs_event_t* handle) {
  uv_fs_event_stop(handle);
}
