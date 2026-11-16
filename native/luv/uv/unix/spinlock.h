
#ifndef UV_SPINLOCK_H_
#define UV_SPINLOCK_H_

#include "internal.h"
#include "atomic-ops.h"

#define UV_SPINLOCK_INITIALIZER { 0 }

typedef struct {
  int lock;
} uv_spinlock_t;

UV_UNUSED(static void uv_spinlock_init(uv_spinlock_t* spinlock));
UV_UNUSED(static void uv_spinlock_lock(uv_spinlock_t* spinlock));
UV_UNUSED(static void uv_spinlock_unlock(uv_spinlock_t* spinlock));
UV_UNUSED(static int uv_spinlock_trylock(uv_spinlock_t* spinlock));

UV_UNUSED(static void uv_spinlock_init(uv_spinlock_t* spinlock)) {
  ACCESS_ONCE(int, spinlock->lock) = 0;
}

UV_UNUSED(static void uv_spinlock_lock(uv_spinlock_t* spinlock)) {
  while (!uv_spinlock_trylock(spinlock)) cpu_relax();
}

UV_UNUSED(static void uv_spinlock_unlock(uv_spinlock_t* spinlock)) {
  ACCESS_ONCE(int, spinlock->lock) = 0;
}

UV_UNUSED(static int uv_spinlock_trylock(uv_spinlock_t* spinlock)) {
  return 0 == cmpxchgi(&spinlock->lock, 0, 1);
}

#endif
