


#include <stdlib.h>

#include "zipint.h"




void
_zip_free(struct zip *za)
{
    int i;

    if (za == NULL)
	return;

    if (za->zn)
	free(za->zn);

    if (za->zp)
	fclose(za->zp);

    _zip_cdir_free(za->cdir);

    if (za->entry) {
	for (i=0; i<za->nentry; i++) {
	    _zip_entry_free(za->entry+i);
	}
	free(za->entry);
    }

    for (i=0; i<za->nfile; i++) {
	if (za->file[i]->error.zip_err == ZIP_ER_OK) {
	    _zip_error_set(&za->file[i]->error, ZIP_ER_ZIPCLOSED, 0);
	    za->file[i]->za = NULL;
	}
    }

    free(za->file);

    free(za);

    return;
}
