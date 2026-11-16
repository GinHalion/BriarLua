
#include <assert.h>
#include <limits.h>
#include <stdlib.h>

#include "uv.h"
#include "internal.h"

static void uv__once_inner(uv_once_t* guard, void (*callback)(void)) {
  DWORD result;
  HANDLE existing_event, created_event;

  created_event = CreateEvent(NULL, 1, 0, NULL);
  if (created_event == 0) {
    uv_fatal_error(GetLastError(), "CreateEvent");
  }

  existing_event = InterlockedCompareExchangePointer(&guard->event,
                                                     created_event,
                                                     NULL);

  if (existing_event == NULL) {
    callback();

    result = SetEvent(created_event);
    assert(result);
    guard->ran = 1;

  } else {
    CloseHandle(created_event);
    result = WaitForSingleObject(existing_event, INFINITE);
    assert(result == WAIT_OBJECT_0);
  }
}


void uv_once(uv_once_t* guard, void (*callback)(void)) {
  if (guard->ran) {
    return;
  }

  uv__once_inner(guard, callback);
}


STATIC_ASSERT(sizeof(uv_thread_t) <= sizeof(void*));

static uv_key_t uv__current_thread_key;
static uv_once_t uv__current_thread_init_guard = UV_ONCE_INIT;


static void uv__init_current_thread_key(void) {
  if (uv_key_create(&uv__current_thread_key))
    abort();
}


struct thread_ctx {
  void (*entry)(void* arg);
  void* arg;
  uv_thread_t self;
};


static UINT __stdcall uv__thread_start(void* arg) {
  struct thread_ctx *ctx_p;
  struct thread_ctx ctx;

  ctx_p = arg;
  ctx = *ctx_p;
  uv__free(ctx_p);

  uv_once(&uv__current_thread_init_guard, uv__init_current_thread_key);
  uv_key_set(&uv__current_thread_key, (void*) ctx.self);

  ctx.entry(ctx.arg);

  return 0;
}


int uv_thread_create(uv_thread_t *tid, void (*entry)(void *arg), void *arg) {
  struct thread_ctx* ctx;
  int err;
  HANDLE thread;

  ctx = uv__malloc(sizeof(*ctx));
  if (ctx == NULL)
    return UV_ENOMEM;

  ctx->entry = entry;
  ctx->arg = arg;

  thread = (HANDLE) _beginthreadex(NULL,
                                   0,
                                   uv__thread_start,
                                   ctx,
                                   CREATE_SUSPENDED,
                                   NULL);
  if (thread == NULL) {
    err = errno;
    uv__free(ctx);
  } else {
    err = 0;
    *tid = thread;
    ctx->self = thread;
    ResumeThread(thread);
  }

  switch (err) {
    case 0:
      return 0;
    case EACCES:
      return UV_EACCES;
    case EAGAIN:
      return UV_EAGAIN;
    case EINVAL:
      return UV_EINVAL;
  }

  return UV_EIO;
}


uv_thread_t uv_thread_self(void) {
  uv_once(&uv__current_thread_init_guard, uv__init_current_thread_key);
  return (uv_thread_t) uv_key_get(&uv__current_thread_key);
}


int uv_thread_join(uv_thread_t *tid) {
  if (WaitForSingleObject(*tid, INFINITE))
    return uv_translate_sys_error(GetLastError());
  else {
    CloseHandle(*tid);
    *tid = 0;
    MemoryBarrier();
    return 0;
  }
}


int uv_thread_equal(const uv_thread_t* t1, const uv_thread_t* t2) {
  return *t1 == *t2;
}


int uv_mutex_init(uv_mutex_t* mutex) {
  InitializeCriticalSection(mutex);
  return 0;
}


int uv_mutex_init_recursive(uv_mutex_t* mutex) {
  return uv_mutex_init(mutex);
}


void uv_mutex_destroy(uv_mutex_t* mutex) {
  DeleteCriticalSection(mutex);
}


void uv_mutex_lock(uv_mutex_t* mutex) {
  EnterCriticalSection(mutex);
}


