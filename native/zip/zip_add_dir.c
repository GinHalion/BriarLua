

#define _ZIP_COMPILING_DEPRECATED
#include "zipint.h"



ZIP_EXTERN zip_int64_t
zip_add_dir(zip_t *za, const char *name)
{
    return zip_dir_add(za, name, 0);
}
