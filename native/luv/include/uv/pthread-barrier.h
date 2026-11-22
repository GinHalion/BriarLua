


#ifndef _UV_PTHREAD_BARRIER_
#define _UV_PTHREAD_BARRIER_
#include <errno.h>
#include <pthread.h>
#if !defined(__MVS__)
#include <semaphore.h>
#endif

#define PTHREAD_BARRIER_SERIAL_THREAD  0x12345
#define UV__PTHREAD_BARRIER_FALLBACK   1

#if defined(__ANDROID__)
# define UV_BARRIER_STRUCT_PADDING \
  sizeof(pthread_mutex_t) + \
  sizeof(pthread_cond_t) + \
  sizeof(unsigned int) - \
  sizeof(void *)
#elif defined(__APPLE__)
# define UV_BARRIER_STRUCT_PADDING \
  sizeof(pthread_mutex_t) + \
  2 * sizeof(sem_t) + \
  2 * sizeof(unsigned int) - \
  sizeof(void *)
#else
# define UV_BARRIER_STRUCT_PADDING 0
#endif

typedef struct {
  pthread_mutex_t  mutex;
  pthread_cond_t   cond;
  unsigned         threshold;
  unsigned         in;
  unsigned         out;
} _uv_barrier;

typedef struct {
  _uv_barrier* b;
  char _pad[UV_BARRIER_STRUCT_PADDING];
} pthread_barrier_t;

int pthread_barrier_init(pthread_barrier_t* barrier,
                         const void* barrier_attr,
                         unsigned count);

int pthread_barrier_wait(pthread_barrier_t* barrier);
int pthread_barrier_destroy(pthread_barrier_t *barrier);

#endif
