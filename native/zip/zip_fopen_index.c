

 


#include <errno.h>
#include <stdio.h>
#include <stdlib.h>

#include "zipint.h"


ZIP_EXTERN zip_file_t *
zip_fopen_index(zip_t *za, zip_uint64_t index, zip_flags_t flags)
{
    return zip_fopen_index_encrypted(za, index, flags, za->default_password);
}
