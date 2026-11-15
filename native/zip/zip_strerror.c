

#include "zipint.h"


ZIP_EXTERN const char *
zip_strerror(zip_t *za)
{
    return zip_error_strerror(&za->error);
}
