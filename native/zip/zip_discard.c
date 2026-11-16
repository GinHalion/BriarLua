

 


#include <stdlib.h>

#include "zipint.h"



void
zip_discard(zip_t *za)
{
    zip_uint64_t i;

    if (za == NULL)
	return;

    if (za->src) {
	zip_source_close(za->src);
	zip_source_free(za->src);
    }

    free(za->default_password);
    _zip_string_free(za->comment_orig);
    _zip_string_free(za->comment_changes);

    if (za->entry) {
	for (i=0; i<za->nentry; i++)
	    _zip_entry_finalize(za->entry+i);
	free(za->entry);
    }

    for (i=0; i<za->nopen_source; i++) {
	_zip_source_invalidate(za->open_source[i]);
    }
    free(za->open_source);

    zip_error_fini(&za->error);
    
    free(za);

    return;
}
