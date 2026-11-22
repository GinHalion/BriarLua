

 


#define _ZIP_COMPILING_DEPRECATED
#include "zipint.h"


ZIP_EXTERN int
zip_replace(zip_t *za, zip_uint64_t idx, zip_source_t *source)
{
    return zip_file_replace(za, idx, source, 0);
}
