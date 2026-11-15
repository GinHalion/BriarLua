

#include <stdlib.h>
#include <string.h>

#include "zipint.h"


int
_zip_set_name(zip_t *za, zip_uint64_t idx, const char *name, zip_flags_t flags)
{
    zip_entry_t *e;
    zip_string_t *str;
    int changed;
    zip_int64_t i;

    if (idx >= za->nentry) {
	zip_error_set(&za->error, ZIP_ER_INVAL, 0);
	return -1;
    }

    if (ZIP_IS_RDONLY(za)) {
	zip_error_set(&za->error, ZIP_ER_RDONLY, 0);
	return -1;
    }

    if (name && strlen(name) > 0) {
	if ((str=_zip_string_new((const zip_uint8_t *)name, (zip_uint16_t)strlen(name), flags, &za->error)) == NULL)
	    return -1;
	if ((flags & ZIP_FL_ENCODING_ALL) == ZIP_FL_ENC_GUESS && _zip_guess_encoding(str, ZIP_ENCODING_UNKNOWN) == ZIP_ENCODING_UTF8_GUESSED)
	    str->encoding = ZIP_ENCODING_UTF8_KNOWN;
    }
    else
	str = NULL;

    if ((i=_zip_name_locate(za, name, 0, NULL)) >= 0 && (zip_uint64_t)i != idx) {
	_zip_string_free(str);
	zip_error_set(&za->error, ZIP_ER_EXISTS, 0);
	return -1;
    }

    if (i>=0 && (zip_uint64_t)i == idx) {
	_zip_string_free(str);
	return 0;
    }

    e = za->entry+idx;

    if (e->changes) {
	_zip_string_free(e->changes->filename);
	e->changes->filename = NULL;
	e->changes->changed &= ~ZIP_DIRENT_FILENAME;
    }

    if (e->orig)
	changed = !_zip_string_equal(e->orig->filename, str);
    else
	changed = 1;

    if (changed) {
        if (e->changes == NULL) {
            if ((e->changes=_zip_dirent_clone(e->orig)) == NULL) {
                zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
		_zip_string_free(str);
                return -1;
            }
        }
        e->changes->filename = str;
        e->changes->changed |= ZIP_DIRENT_FILENAME;
    }
    else {
	_zip_string_free(str);
	if (e->changes && e->changes->changed == 0) {
	    _zip_dirent_free(e->changes);
	    e->changes = NULL;
	}
    }

    return 0;
}
