

 


#include <stdlib.h>

#include "zipint.h"


ZIP_EXTERN int
zip_unchange_all(zip_t *za)
{
    int ret;
    zip_uint64_t i;

    ret = 0;
    for (i=0; i<za->nentry; i++)
	ret |= _zip_unchange(za, i, 1);

    ret |= zip_unchange_archive(za);

    return ret;
}
