

 


#include "zipint.h"


ZIP_EXTERN zip_t *
zip_fdopen(int fd_orig, int _flags, int *zep)
{
    int fd;
    FILE *fp;
    zip_t *za;
    zip_source_t *src;
    struct zip_error error;

    if (_flags < 0 || (_flags & ZIP_TRUNCATE)) {
	_zip_set_open_error(zep, NULL, ZIP_ER_INVAL);
        return  NULL;
    }
        

    if ((fd=dup(fd_orig)) < 0) {
	_zip_set_open_error(zep, NULL, ZIP_ER_OPEN);
	return NULL;
    }

    if ((fp=fdopen(fd, "rb")) == NULL) {
	close(fd);
	_zip_set_open_error(zep, NULL, ZIP_ER_OPEN);
	return NULL;
    }

    zip_error_init(&error);
    if ((src = zip_source_filep_create(fp, 0, -1, &error)) == NULL) {
	_zip_set_open_error(zep, &error, 0);
	zip_error_fini(&error);
	return NULL;
    }

    if ((za = zip_open_from_source(src, _flags, &error)) == NULL) {
	_zip_set_open_error(zep, &error, 0);
	zip_error_fini(&error);
	return NULL;
    }

    zip_error_fini(&error);
    close(fd_orig);
    return za;
}
