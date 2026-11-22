

 


#include "zipint.h"


int
zip_source_stat(zip_source_t *src, zip_stat_t *st)
{
    if (src->source_closed) {
        return -1;
    }
    if (st == NULL) {
        zip_error_set(&src->error, ZIP_ER_INVAL, 0);
	return -1;
    }

    zip_stat_init(st);
    
    if (ZIP_SOURCE_IS_LAYERED(src)) {
        if (zip_source_stat(src->src, st) < 0) {
            _zip_error_set_from_source(&src->error, src->src);
            return -1;
        }
    }

    if (_zip_source_call(src, st, sizeof(*st), ZIP_SOURCE_STAT) < 0) {
	return -1;
    }

    return 0;
}
