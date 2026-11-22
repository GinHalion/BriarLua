

 


#include <stdarg.h>

#include "zipint.h"


zip_int64_t
zip_source_supports(zip_source_t *src)
{
    return src->supports;
}


zip_int64_t
zip_source_make_command_bitmap(zip_source_cmd_t cmd0, ...)
{
    zip_int64_t bitmap;
    va_list ap;
    
    bitmap = ZIP_SOURCE_MAKE_COMMAND_BITMASK(cmd0);
    
    
    
    va_start(ap, cmd0);
    for (;;) {
        int cmd = va_arg(ap, int);
        if (cmd < 0) {
            break;
        }
        bitmap |= ZIP_SOURCE_MAKE_COMMAND_BITMASK(cmd);
    }
    va_end(ap);
    
    return bitmap;
}
