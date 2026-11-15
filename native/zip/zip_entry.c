

#include "zipint.h"

void
_zip_entry_finalize(zip_entry_t *e)
{
    _zip_unchange_data(e);
    _zip_dirent_free(e->orig);
    _zip_dirent_free(e->changes);
}


void
_zip_entry_init(zip_entry_t *e)
{
    e->orig = NULL;
    e->changes = NULL;
    e->source = NULL;
    e->deleted = 0;
}
