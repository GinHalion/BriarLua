

 


#include "zipint.h"


ZIP_EXTERN zip_int64_t
zip_source_tell(zip_source_t *src)
{
    if (src->source_closed) {
        return -1;
    }
    if (!ZIP_SOURCE_IS_OPEN_READING(src)) {
        zip_error_set(&src->error, ZIP_ER_INVAL, 0);
        return -1;
    }
    
    return _zip_source_call(src, NULL, 0, ZIP_SOURCE_TELL);
}
