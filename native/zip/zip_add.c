

 


#define _ZIP_COMPILING_DEPRECATED
#include "zipint.h"



ZIP_EXTERN zip_int64_t
zip_add(zip_t *za, const char *name, zip_source_t *source)
{
    return zip_file_add(za, name, source, 0);
}
