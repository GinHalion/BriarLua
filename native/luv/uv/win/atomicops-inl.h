
#ifndef UV_WIN_ATOMICOPS_INL_H_
#define UV_WIN_ATOMICOPS_INL_H_

#include "uv.h"
#include "internal.h"


#ifdef _MSC_VER

#pragma intrinsic(_InterlockedOr8)

static char INLINE uv__atomic_exchange_set(char volatile* target) {
  return _InterlockedOr8(target, 1);
}

#else

static inline char uv__atomic_exchange_set(char volatile* target) {
  const char one = 1;
  char old_value;
  __asm__ __volatile__ ("lock xchgb %0, %1\n\t"
                        : "=r"(old_value), "=m"(*target)
                        : "0"(one), "m"(*target)
                        : "memory");
  return old_value;
}

#endif

#endif
