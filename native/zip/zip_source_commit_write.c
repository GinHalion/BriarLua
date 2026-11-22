

 


#include "zipint.h"


ZIP_EXTERN int
zip_source_commit_write(zip_source_t *src)
{
    if (!ZIP_SOURCE_IS_OPEN_WRITING(src)) {
        zip_error_set(&src->error, ZIP_ER_INVAL, 0);
        return -1;
    }
    
    if (src->open_count > 1) {
	zip_error_set(&src->error, ZIP_ER_INUSE, 0);
	return -1;
    }
    else if (ZIP_SOURCE_IS_OPEN_READING(src)) {
        if (zip_source_close(src) < 0) {
	    return -1;
	}
    }
    
    if (_zip_source_call(src, NULL, 0, ZIP_SOURCE_COMMIT_WRITE) < 0) {
        src->write_state = ZIP_SOURCE_WRITE_FAILED;
        return -1;
    }
    
    src->write_state = ZIP_SOURCE_WRITE_CLOSED;
    
    return 0;
}
