
#ifndef UV_ATOMIC_OPS_H_
#define UV_ATOMIC_OPS_H_

#include "internal.h"

#if defined(__SUNPRO_C) || defined(__SUNPRO_CC)
#include <atomic.h>
#endif

UV_UNUSED(static int cmpxchgi(int* ptr, int oldval, int newval));
UV_UNUSED(static long cmpxchgl(long* ptr, long oldval, long newval));
UV_UNUSED(static void cpu_relax(void));

UV_UNUSED(static int cmpxchgi(int* ptr, int oldval, int newval)) {
#if defined(__i386__) || defined(__x86_64__)
  int out;
  __asm__ __volatile__ ("lock; cmpxchg %2, %1;"
                        : "=a" (out), "+m" (*(volatile int*) ptr)
                        : "r" (newval), "0" (oldval)
                        : "memory");
  return out;
#elif defined(_AIX) && defined(__xlC__)
  const int out = (*(volatile int*) ptr);
  __compare_and_swap(ptr, &oldval, newval);
  return out;
#elif defined(__MVS__)
  unsigned int op4;
  if (__plo_CSST(ptr, (unsigned int*) &oldval, newval,
                (unsigned int*) ptr, *ptr, &op4))
    return oldval;
  else
    return op4;
#elif defined(__SUNPRO_C) || defined(__SUNPRO_CC)
  return atomic_cas_uint(ptr, oldval, newval);
#else
  return __sync_val_compare_and_swap(ptr, oldval, newval);
#endif
}

UV_UNUSED(static long cmpxchgl(long* ptr, long oldval, long newval)) {
#if defined(__i386__) || defined(__x86_64__)
  long out;
  __asm__ __volatile__ ("lock; cmpxchg %2, %1;"
                        : "=a" (out), "+m" (*(volatile long*) ptr)
                        : "r" (newval), "0" (oldval)
                        : "memory");
  return out;
#elif defined(_AIX) && defined(__xlC__)
  const long out = (*(volatile int*) ptr);
# if defined(__64BIT__)
  __compare_and_swaplp(ptr, &oldval, newval);
# else
  __compare_and_swap(ptr, &oldval, newval);
# endif
  return out;
#elif defined (__MVS__)
#ifdef _LP64
  unsigned long long op4;
  if (__plo_CSSTGR(ptr, (unsigned long long*) &oldval, newval,
                  (unsigned long long*) ptr, *ptr, &op4))
#else
  unsigned long op4;
  if (__plo_CSST(ptr, (unsigned int*) &oldval, newval,
                (unsigned int*) ptr, *ptr, &op4))
#endif
    return oldval;
  else
    return op4;
#elif defined(__SUNPRO_C) || defined(__SUNPRO_CC)
  return atomic_cas_ulong(ptr, oldval, newval);
#else
  return __sync_val_compare_and_swap(ptr, oldval, newval);
#endif
}

UV_UNUSED(static void cpu_relax(void)) {
#if defined(__i386__) || defined(__x86_64__)
  __asm__ __volatile__ ("rep; nop");
#endif
}

#endif
