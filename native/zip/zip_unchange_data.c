

#include "zipint.h"

void
_zip_unchange_data(zip_entry_t *ze)
{
    if (ze->source) {
	zip_source_free(ze->source);
	ze->source = NULL;
    }

    if (ze->changes != NULL && (ze->changes->changed & ZIP_DIRENT_COMP_METHOD) && ze->changes->comp_method == ZIP_CM_REPLACED_DEFAULT) {
	ze->changes->changed &= ~ZIP_DIRENT_COMP_METHOD;
	if (ze->changes->changed == 0) {
	    _zip_dirent_free(ze->changes);
	    ze->changes = NULL;
	}
    }

    ze->deleted = 0;
}

