

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>

#include "zipint.h"

static zip_file_t *_zip_file_new(zip_t *za);


ZIP_EXTERN zip_file_t *
zip_fopen_index_encrypted(zip_t *za, zip_uint64_t index, zip_flags_t flags,
			  const char *password)
{
    zip_file_t *zf;
    zip_source_t *src;

    if ((src=_zip_source_zip_new(za, za, index, flags, 0, 0, password)) == NULL)
	return NULL;

    if (zip_source_open(src) < 0) {
	_zip_error_set_from_source(&za->error, src);
	zip_source_free(src);
	return NULL;
    }

    if ((zf=_zip_file_new(za)) == NULL) {
	zip_source_free(src);
	return NULL;
    }

    zf->src = src;

    return zf;
}


static zip_file_t *
_zip_file_new(zip_t *za)
{
    zip_file_t *zf;

    if ((zf=(zip_file_t *)malloc(sizeof(struct zip_file))) == NULL) {
	zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
	return NULL;
    }

    zf->za = za;
    zip_error_init(&zf->error);
    zf->eof = 0;
    zf->src = NULL;

    return zf;
}
