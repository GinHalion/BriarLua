
#define _ZIP_COMPILING_DEPRECATED
#include "zipint.h"


ZIP_EXTERN int
zip_error_get_sys_type(int ze)
{
    if (ze < 0 || ze >= _zip_nerr_str)
	return 0;

    return _zip_err_type[ze];
}
