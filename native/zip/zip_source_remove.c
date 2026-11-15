

#include "zipint.h"


int
zip_source_remove(zip_source_t *src)
{
    if (src->write_state == ZIP_SOURCE_WRITE_REMOVED) {
        return 0;
    }

    if (ZIP_SOURCE_IS_OPEN_READING(src)) {
	if (zip_source_close(src) < 0) {
	    return -1;
	}
    }
    if (src->write_state != ZIP_SOURCE_WRITE_CLOSED) {
        zip_source_rollback_write(src);
    }

    if (_zip_source_call(src, NULL, 0, ZIP_SOURCE_REMOVE) < 0) {
        return -1;
    }

    src->write_state = ZIP_SOURCE_WRITE_REMOVED;

    return 0;
}
