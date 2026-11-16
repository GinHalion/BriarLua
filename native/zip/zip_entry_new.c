

 



#include <stdlib.h>

#include "zipint.h"



struct zip_entry *
_zip_entry_new(struct zip *za)
{
    struct zip_entry *ze;
    if (!za) {
	ze = (struct zip_entry *)malloc(sizeof(struct zip_entry));
	if (!ze) {
	    _zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
	    return NULL;
	}
    }
    else {
	if (za->nentry >= za->nentry_alloc-1) {
	    za->nentry_alloc += 16;
	    za->entry = (struct zip_entry *)realloc(za->entry,
						    sizeof(struct zip_entry)
						    * za->nentry_alloc);
	    if (!za->entry) {
		_zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
		return NULL;
	    }
	}
	ze = za->entry+za->nentry;
    }

    ze->state = ZIP_ST_UNCHANGED;

    ze->ch_filename = NULL;
    ze->ch_comment = NULL;
    ze->ch_comment_len = -1;
    ze->source = NULL;

    if (za)
	za->nentry++;

    return ze;
}
