

#include <stdlib.h>

#include "zipint.h"


ZIP_EXTERN void
zip_source_free(zip_source_t *src)
{
    if (src == NULL)
	return;

    if (src->refcount > 0) {
        src->refcount--;
    }
    if (src->refcount > 0) {
        return;
    }

    if (ZIP_SOURCE_IS_OPEN_READING(src)) {
	src->open_count = 1;
	zip_source_close(src);
    }
    if (ZIP_SOURCE_IS_OPEN_WRITING(src)) {
        zip_source_rollback_write(src);
    }

    if (src->source_archive && !src->source_closed) {
        _zip_deregister_source(src->source_archive, src);
    }

    (void)_zip_source_call(src, NULL, 0, ZIP_SOURCE_FREE);

    if (src->src) {
        zip_source_free(src->src);
    }

    free(src);
}
