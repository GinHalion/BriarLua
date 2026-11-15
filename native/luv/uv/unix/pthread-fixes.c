
#include <errno.h>
#include <pthread.h>
#include <signal.h>

int uv__pthread_sigmask(int how, const sigset_t* set, sigset_t* oset) {
  static int workaround;
  int err;

  if (workaround) {
    return sigprocmask(how, set, oset);
  } else {
    err = pthread_sigmask(how, set, oset);
    if (err) {
      if (err == EINVAL && sigprocmask(how, set, oset) == 0) {
        workaround = 1;
        return 0;
      } else {
        return -1;
      }
    }
  }

  return 0;
}
