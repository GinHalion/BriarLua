

 


#include "zipint.h"


ZIP_EXTERN zip_int64_t
zip_file_add(zip_t *za, const char *name, zip_source_t *source, zip_flags_t flags)
{
    if (name == NULL || source == NULL) {
	zip_error_set(&za->error, ZIP_ER_INVAL, 0);
	return -1;
    }
	
    return _zip_file_replace(za, ZIP_UINT64_MAX, name, source, flags);
}
