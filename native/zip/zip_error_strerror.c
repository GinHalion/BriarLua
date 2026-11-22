

 


#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "zipint.h"


ZIP_EXTERN const char *
zip_error_strerror(zip_error_t *err)
{
    const char *zs, *ss;
    char buf[128], *s;

    zip_error_fini(err);

    if (err->zip_err < 0 || err->zip_err >= _zip_nerr_str) {
	sprintf(buf, "Unknown error %d", err->zip_err);
	zs = NULL;
	ss = buf;
    }
    else {
	zs = _zip_err_str[err->zip_err];
	
	switch (_zip_err_type[err->zip_err]) {
	case ZIP_ET_SYS:
	    ss = strerror(err->sys_err);
	    break;

	case ZIP_ET_ZLIB:
	    ss = zError(err->sys_err);
	    break;

	default:
	    ss = NULL;
	}
    }

    if (ss == NULL)
	return zs;
    else {
	if ((s=(char *)malloc(strlen(ss)
			      + (zs ? strlen(zs)+2 : 0) + 1)) == NULL)
	    return _zip_err_str[ZIP_ER_MEMORY];
	
	sprintf(s, "%s%s%s",
		(zs ? zs : ""),
		(zs ? ": " : ""),
		ss);
	err->str = s;

	return s;
    }
}
