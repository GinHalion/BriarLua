

#include "zipint.h"


ZIP_EXTERN int
zip_source_seek_write(zip_source_t *src, zip_int64_t offset, int whence)
{
    zip_source_args_seek_t args;

    if (!ZIP_SOURCE_IS_OPEN_WRITING(src) || (whence != SEEK_SET && whence != SEEK_CUR && whence != SEEK_END)) {
        zip_error_set(&src->error, ZIP_ER_INVAL, 0);
        return -1;
    }

    args.offset = offset;
    args.whence = whence;

    return (_zip_source_call(src, &args, sizeof(args), ZIP_SOURCE_SEEK_WRITE) < 0 ? -1 : 0);
}
