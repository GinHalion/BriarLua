

 


#include "zipint.h"


ZIP_EXTERN int
zip_stat(zip_t *za, const char *fname, zip_flags_t flags, zip_stat_t *st)
{
    zip_int64_t idx;

    if ((idx=zip_name_locate(za, fname, flags)) < 0)
	return -1;

    return zip_stat_index(za, (zip_uint64_t)idx, flags, st);
}
