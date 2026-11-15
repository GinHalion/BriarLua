

#include <stdlib.h>
#include <string.h>

#include "zipint.h"


ZIP_EXTERN int
zip_set_default_password(zip_t *za, const char *passwd)
{
    if (za == NULL)
	return -1;

    if (za->default_password)
	free(za->default_password);

    if (passwd) {
	if ((za->default_password=strdup(passwd)) == NULL) {
	    zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
	    return -1;
	}
    }
    else
	za->default_password = NULL;

    return 0;
}
