

 



#include <stdlib.h>

#include "zipint.h"



void
_zip_entry_free(struct zip_entry *ze)
{
    free(ze->ch_filename);
    ze->ch_filename = NULL;
    free(ze->ch_comment);
    ze->ch_comment = NULL;
    ze->ch_comment_len = -1;

    _zip_unchange_data(ze);
}
