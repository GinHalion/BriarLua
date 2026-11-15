

#define _ZIP_COMPILING_DEPRECATED
#include "zipint.h"
#include <limits.h>


ZIP_EXTERN int
zip_get_num_files(zip_t *za)
{
    if (za == NULL)
	return -1;

    if (za->nentry > INT_MAX) {
	zip_error_set(&za->error, ZIP_ER_OPNOTSUPP, 0);
	return -1;
    }

    return (int)za->nentry;
}
