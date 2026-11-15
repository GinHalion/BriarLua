
#define HAVE_DLFCN_H 0

#define HAVE_FSEEKO 0

#define HAVE_FTELLO 0

#define HAVE_FTS_H 1

#define HAVE_GETOPT 1

#define HAVE_INT16_T 1

#define HAVE_INT32_T 1

#define HAVE_INT64_T 1

#define HAVE_INT8_T 1

#define HAVE_INTTYPES_H 0

#define HAVE_LIBZ 1

#define HAVE_MEMORY_H 0

#define HAVE_MKSTEMP 0


#define HAVE_OPEN 1

#define HAVE_SNPRINTF 1

#define HAVE_SSIZE_T 1

#define HAVE_STDBOOL_H 1

#define HAVE_STDINT_H 1

#define HAVE_STDLIB_H 1

#define HAVE_STRCASECMP 1

#define HAVE_STRDUP 1

#define HAVE_STRINGS_H 1

#define HAVE_STRING_H 1

#define HAVE_STRUCT_TM_TM_ZONE 1

#define HAVE_SYS_STAT_H 1

#define HAVE_SYS_TYPES_H 1

#define HAVE_TM_ZONE 1


#define HAVE_UINT16_T 1

#define HAVE_UINT32_T 1

#define HAVE_UINT64_T 1

#define HAVE_UINT8_T 1

#define HAVE_UNISTD_H 1

#define HAVE_VISIBILITY 1









#define LT_OBJDIR ".libs/"

#define PACKAGE "libzip"

#define PACKAGE_BUGREPORT "libzip@nih.at"

#define PACKAGE_NAME "libzip"

#define PACKAGE_STRING "libzip 1.0.1"

#define PACKAGE_TARNAME "libzip"

#define PACKAGE_URL ""

#define PACKAGE_VERSION "1.0.1"

#define SIZEOF_INT 4

#define SIZEOF_LONG 8

#define SIZEOF_LONG_LONG 8

#define SIZEOF_OFF_T 8

#define SIZEOF_SHORT 2

#define SIZEOF_SIZE_T 8

#define STDC_HEADERS 1


#define VERSION "1.0.1"

#ifndef _DARWIN_USE_64_BIT_INODE
# define _DARWIN_USE_64_BIT_INODE 1
#endif




#ifndef HAVE_SSIZE_T
#  if SIZEOF_SIZE_T == SIZEOF_INT
typedef int ssize_t;
#  elif SIZEOF_SIZE_T == SIZEOF_LONG
typedef long ssize_t;
#  elif SIZEOF_SIZE_T == SIZEOF_LONG_LONG
typedef long long ssize_t;
#  else
#error no suitable type for ssize_t found
#  endif
#endif

