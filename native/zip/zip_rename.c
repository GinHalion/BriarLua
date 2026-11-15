

#include <string.h>

#define _ZIP_COMPILING_DEPRECATED
#include "zipint.h"


ZIP_EXTERN int
zip_rename(zip_t *za, zip_uint64_t idx, const char *name)
{
    return zip_file_rename(za, idx, name, 0);
}
