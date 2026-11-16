

 

#include "zipint.h"

ZIP_EXTERN int
zip_file_set_external_attributes(zip_t *za, zip_uint64_t idx, zip_flags_t flags, zip_uint8_t opsys, zip_uint32_t attributes)
{
    zip_entry_t *e;
    int changed;
    zip_uint8_t unchanged_opsys;
    zip_uint32_t unchanged_attributes;

    if (_zip_get_dirent(za, idx, 0, NULL) == NULL)
	return -1;

    if (ZIP_IS_RDONLY(za)) {
	zip_error_set(&za->error, ZIP_ER_RDONLY, 0);
	return -1;
    }

    e = za->entry+idx;

    unchanged_opsys = (e->orig ? (zip_uint8_t)(e->orig->version_madeby>>8) : (zip_uint8_t)ZIP_OPSYS_DEFAULT);
    unchanged_attributes = e->orig ? e->orig->ext_attrib : ZIP_EXT_ATTRIB_DEFAULT;

    changed = (opsys != unchanged_opsys || attributes != unchanged_attributes);

    if (changed) {
        if (e->changes == NULL) {
            if ((e->changes=_zip_dirent_clone(e->orig)) == NULL) {
                zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
                return -1;
            }
        }
        e->changes->version_madeby = (zip_uint16_t)((opsys << 8) | (e->changes->version_madeby & 0xff));
	e->changes->ext_attrib = attributes;
        e->changes->changed |= ZIP_DIRENT_ATTRIBUTES;
    }
    else if (e->changes) {
	e->changes->changed &= ~ZIP_DIRENT_ATTRIBUTES;
	if (e->changes->changed == 0) {
	    _zip_dirent_free(e->changes);
	    e->changes = NULL;
	}
	else {
	    e->changes->version_madeby = (zip_uint16_t)((unchanged_opsys << 8) | (e->changes->version_madeby & 0xff));
	    e->changes->ext_attrib = unchanged_attributes;
	}
    }

    return 0;
}
