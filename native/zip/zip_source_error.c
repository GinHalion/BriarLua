

#include "zipint.h"


zip_error_t *
zip_source_error(zip_source_t *src)
{
    return &src->error;
}
