

 

#define _ZIP_COMPILING_DEPRECATED
#include "zipint.h"


ZIP_EXTERN void
zip_file_error_get(zip_file_t *zf, int *zep, int *sep)
{
    _zip_error_get(&zf->error, zep, sep);
}
