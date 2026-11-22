

 


#include <stdlib.h>

#include "zipint.h"



zip_int64_t
_zip_add_entry(zip_t *za)
{
    zip_uint64_t idx;

    if (za->nentry+1 >= za->nentry_alloc) {
	zip_entry_t *rentries;
	zip_uint64_t nalloc = za->nentry_alloc + 16;
	zip_uint64_t realloc_size = sizeof(struct zip_entry) * (size_t)nalloc;

	if (sizeof(struct zip_entry) * (size_t)za->nentry_alloc > realloc_size) {
	    zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
	    return -1;
	}
	rentries = (zip_entry_t *)realloc(za->entry, sizeof(struct zip_entry) * (size_t)nalloc);
	if (!rentries) {
	    zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
	    return -1;
	}
	za->entry = rentries;
	za->nentry_alloc = nalloc;
    }

    idx = za->nentry++;

    _zip_entry_init(za->entry+idx);

    return (zip_int64_t)idx;
}