int uv_mutex_trylock(uv_mutex_t* mutex) {
  if (TryEnterCriticalSection(mutex))
    return 0;
  else
    return UV_EBUSY;
}


void uv_mutex_unlock(uv_mutex_t* mutex) {
  LeaveCriticalSection(mutex);
}


int uv_rwlock_init(uv_rwlock_t* rwlock) {
  HANDLE handle = CreateSemaphoreW(NULL, 1, 1, NULL);
  if (handle == NULL)
    return uv_translate_sys_error(GetLastError());
  rwlock->state_.write_semaphore_ = handle;

  InitializeCriticalSection(&rwlock->state_.num_readers_lock_);

  rwlock->state_.num_readers_ = 0;

  return 0;
}


void uv_rwlock_destroy(uv_rwlock_t* rwlock) {
  DeleteCriticalSection(&rwlock->state_.num_readers_lock_);
  CloseHandle(rwlock->state_.write_semaphore_);
}


void uv_rwlock_rdlock(uv_rwlock_t* rwlock) {
  EnterCriticalSection(&rwlock->state_.num_readers_lock_);

  if (++rwlock->state_.num_readers_ == 1) {
    DWORD r = WaitForSingleObject(rwlock->state_.write_semaphore_, INFINITE);
    if (r != WAIT_OBJECT_0)
      uv_fatal_error(GetLastError(), "WaitForSingleObject");
  }

  LeaveCriticalSection(&rwlock->state_.num_readers_lock_);
}


int uv_rwlock_tryrdlock(uv_rwlock_t* rwlock) {
  int err;

  if (!TryEnterCriticalSection(&rwlock->state_.num_readers_lock_))
    return UV_EBUSY;

  err = 0;

  if (rwlock->state_.num_readers_ == 0) {
    DWORD r = WaitForSingleObject(rwlock->state_.write_semaphore_, 0);
    if (r == WAIT_OBJECT_0)
      rwlock->state_.num_readers_++;
    else if (r == WAIT_TIMEOUT)
      err = UV_EBUSY;
    else if (r == WAIT_FAILED)
      uv_fatal_error(GetLastError(), "WaitForSingleObject");

  } else {
    rwlock->state_.num_readers_++;
  }

  LeaveCriticalSection(&rwlock->state_.num_readers_lock_);
  return err;
}


void uv_rwlock_rdunlock(uv_rwlock_t* rwlock) {
  EnterCriticalSection(&rwlock->state_.num_readers_lock_);

  if (--rwlock->state_.num_readers_ == 0) {
    if (!ReleaseSemaphore(rwlock->state_.write_semaphore_, 1, NULL))
      uv_fatal_error(GetLastError(), "ReleaseSemaphore");
  }

  LeaveCriticalSection(&rwlock->state_.num_readers_lock_);
}


void uv_rwlock_wrlock(uv_rwlock_t* rwlock) {
  DWORD r = WaitForSingleObject(rwlock->state_.write_semaphore_, INFINITE);
  if (r != WAIT_OBJECT_0)
    uv_fatal_error(GetLastError(), "WaitForSingleObject");
}


int uv_rwlock_trywrlock(uv_rwlock_t* rwlock) {
  DWORD r = WaitForSingleObject(rwlock->state_.write_semaphore_, 0);
  if (r == WAIT_OBJECT_0)
    return 0;
  else if (r == WAIT_TIMEOUT)
    return UV_EBUSY;
  else
    uv_fatal_error(GetLastError(), "WaitForSingleObject");
}


void uv_rwlock_wrunlock(uv_rwlock_t* rwlock) {
  if (!ReleaseSemaphore(rwlock->state_.write_semaphore_, 1, NULL))
    uv_fatal_error(GetLastError(), "ReleaseSemaphore");
}


int uv_sem_init(uv_sem_t* sem, unsigned int value) {
  *sem = CreateSemaphore(NULL, value, INT_MAX, NULL);
  if (*sem == NULL)
    return uv_translate_sys_error(GetLastError());
  else
    return 0;
}


void uv_sem_destroy(uv_sem_t* sem) {
  if (!CloseHandle(*sem))
    abort();
}


