

#include "zipint.h"


zip_encryption_implementation
_zip_get_encryption_implementation(zip_uint16_t em)
{
    if (em == ZIP_EM_TRAD_PKWARE)
	return zip_source_pkware;
    return NULL;
}
