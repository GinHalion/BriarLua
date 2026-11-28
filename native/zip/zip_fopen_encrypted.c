

 


#include "zipint.h"


ZIP_EXTERN zip_file_t *
zip_fopen_encrypted(zip_t *za, const char *fname, zip_flags_t flags, const char *password)
{
    zip_int64_t idx;

    if ((idx=zip_name_locate(za, fname, flags)) < 0)
	return NULL;

    return zip_fopen_index_encrypted(za, (zip_uint64_t)idx, flags, password);
}
