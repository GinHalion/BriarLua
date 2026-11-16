

 


#include <stdlib.h>

#include "zipint.h"


ZIP_EXTERN int
zip_unchange_archive(zip_t *za)
{
    if (za->comment_changed) {
	_zip_string_free(za->comment_changes);
	za->comment_changes = NULL;
	za->comment_changed = 0;
    }
    
    za->ch_flags = za->flags;

    return 0;
}
