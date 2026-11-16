
#include "uv.h"
#include "internal.h"
#include "spinlock.h"

#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <termios.h>
#include <errno.h>
#include <sys/ioctl.h>

#if defined(__MVS__) && !defined(IMAXBEL)
#define IMAXBEL 0
#endif

static int orig_termios_fd = -1;
static struct termios orig_termios;
static uv_spinlock_t termios_spinlock = UV_SPINLOCK_INITIALIZER;

static int uv__tty_is_slave(const int fd) {
  int result;
#if defined(__linux__) || defined(__FreeBSD__) || defined(__FreeBSD_kernel__)
  int dummy;

  result = ioctl(fd, TIOCGPTN, &dummy) != 0;
#elif defined(__APPLE__)
  char dummy[256];

  result = ioctl(fd, TIOCPTYGNAME, &dummy) != 0;
#elif defined(__NetBSD__)

  struct stat sb;
  static devmajor_t pts = NODEVMAJOR;

  if (pts == NODEVMAJOR) {
    pts = getdevmajor("pts", S_IFCHR);
    if (pts == NODEVMAJOR)
      abort();
  }

  if (fstat(fd, &sb) != 0)
    abort();

  if (!S_ISCHR(sb.st_mode))
    abort();

  if (major(sb.st_rdev) == NODEVMAJOR)
    abort();

  result = (pts == major(sb.st_rdev));
#else
  result = ptsname(fd) == NULL;
#endif
  return result;
}

int uv_tty_init(uv_loop_t* loop, uv_tty_t* tty, int fd, int readable) {
  uv_handle_type type;
  int flags;
  int newfd;
  int r;
  int saved_flags;
  char path[256];

  type = uv_guess_handle(fd);
  if (type == UV_FILE || type == UV_UNKNOWN_HANDLE)
    return UV_EINVAL;

  flags = 0;
  newfd = -1;

  if (type == UV_TTY) {
    if (uv__tty_is_slave(fd) && ttyname_r(fd, path, sizeof(path)) == 0)
      r = uv__open_cloexec(path, O_RDWR);
    else
      r = -1;

    if (r < 0) {
      if (!readable)
        flags |= UV_STREAM_BLOCKING;
      goto skip;
    }

    newfd = r;

    r = uv__dup2_cloexec(newfd, fd);
    if (r < 0 && r != UV_EINVAL) {
      uv__close(newfd);
      return r;
    }

    fd = newfd;
  }

#if defined(__APPLE__)
  do
    saved_flags = fcntl(fd, F_GETFL);
  while (saved_flags == -1 && errno == EINTR);

  if (saved_flags == -1) {
    if (newfd != -1)
      uv__close(newfd);
    return UV__ERR(errno);
  }
#endif

  (void) &saved_flags;

skip:
  uv__stream_init(loop, (uv_stream_t*) tty, UV_TTY);


  if (!(flags & UV_STREAM_BLOCKING))
    uv__nonblock(fd, 1);

#if defined(__APPLE__)
  r = uv__stream_try_select((uv_stream_t*) tty, &fd);
  if (r) {
    int rc = r;
    if (newfd != -1)
      uv__close(newfd);
    QUEUE_REMOVE(&tty->handle_queue);
    do
      r = fcntl(fd, F_SETFL, saved_flags);
    while (r == -1 && errno == EINTR);
    return rc;
  }
#endif

  if (readable)
    flags |= UV_STREAM_READABLE;
  else
    flags |= UV_STREAM_WRITABLE;

  uv__stream_open((uv_stream_t*) tty, fd, flags);
  tty->mode = UV_TTY_MODE_NORMAL;

  return 0;
}

static void uv__tty_make_raw(struct termios* tio) {
  assert(tio != NULL);

#if defined __sun || defined __MVS__
  tio->c_iflag &= ~(IMAXBEL | IGNBRK | BRKINT | PARMRK | ISTRIP | INLCR |
                    IGNCR | ICRNL | IXON);
  tio->c_oflag &= ~OPOST;
  tio->c_lflag &= ~(ECHO | ECHONL | ICANON | ISIG | IEXTEN);
  tio->c_cflag &= ~(CSIZE | PARENB);
  tio->c_cflag |= CS8;
#else
  cfmakeraw(tio);
#endif
}

