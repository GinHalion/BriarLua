 
 
 


#include "zipint.h"


zip_int64_t
_zip_source_call(zip_source_t *src, void *data, zip_uint64_t length, zip_source_cmd_t command)
{
    zip_int64_t ret;
    
    if ((src->supports & ZIP_SOURCE_MAKE_COMMAND_BITMASK(command)) == 0) {
        zip_error_set(&src->error, ZIP_ER_OPNOTSUPP, 0);
        return -1;
    }

    if (src->src == NULL) {
        ret = src->cb.f(src->ud, data, length, command);
    }
    else {
        ret = src->cb.l(src->src, src->ud, data, length, command);
    }
    
    if (ret < 0) {
        if (command != ZIP_SOURCE_ERROR && command != ZIP_SOURCE_SUPPORTS) {
            int e[2];
            
            if (_zip_source_call(src, e, sizeof(e), ZIP_SOURCE_ERROR) < 0) {
                zip_error_set(&src->error, ZIP_ER_INTERNAL, 0);
            }
            else {
                zip_error_set(&src->error, e[0], e[1]);
            }
        }
    }

    return ret;
}
