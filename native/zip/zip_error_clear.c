

 


#include "zipint.h"


ZIP_EXTERN void
zip_error_clear(zip_t *za)
{
    if (za == NULL)
	return;

    _zip_error_clear(&za->error);
}
