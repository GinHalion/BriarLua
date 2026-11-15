

#include "zipint.h"


ZIP_EXTERN int
zip_set_file_compression(zip_t *za, zip_uint64_t idx, zip_int32_t method, zip_uint32_t flags)
{
    zip_entry_t *e;
    zip_int32_t old_method;

    if (idx >= za->nentry) {
	zip_error_set(&za->error, ZIP_ER_INVAL, 0);
	return -1;
    }

    if (ZIP_IS_RDONLY(za)) {
	zip_error_set(&za->error, ZIP_ER_RDONLY, 0);
	return -1;
    }

    if (method != ZIP_CM_DEFAULT && method != ZIP_CM_STORE && method != ZIP_CM_DEFLATE) {
	zip_error_set(&za->error, ZIP_ER_COMPNOTSUPP, 0);
	return -1;
    }

    e = za->entry+idx;

    old_method = (e->orig == NULL ? ZIP_CM_DEFAULT : e->orig->comp_method);


    if (method == old_method) {
	if (e->changes) {
	    e->changes->changed &= ~ZIP_DIRENT_COMP_METHOD;
	    if (e->changes->changed == 0) {
		_zip_dirent_free(e->changes);
		e->changes = NULL;
	    }
	}
    }
    else {
        if (e->changes == NULL) {
            if ((e->changes=_zip_dirent_clone(e->orig)) == NULL) {
                zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
                return -1;
            }
        }

        e->changes->comp_method = method;
        e->changes->changed |= ZIP_DIRENT_COMP_METHOD;
    }

    return 0;
}
