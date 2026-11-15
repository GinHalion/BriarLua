

#define _ZIP_COMPILING_DEPRECATED
#include "zipint.h"


ZIP_EXTERN void
zip_error_get(zip_t *za, int *zep, int *sep)
{
    _zip_error_get(&za->error, zep, sep);
}


ZIP_EXTERN zip_error_t *
zip_get_error(zip_t *za)
{
    return &za->error;
}


ZIP_EXTERN zip_error_t *
zip_file_get_error(zip_file_t *f)
{
    return &f->error;
}
