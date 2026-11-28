

 

#include <string.h>

#include "zipint.h"


ZIP_EXTERN void
zip_stat_init(zip_stat_t *st)
{
    st->valid = 0;
    st->name = NULL;
    st->index = ZIP_UINT64_MAX;
    st->crc = 0;
    st->mtime = (time_t)-1;
    st->size = 0;
    st->comp_size = 0;
    st->comp_method = ZIP_CM_STORE;
    st->encryption_method = ZIP_EM_NONE;
}


int
_zip_stat_merge(zip_stat_t *dst, const zip_stat_t *src, zip_error_t *error)
{
    if (src->valid & ZIP_STAT_INDEX) {
        dst->index = src->index;
    }
    if (src->valid & ZIP_STAT_SIZE) {
        dst->size = src->size;
    }
    if (src->valid & ZIP_STAT_COMP_SIZE) {
        dst->comp_size = src->comp_size;
    }
    if (src->valid & ZIP_STAT_MTIME) {
        dst->mtime = src->mtime;
    }
    if (src->valid & ZIP_STAT_CRC) {
        dst->crc = src->crc;
    }
    if (src->valid & ZIP_STAT_COMP_METHOD) {
        dst->comp_method = src->comp_method;
    }
    if (src->valid & ZIP_STAT_ENCRYPTION_METHOD) {
        dst->encryption_method = src->encryption_method;
    }
    if (src->valid & ZIP_STAT_FLAGS) {
        dst->flags = src->flags;
    }
    dst->valid |= src->valid;
    
    return 0;
}
