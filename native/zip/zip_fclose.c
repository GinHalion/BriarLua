

#include <stdlib.h>

#include "zipint.h"


ZIP_EXTERN int
zip_fclose(zip_file_t *zf)
{
    int ret;

    if (zf->src)
	zip_source_free(zf->src);

    ret = 0;
    if (zf->error.zip_err)
	ret = zf->error.zip_err;

    zip_error_fini(&zf->error);
    free(zf);
    return ret;
}
