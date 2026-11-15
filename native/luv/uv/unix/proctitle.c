
#include "uv.h"
#include "internal.h"

#include <stdlib.h>
#include <string.h>

extern void uv__set_process_title(const char* title);

static uv_mutex_t process_title_mutex;
static uv_once_t process_title_mutex_once = UV_ONCE_INIT;
static void* args_mem;

static struct {
  char* str;
  size_t len;
} process_title;


static void init_process_title_mutex_once(void) {
  uv_mutex_init(&process_title_mutex);
}


char** uv_setup_args(int argc, char** argv) {
  char** new_argv;
  size_t size;
  char* s;
  int i;

  if (argc <= 0)
    return argv;

  size = 0;
  for (i = 0; i < argc; i++)
    size += strlen(argv[i]) + 1;

#if defined(__MVS__)
  process_title.str = argv[0];
  process_title.len = strlen(argv[0]);
#else
  process_title.str = argv[0];
  process_title.len = argv[argc - 1] + strlen(argv[argc - 1]) - argv[0];
  assert(process_title.len + 1 == size);
#endif

  size += (argc + 1) * sizeof(char*);

  new_argv = uv__malloc(size);
  if (new_argv == NULL)
    return argv;
  args_mem = new_argv;

  s = (char*) &new_argv[argc + 1];
  for (i = 0; i < argc; i++) {
    size = strlen(argv[i]) + 1;
    memcpy(s, argv[i], size);
    new_argv[i] = s;
    s += size;
  }
  new_argv[i] = NULL;

  return new_argv;
}


int uv_set_process_title(const char* title) {
  uv_once(&process_title_mutex_once, init_process_title_mutex_once);
  uv_mutex_lock(&process_title_mutex);

  if (process_title.len != 0) {
    strncpy(process_title.str, title, process_title.len);
    uv__set_process_title(title);
  }

  uv_mutex_unlock(&process_title_mutex);

  return 0;
}


int uv_get_process_title(char* buffer, size_t size) {
  if (buffer == NULL || size == 0)
    return UV_EINVAL;

  uv_once(&process_title_mutex_once, init_process_title_mutex_once);
  uv_mutex_lock(&process_title_mutex);

  if (size <= process_title.len) {
    uv_mutex_unlock(&process_title_mutex);
    return UV_ENOBUFS;
  }

  if (process_title.len != 0)
    memcpy(buffer, process_title.str, process_title.len + 1);

  buffer[process_title.len] = '\0';

  uv_mutex_unlock(&process_title_mutex);

  return 0;
}


UV_DESTRUCTOR(static void free_args_mem(void)) {
  uv__free(args_mem);
  args_mem = NULL;
}
