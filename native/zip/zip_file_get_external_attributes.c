

 

#include "zipint.h"

int
zip_file_get_external_attributes(zip_t *za, zip_uint64_t idx, zip_flags_t flags, zip_uint8_t *opsys, zip_uint32_t *attributes)
{
    zip_dirent_t *de;

    if ((de=_zip_get_dirent(za, idx, flags, NULL)) == NULL)
	return -1;

    if (opsys)
	*opsys = (zip_uint8_t)((de->version_madeby >> 8) & 0xff);

    if (attributes)
	*attributes = de->ext_attrib;

    return 0;
}
