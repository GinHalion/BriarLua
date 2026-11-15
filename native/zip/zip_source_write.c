

#include "zipint.h"


ZIP_EXTERN zip_int64_t
zip_source_write(zip_source_t *src, const void *data, zip_uint64_t length)
{
    if (!ZIP_SOURCE_IS_OPEN_WRITING(src) || length > ZIP_INT64_MAX) {
        zip_error_set(&src->error, ZIP_ER_INVAL, 0);
        return -1;
    }

    return _zip_source_call(src, (void *)data, length, ZIP_SOURCE_WRITE);
}
