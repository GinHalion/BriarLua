

 


#include "zipint.h"


ZIP_EXTERN int
zip_delete(zip_t *za, zip_uint64_t idx)
{
    if (idx >= za->nentry) {
	zip_error_set(&za->error, ZIP_ER_INVAL, 0);
	return -1;
    }

    if (ZIP_IS_RDONLY(za)) {
	zip_error_set(&za->error, ZIP_ER_RDONLY, 0);
	return -1;
    }

    if (_zip_unchange(za, idx, 1) != 0)
	return -1;

    za->entry[idx].deleted = 1;

    return 0;
}

