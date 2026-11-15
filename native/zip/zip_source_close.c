

#include "zipint.h"


int
zip_source_close(zip_source_t *src)
{
    if (!ZIP_SOURCE_IS_OPEN_READING(src)) {
        zip_error_set(&src->error, ZIP_ER_INVAL, 0);
        return -1;
    }

    src->open_count--;
    if (src->open_count == 0) {
	_zip_source_call(src, NULL, 0, ZIP_SOURCE_CLOSE);

	if (ZIP_SOURCE_IS_LAYERED(src)) {
	    if (zip_source_close(src->src) < 0) {
		zip_error_set(&src->error, ZIP_ER_INTERNAL, 0);
	    }
	}
    }

    return 0;
}
