

 


#include "zipint.h"


ZIP_EXTERN int
zip_source_begin_write(zip_source_t *src)
{
    if (ZIP_SOURCE_IS_OPEN_WRITING(src)) {
        zip_error_set(&src->error, ZIP_ER_INVAL, 0);
        return -1;
    }
    
    if (_zip_source_call(src, NULL, 0, ZIP_SOURCE_BEGIN_WRITE) < 0) {
        return -1;
    }

    src->write_state = ZIP_SOURCE_WRITE_OPEN;
    
    return 0;
}
