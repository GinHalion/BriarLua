

#include <stdlib.h>

#include "zipint.h"


ZIP_EXTERN zip_source_t *
zip_source_function(zip_t *za, zip_source_callback zcb, void *ud)
{
    if (za == NULL) {
        return NULL;
    }

    return zip_source_function_create(zcb, ud, &za->error);
}


ZIP_EXTERN zip_source_t *
zip_source_function_create(zip_source_callback zcb, void *ud, zip_error_t *error)
{
    zip_source_t *zs;

    if ((zs=_zip_source_new(error)) == NULL)
	return NULL;

    zs->cb.f = zcb;
    zs->ud = ud;

    zs->supports = zcb(ud, NULL, 0, ZIP_SOURCE_SUPPORTS);
    if (zs->supports < 0) {
        zs->supports = ZIP_SOURCE_SUPPORTS_READABLE;
    }

    return zs;
}


ZIP_EXTERN void
zip_source_keep(zip_source_t *src)
{
    src->refcount++;
}


zip_source_t *
_zip_source_new(zip_error_t *error)
{
    zip_source_t *src;

    if ((src=(zip_source_t *)malloc(sizeof(*src))) == NULL) {
        zip_error_set(error, ZIP_ER_MEMORY, 0);
	return NULL;
    }

    src->src = NULL;
    src->cb.f = NULL;
    src->ud = NULL;
    src->open_count = 0;
    src->write_state = ZIP_SOURCE_WRITE_CLOSED;
    src->source_closed = false;
    src->source_archive = NULL;
    src->refcount = 1;
    zip_error_init(&src->error);

    return src;
}
