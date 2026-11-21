

 


#include <errno.h>
#include <stdio.h>

#include "zipint.h"


ZIP_EXTERN zip_source_t *
zip_source_file(zip_t *za, const char *fname, zip_uint64_t start, zip_int64_t len)
{
    if (za == NULL)
	return NULL;
    
    return zip_source_file_create(fname, start, len, &za->error);
}


ZIP_EXTERN zip_source_t *
zip_source_file_create(const char *fname, zip_uint64_t start, zip_int64_t length, zip_error_t *error)
{
    if (fname == NULL || length < -1) {
	zip_error_set(error, ZIP_ER_INVAL, 0);
	return NULL;
    }

    return _zip_source_file_or_p(fname, NULL, start, length, NULL, error);
}
