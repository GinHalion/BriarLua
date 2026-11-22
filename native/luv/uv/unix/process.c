
#include "uv.h"
#include "internal.h"

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <errno.h>

#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>

#if defined(__APPLE__) && !TARGET_OS_IPHONE
# include <crt_externs.h>
# define environ (*_NSGetEnviron())
#else
extern char **environ;
#endif

#if defined(__linux__) || defined(__GLIBC__)
# include <grp.h>
#endif


static void uv__chld(uv_signal_t* handle, int signum) {
  uv_process_t* process;
  uv_loop_t* loop;
  int exit_status;
  int term_signal;
  int status;
  pid_t pid;
  QUEUE pending;
  QUEUE* q;
  QUEUE* h;

  assert(signum == SIGCHLD);

  QUEUE_INIT(&pending);
  loop = handle->loop;

  h = &loop->process_handles;
  q = QUEUE_HEAD(h);
  while (q != h) {
    process = QUEUE_DATA(q, uv_process_t, queue);
    q = QUEUE_NEXT(q);

    do
      pid = waitpid(process->pid, &status, WNOHANG);
    while (pid == -1 && errno == EINTR);

    if (pid == 0)
      continue;

    if (pid == -1) {
      if (errno != ECHILD)
        abort();
      continue;
    }

    process->status = status;
    QUEUE_REMOVE(&process->queue);
    QUEUE_INSERT_TAIL(&pending, &process->queue);
  }

  h = &pending;
  q = QUEUE_HEAD(h);
  while (q != h) {
    process = QUEUE_DATA(q, uv_process_t, queue);
    q = QUEUE_NEXT(q);

    QUEUE_REMOVE(&process->queue);
    QUEUE_INIT(&process->queue);
    uv__handle_stop(process);

    if (process->exit_cb == NULL)
      continue;

    exit_status = 0;
    if (WIFEXITED(process->status))
      exit_status = WEXITSTATUS(process->status);

    term_signal = 0;
    if (WIFSIGNALED(process->status))
      term_signal = WTERMSIG(process->status);

    process->exit_cb(process, exit_status, term_signal);
  }
  assert(QUEUE_EMPTY(&pending));
}


int uv__make_socketpair(int fds[2], int flags) {
#if defined(__linux__)
  static int no_cloexec;

  if (no_cloexec)
    goto skip;

  if (socketpair(AF_UNIX, SOCK_STREAM | UV__SOCK_CLOEXEC | flags, 0, fds) == 0)
    return 0;

  if (errno != EINVAL)
    return UV__ERR(errno);

  no_cloexec = 1;

skip:
#endif

  if (socketpair(AF_UNIX, SOCK_STREAM, 0, fds))
    return UV__ERR(errno);

  uv__cloexec(fds[0], 1);
  uv__cloexec(fds[1], 1);

  if (flags & UV__F_NONBLOCK) {
    uv__nonblock(fds[0], 1);
    uv__nonblock(fds[1], 1);
  }

  return 0;
}


int uv__make_pipe(int fds[2], int flags) {
#if defined(__linux__)
  static int no_pipe2;

  if (no_pipe2)
    goto skip;

  if (uv__pipe2(fds, flags | UV__O_CLOEXEC) == 0)
    return 0;

  if (errno != ENOSYS)
    return UV__ERR(errno);

  no_pipe2 = 1;

skip:
#endif

  if (pipe(fds))
    return UV__ERR(errno);

  uv__cloexec(fds[0], 1);
  uv__cloexec(fds[1], 1);

  if (flags & UV__F_NONBLOCK) {
    uv__nonblock(fds[0], 1);
    uv__nonblock(fds[1], 1);
  }

  return 0;
}


static int uv__process_init_stdio(uv_stdio_container_t* container, int fds[2]) {
  int mask;
  int fd;

  mask = UV_IGNORE | UV_CREATE_PIPE | UV_INHERIT_FD | UV_INHERIT_STREAM;

  switch (container->flags & mask) {
  case UV_IGNORE:
    return 0;

  case UV_CREATE_PIPE:
    assert(container->data.stream != NULL);
    if (container->data.stream->type != UV_NAMED_PIPE)
      return UV_EINVAL;
    else
      return uv__make_socketpair(fds, 0);

  case UV_INHERIT_FD:
  case UV_INHERIT_STREAM:
    if (container->flags & UV_INHERIT_FD)
      fd = container->data.fd;
    else
      fd = uv__stream_fd(container->data.stream);

    if (fd == -1)
      return UV_EINVAL;

    fds[1] = fd;
    return 0;

  default:
    assert(0 && "Unexpected flags");
    return UV_EINVAL;
  }
}