int uv_tty_set_mode(uv_tty_t* tty, uv_tty_mode_t mode) {
  struct termios tmp;
  int fd;

  if (tty->mode == (int) mode)
    return 0;

  fd = uv__stream_fd(tty);
  if (tty->mode == UV_TTY_MODE_NORMAL && mode != UV_TTY_MODE_NORMAL) {
    if (tcgetattr(fd, &tty->orig_termios))
      return UV__ERR(errno);

    uv_spinlock_lock(&termios_spinlock);
    if (orig_termios_fd == -1) {
      orig_termios = tty->orig_termios;
      orig_termios_fd = fd;
    }
    uv_spinlock_unlock(&termios_spinlock);
  }

  tmp = tty->orig_termios;
  switch (mode) {
    case UV_TTY_MODE_NORMAL:
      break;
    case UV_TTY_MODE_RAW:
      tmp.c_iflag &= ~(BRKINT | ICRNL | INPCK | ISTRIP | IXON);
      tmp.c_oflag |= (ONLCR);
      tmp.c_cflag |= (CS8);
      tmp.c_lflag &= ~(ECHO | ICANON | IEXTEN | ISIG);
      tmp.c_cc[VMIN] = 1;
      tmp.c_cc[VTIME] = 0;
      break;
    case UV_TTY_MODE_IO:
      uv__tty_make_raw(&tmp);
      break;
  }

  if (tcsetattr(fd, TCSADRAIN, &tmp))
    return UV__ERR(errno);

  tty->mode = mode;
  return 0;
}


int uv_tty_get_winsize(uv_tty_t* tty, int* width, int* height) {
  struct winsize ws;
  int err;

  do
    err = ioctl(uv__stream_fd(tty), TIOCGWINSZ, &ws);
  while (err == -1 && errno == EINTR);

  if (err == -1)
    return UV__ERR(errno);

  *width = ws.ws_col;
  *height = ws.ws_row;

  return 0;
}


uv_handle_type uv_guess_handle(uv_file file) {
  struct sockaddr sa;
  struct stat s;
  socklen_t len;
  int type;

  if (file < 0)
    return UV_UNKNOWN_HANDLE;

  if (isatty(file))
    return UV_TTY;

  if (fstat(file, &s))
    return UV_UNKNOWN_HANDLE;

  if (S_ISREG(s.st_mode))
    return UV_FILE;

  if (S_ISCHR(s.st_mode))
    return UV_FILE;

  if (S_ISFIFO(s.st_mode))
    return UV_NAMED_PIPE;

  if (!S_ISSOCK(s.st_mode))
    return UV_UNKNOWN_HANDLE;

  len = sizeof(type);
  if (getsockopt(file, SOL_SOCKET, SO_TYPE, &type, &len))
    return UV_UNKNOWN_HANDLE;

  len = sizeof(sa);
  if (getsockname(file, &sa, &len))
    return UV_UNKNOWN_HANDLE;

  if (type == SOCK_DGRAM)
    if (sa.sa_family == AF_INET || sa.sa_family == AF_INET6)
      return UV_UDP;

  if (type == SOCK_STREAM) {
#if defined(_AIX) || defined(__DragonFly__)
    if (len == 0)
      return UV_NAMED_PIPE;
#endif

    if (sa.sa_family == AF_INET || sa.sa_family == AF_INET6)
      return UV_TCP;
    if (sa.sa_family == AF_UNIX)
      return UV_NAMED_PIPE;
  }

  return UV_UNKNOWN_HANDLE;
}


int uv_tty_reset_mode(void) {
  int saved_errno;
  int err;

  saved_errno = errno;
  if (!uv_spinlock_trylock(&termios_spinlock))
    return UV_EBUSY;

  err = 0;
  if (orig_termios_fd != -1)
    if (tcsetattr(orig_termios_fd, TCSANOW, &orig_termios))
      err = UV__ERR(errno);

  uv_spinlock_unlock(&termios_spinlock);
  errno = saved_errno;

  return err;
}
