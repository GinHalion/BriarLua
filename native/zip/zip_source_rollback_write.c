

#include "zipint.h"


ZIP_EXTERN void
zip_source_rollback_write(zip_source_t *src)
{
    if (src->write_state != ZIP_SOURCE_WRITE_OPEN && src->write_state != ZIP_SOURCE_WRITE_FAILED) {
	return;
    }

    _zip_source_call(src, NULL, 0, ZIP_SOURCE_ROLLBACK_WRITE);
    src->write_state = ZIP_SOURCE_WRITE_CLOSED;
}