static int uv__process_open_stream(uv_stdio_container_t* container,
                                   int pipefds[2]) {
  int flags;
  int err;

  if (!(container->flags & UV_CREATE_PIPE) || pipefds[0] < 0)
    return 0;

  err = uv__close(pipefds[1]);
  if (err != 0)
    abort();

  pipefds[1] = -1;
  uv__nonblock(pipefds[0], 1);

  flags = 0;
  if (container->flags & UV_WRITABLE_PIPE)
    flags |= UV_STREAM_READABLE;
  if (container->flags & UV_READABLE_PIPE)
    flags |= UV_STREAM_WRITABLE;

  return uv__stream_open(container->data.stream, pipefds[0], flags);
}


static void uv__process_close_stream(uv_stdio_container_t* container) {
  if (!(container->flags & UV_CREATE_PIPE)) return;
  uv__stream_close((uv_stream_t*)container->data.stream);
}


static void uv__write_int(int fd, int val) {
  ssize_t n;

  do
    n = write(fd, &val, sizeof(val));
  while (n == -1 && errno == EINTR);

  if (n == -1 && errno == EPIPE)
    return;

  assert(n == sizeof(val));
}


#if !(defined(__APPLE__) && (TARGET_OS_TV || TARGET_OS_WATCH))
static void uv__process_child_init(const uv_process_options_t* options,
                                   int stdio_count,
                                   int (*pipes)[2],
                                   int error_fd) {
  sigset_t set;
  int close_fd;
  int use_fd;
  int err;
  int fd;
  int n;

  if (options->flags & UV_PROCESS_DETACHED)
    setsid();

  for (fd = 0; fd < stdio_count; fd++) {
    use_fd = pipes[fd][1];
    if (use_fd < 0 || use_fd >= fd)
      continue;
    pipes[fd][1] = fcntl(use_fd, F_DUPFD, stdio_count);
    if (pipes[fd][1] == -1) {
      uv__write_int(error_fd, UV__ERR(errno));
      _exit(127);
    }
  }

  for (fd = 0; fd < stdio_count; fd++) {
    close_fd = pipes[fd][0];
    use_fd = pipes[fd][1];

    if (use_fd < 0) {
      if (fd >= 3)
        continue;
      else {
        use_fd = open("/dev/null", fd == 0 ? O_RDONLY : O_RDWR);
        close_fd = use_fd;

        if (use_fd == -1) {
          uv__write_int(error_fd, UV__ERR(errno));
          _exit(127);
        }
      }
    }

    if (fd == use_fd)
      uv__cloexec_fcntl(use_fd, 0);
    else
      fd = dup2(use_fd, fd);

    if (fd == -1) {
      uv__write_int(error_fd, UV__ERR(errno));
      _exit(127);
    }

    if (fd <= 2)
      uv__nonblock_fcntl(fd, 0);

    if (close_fd >= stdio_count)
      uv__close(close_fd);
  }

  for (fd = 0; fd < stdio_count; fd++) {
    use_fd = pipes[fd][1];

    if (use_fd >= stdio_count)
      uv__close(use_fd);
  }

  if (options->cwd != NULL && chdir(options->cwd)) {
    uv__write_int(error_fd, UV__ERR(errno));
    _exit(127);
  }

  if (options->flags & (UV_PROCESS_SETUID | UV_PROCESS_SETGID)) {
    SAVE_ERRNO(setgroups(0, NULL));
  }

  if ((options->flags & UV_PROCESS_SETGID) && setgid(options->gid)) {
    uv__write_int(error_fd, UV__ERR(errno));
    _exit(127);
  }

  if ((options->flags & UV_PROCESS_SETUID) && setuid(options->uid)) {
    uv__write_int(error_fd, UV__ERR(errno));
    _exit(127);
  }

  if (options->env != NULL) {
    environ = options->env;
  }

  for (n = 1; n < 32; n += 1) {
    if (n == SIGKILL || n == SIGSTOP)
      continue;

    if (SIG_ERR != signal(n, SIG_DFL))
      continue;

    uv__write_int(error_fd, UV__ERR(errno));
    _exit(127);
  }

  sigemptyset(&set);
  err = pthread_sigmask(SIG_SETMASK, &set, NULL);

  if (err != 0) {
    uv__write_int(error_fd, UV__ERR(err));
    _exit(127);
  }

  execvp(options->file, options->args);
  uv__write_int(error_fd, UV__ERR(errno));
  _exit(127);
}
#endif


