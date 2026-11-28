

 


#include <stdio.h>
#include <errno.h>

#include "zipint.h"



int
_zip_filerange_crc(zip_source_t *src, zip_uint64_t start, zip_uint64_t len, uLong *crcp, zip_error_t *error)
{
    Bytef buf[BUFSIZE];
    zip_int64_t n;

    *crcp = crc32(0L, Z_NULL, 0);

    if (start > ZIP_INT64_MAX) {
	zip_error_set(error, ZIP_ER_SEEK, EFBIG);
	return -1;
    }

    if (zip_source_seek(src, (zip_int64_t)start, SEEK_SET) != 0) {
	_zip_error_set_from_source(error, src);
	return -1;
    }
    
    while (len > 0) {
	n = (zip_int64_t)(len > BUFSIZE ? BUFSIZE : len);
	if ((n = zip_source_read(src, buf, (zip_uint64_t)n)) < 0) {
	    _zip_error_set_from_source(error, src);
	    return -1;
	}
	if (n == 0) {
	    zip_error_set(error, ZIP_ER_EOF, 0);
	    return -1;
	}

	*crcp = crc32(*crcp, buf, (uInt)n);

	len -= (zip_uint64_t)n;
    }

    return 0;
}
