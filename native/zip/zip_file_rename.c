

#include <string.h>

#include "zipint.h"


ZIP_EXTERN int
zip_file_rename(zip_t *za, zip_uint64_t idx, const char *name, zip_flags_t flags)
{
    const char *old_name;
    int old_is_dir, new_is_dir;

    if (idx >= za->nentry || (name != NULL && strlen(name) > ZIP_UINT16_MAX)) {
	zip_error_set(&za->error, ZIP_ER_INVAL, 0);
	return -1;
    }

    if (ZIP_IS_RDONLY(za)) {
	zip_error_set(&za->error, ZIP_ER_RDONLY, 0);
	return -1;
    }

    if ((old_name=zip_get_name(za, idx, 0)) == NULL)
	return -1;

    new_is_dir = (name != NULL && name[strlen(name)-1] == '/');
    old_is_dir = (old_name[strlen(old_name)-1] == '/');

    if (new_is_dir != old_is_dir) {
	zip_error_set(&za->error, ZIP_ER_INVAL, 0);
	return -1;
    }

    return _zip_set_name(za, idx, name, flags);
}