int uv_spawn(uv_loop_t* loop,
             uv_process_t* process,
             const uv_process_options_t* options) {
#if defined(__APPLE__) && (TARGET_OS_TV || TARGET_OS_WATCH)
  return UV_ENOSYS;
#else
  int signal_pipe[2] = { -1, -1 };
  int pipes_storage[8][2];
  int (*pipes)[2];
  int stdio_count;
  ssize_t r;
  pid_t pid;
  int err;
  int exec_errorno;
  int i;
  int status;

  assert(options->file != NULL);
  assert(!(options->flags & ~(UV_PROCESS_DETACHED |
                              UV_PROCESS_SETGID |
                              UV_PROCESS_SETUID |
                              UV_PROCESS_WINDOWS_HIDE |
                              UV_PROCESS_WINDOWS_VERBATIM_ARGUMENTS)));

  uv__handle_init(loop, (uv_handle_t*)process, UV_PROCESS);
  QUEUE_INIT(&process->queue);

  stdio_count = options->stdio_count;
  if (stdio_count < 3)
    stdio_count = 3;

  err = UV_ENOMEM;
  pipes = pipes_storage;
  if (stdio_count > (int) ARRAY_SIZE(pipes_storage))
    pipes = uv__malloc(stdio_count * sizeof(*pipes));

  if (pipes == NULL)
    goto error;

  for (i = 0; i < stdio_count; i++) {
    pipes[i][0] = -1;
    pipes[i][1] = -1;
  }

  for (i = 0; i < options->stdio_count; i++) {
    err = uv__process_init_stdio(options->stdio + i, pipes[i]);
    if (err)
      goto error;
  }

  err = uv__make_pipe(signal_pipe, 0);
  if (err)
    goto error;

  uv_signal_start(&loop->child_watcher, uv__chld, SIGCHLD);

  uv_rwlock_wrlock(&loop->cloexec_lock);
  pid = fork();

  if (pid == -1) {
    err = UV__ERR(errno);
    uv_rwlock_wrunlock(&loop->cloexec_lock);
    uv__close(signal_pipe[0]);
    uv__close(signal_pipe[1]);
    goto error;
  }

  if (pid == 0) {
    uv__process_child_init(options, stdio_count, pipes, signal_pipe[1]);
    abort();
  }

  uv_rwlock_wrunlock(&loop->cloexec_lock);
  uv__close(signal_pipe[1]);

  process->status = 0;
  exec_errorno = 0;
  do
    r = read(signal_pipe[0], &exec_errorno, sizeof(exec_errorno));
  while (r == -1 && errno == EINTR);

  if (r == 0)
    ;
  else if (r == sizeof(exec_errorno)) {
    do
      err = waitpid(pid, &status, 0);
    while (err == -1 && errno == EINTR);
    assert(err == pid);
  } else if (r == -1 && errno == EPIPE) {
    do
      err = waitpid(pid, &status, 0);
    while (err == -1 && errno == EINTR);
    assert(err == pid);
  } else
    abort();

  uv__close_nocheckstdio(signal_pipe[0]);

  for (i = 0; i < options->stdio_count; i++) {
    err = uv__process_open_stream(options->stdio + i, pipes[i]);
    if (err == 0)
      continue;

    while (i--)
      uv__process_close_stream(options->stdio + i);

    goto error;
  }

  if (exec_errorno == 0) {
    QUEUE_INSERT_TAIL(&loop->process_handles, &process->queue);
    uv__handle_start(process);
  }

  process->pid = pid;
  process->exit_cb = options->exit_cb;

  if (pipes != pipes_storage)
    uv__free(pipes);

  return exec_errorno;

error:
  if (pipes != NULL) {
    for (i = 0; i < stdio_count; i++) {
      if (i < options->stdio_count)
        if (options->stdio[i].flags & (UV_INHERIT_FD | UV_INHERIT_STREAM))
          continue;
      if (pipes[i][0] != -1)
        uv__close_nocheckstdio(pipes[i][0]);
      if (pipes[i][1] != -1)
        uv__close_nocheckstdio(pipes[i][1]);
    }

    if (pipes != pipes_storage)
      uv__free(pipes);
  }

  return err;
#endif
}


int uv_process_kill(uv_process_t* process, int signum) {
  return uv_kill(process->pid, signum);
}


int uv_kill(int pid, int signum) {
  if (kill(pid, signum))
    return UV__ERR(errno);
  else
    return 0;
}


void uv__process_close(uv_process_t* handle) {
  QUEUE_REMOVE(&handle->queue);
  uv__handle_stop(handle);
  if (QUEUE_EMPTY(&handle->loop->process_handles))
    uv_signal_stop(&handle->loop->child_watcher);
}
