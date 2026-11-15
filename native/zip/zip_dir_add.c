

#include <stdlib.h>
#include <string.h>

#include "zipint.h"



ZIP_EXTERN zip_int64_t
zip_dir_add(zip_t *za, const char *name, zip_flags_t flags)
{
    size_t len;
    zip_int64_t idx;
    char *s;
    zip_source_t *source;

    if (ZIP_IS_RDONLY(za)) {
	zip_error_set(&za->error, ZIP_ER_RDONLY, 0);
	return -1;
    }

    if (name == NULL) {
	zip_error_set(&za->error, ZIP_ER_INVAL, 0);
	return -1;
    }

    s = NULL;
    len = strlen(name);

    if (name[len-1] != '/') {
	if ((s=(char *)malloc(len+2)) == NULL) {
	    zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
	    return -1;
	}
	strcpy(s, name);
	s[len] = '/';
	s[len+1] = '\0';
    }

    if ((source=zip_source_buffer(za, NULL, 0, 0)) == NULL) {
	free(s);
	return -1;
    }

    idx = _zip_file_replace(za, ZIP_UINT64_MAX, s ? s : name, source, flags);

    free(s);

    if (idx < 0)
	zip_source_free(source);
    else {
	if (zip_file_set_external_attributes(za, (zip_uint64_t)idx, 0, ZIP_OPSYS_DEFAULT, ZIP_EXT_ATTRIB_DEFAULT_DIR) < 0) {
	    zip_delete(za, (zip_uint64_t)idx);
	    return -1;
	}
    }

    return idx;
}
