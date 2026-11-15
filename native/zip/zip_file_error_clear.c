

#include "zipint.h"


ZIP_EXTERN void
zip_file_error_clear(zip_file_t *zf)
{
    if (zf == NULL)
	return;

    _zip_error_clear(&zf->error);
}