void uv_sem_post(uv_sem_t* sem) {
  if (!ReleaseSemaphore(*sem, 1, NULL))
    abort();
}


void uv_sem_wait(uv_sem_t* sem) {
  if (WaitForSingleObject(*sem, INFINITE) != WAIT_OBJECT_0)
    abort();
}


int uv_sem_trywait(uv_sem_t* sem) {
  DWORD r = WaitForSingleObject(*sem, 0);

  if (r == WAIT_OBJECT_0)
    return 0;

  if (r == WAIT_TIMEOUT)
    return UV_EAGAIN;

  abort();
  return -1;
}


int uv_cond_init(uv_cond_t* cond) {
  InitializeConditionVariable(&cond->cond_var);
  return 0;
}


void uv_cond_destroy(uv_cond_t* cond) {
  (void) &cond;
}


void uv_cond_signal(uv_cond_t* cond) {
  WakeConditionVariable(&cond->cond_var);
}


void uv_cond_broadcast(uv_cond_t* cond) {
  WakeAllConditionVariable(&cond->cond_var);
}


void uv_cond_wait(uv_cond_t* cond, uv_mutex_t* mutex) {
  if (!SleepConditionVariableCS(&cond->cond_var, mutex, INFINITE))
    abort();
}

int uv_cond_timedwait(uv_cond_t* cond, uv_mutex_t* mutex, uint64_t timeout) {
  if (SleepConditionVariableCS(&cond->cond_var, mutex, (DWORD)(timeout / 1e6)))
    return 0;
  if (GetLastError() != ERROR_TIMEOUT)
    abort();
  return UV_ETIMEDOUT;
}


int uv_barrier_init(uv_barrier_t* barrier, unsigned int count) {
  int err;

  barrier->n = count;
  barrier->count = 0;

  err = uv_mutex_init(&barrier->mutex);
  if (err)
    return err;

  err = uv_sem_init(&barrier->turnstile1, 0);
  if (err)
    goto error2;

  err = uv_sem_init(&barrier->turnstile2, 1);
  if (err)
    goto error;

  return 0;

error:
  uv_sem_destroy(&barrier->turnstile1);
error2:
  uv_mutex_destroy(&barrier->mutex);
  return err;

}


void uv_barrier_destroy(uv_barrier_t* barrier) {
  uv_sem_destroy(&barrier->turnstile2);
  uv_sem_destroy(&barrier->turnstile1);
  uv_mutex_destroy(&barrier->mutex);
}


int uv_barrier_wait(uv_barrier_t* barrier) {
  int serial_thread;

  uv_mutex_lock(&barrier->mutex);
  if (++barrier->count == barrier->n) {
    uv_sem_wait(&barrier->turnstile2);
    uv_sem_post(&barrier->turnstile1);
  }
  uv_mutex_unlock(&barrier->mutex);

  uv_sem_wait(&barrier->turnstile1);
  uv_sem_post(&barrier->turnstile1);

  uv_mutex_lock(&barrier->mutex);
  serial_thread = (--barrier->count == 0);
  if (serial_thread) {
    uv_sem_wait(&barrier->turnstile1);
    uv_sem_post(&barrier->turnstile2);
  }
  uv_mutex_unlock(&barrier->mutex);

  uv_sem_wait(&barrier->turnstile2);
  uv_sem_post(&barrier->turnstile2);
  return serial_thread;
}


int uv_key_create(uv_key_t* key) {
  key->tls_index = TlsAlloc();
  if (key->tls_index == TLS_OUT_OF_INDEXES)
    return UV_ENOMEM;
  return 0;
}


void uv_key_delete(uv_key_t* key) {
  if (TlsFree(key->tls_index) == FALSE)
    abort();
  key->tls_index = TLS_OUT_OF_INDEXES;
}


void* uv_key_get(uv_key_t* key) {
  void* value;

  value = TlsGetValue(key->tls_index);
  if (value == NULL)
    if (GetLastError() != ERROR_SUCCESS)
      abort();

  return value;
}


void uv_key_set(uv_key_t* key, void* value) {
  if (TlsSetValue(key->tls_index, value) == FALSE)
    abort();
}
