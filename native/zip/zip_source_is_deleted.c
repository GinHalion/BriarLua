

 


#include "zipint.h"


ZIP_EXTERN int
zip_source_is_deleted(zip_source_t *src)
{
    return src->write_state == ZIP_SOURCE_WRITE_REMOVED;
}
