

#include "zipint.h"


zip_int64_t
zip_source_read(zip_source_t *src, void *data, zip_uint64_t len)
{
    if (src->source_closed) {
        return -1;
    }
    if (!ZIP_SOURCE_IS_OPEN_READING(src) || len > ZIP_INT64_MAX || (len > 0 && data == NULL)) {
        zip_error_set(&src->error, ZIP_ER_INVAL, 0);
	return -1;
    }

    return _zip_source_call(src, data, len, ZIP_SOURCE_READ);
}
