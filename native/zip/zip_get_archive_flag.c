

 


#include "zipint.h"


ZIP_EXTERN int
zip_get_archive_flag(zip_t *za, zip_flags_t flag, zip_flags_t flags)
{
    unsigned int fl;

    fl = (flags & ZIP_FL_UNCHANGED) ? za->flags : za->ch_flags;

    return (fl & flag) ? 1 : 0;
}
