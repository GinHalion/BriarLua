

#include "zipint.h"


ZIP_EXTERN const char *
zip_file_strerror(zip_file_t *zf)
{
    return zip_error_strerror(&zf->error);
}
