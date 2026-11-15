
#include <stdlib.h>
#include <string.h>

#include "zipint.h"


void *
_zip_memdup(const void *mem, size_t len, zip_error_t *error)
{
    void *ret;

    if (len == 0)
	return NULL;

    ret = malloc(len);
    if (!ret) {
	zip_error_set(error, ZIP_ER_MEMORY, 0);
	return NULL;
    }

    memcpy(ret, mem, len);

    return ret;
}
