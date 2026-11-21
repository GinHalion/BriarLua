

 


#include <stdlib.h>
#include <string.h>
#include <limits.h>

#include "zipint.h"

struct crc_context {
    int eof;
    int validate;
    zip_error_t error;
    zip_uint64_t size;
    zip_uint32_t crc;
};

static zip_int64_t crc_read(zip_source_t *, void *, void *, zip_uint64_t, zip_source_cmd_t);


zip_source_t *
zip_source_crc(zip_t *za, zip_source_t *src, int validate)
{
    struct crc_context *ctx;

    if (src == NULL) {
	zip_error_set(&za->error, ZIP_ER_INVAL, 0);
	return NULL;
    }

    if ((ctx=(struct crc_context *)malloc(sizeof(*ctx))) == NULL) {
	zip_error_set(&za->error, ZIP_ER_MEMORY, 0);
	return NULL;
    }

    ctx->eof = 0;
    ctx->validate = validate;
    zip_error_init(&ctx->error);
    ctx->size = 0;
    ctx->crc = 0;
    
    return zip_source_layered(za, src, crc_read, ctx);
}


static zip_int64_t
crc_read(zip_source_t *src, void *_ctx, void *data, zip_uint64_t len, zip_source_cmd_t cmd)
{
    struct crc_context *ctx;
    zip_int64_t n;

    ctx = (struct crc_context *)_ctx;

    switch (cmd) {
        case ZIP_SOURCE_OPEN:
            ctx->eof = 0;
            ctx->crc = (zip_uint32_t)crc32(0, NULL, 0);
            ctx->size = 0;
            
            return 0;
            
        case ZIP_SOURCE_READ:
            if (ctx->eof || len == 0)
                return 0;
            
            if ((n=zip_source_read(src, data, len)) < 0) {
                _zip_error_set_from_source(&ctx->error, src);
                return -1;
            }
            
            if (n == 0) {
                ctx->eof = 1;
                if (ctx->validate) {
                    struct zip_stat st;
                    
                    if (zip_source_stat(src, &st) < 0) {
                        _zip_error_set_from_source(&ctx->error, src);
                        return -1;
                    }
                    
                    if ((st.valid & ZIP_STAT_CRC) && st.crc != ctx->crc) {
                        zip_error_set(&ctx->error, ZIP_ER_CRC, 0);
                        return -1;
                    }
                    if ((st.valid & ZIP_STAT_SIZE) && st.size != ctx->size) {
                        zip_error_set(&ctx->error, ZIP_ER_INCONS, 0);
                        return -1;
                    }
                }
            }
            else {
		zip_uint64_t i, nn;

		for (i=0; i < (zip_uint64_t)n; i += nn) {
		    nn = ZIP_MIN(UINT_MAX, (zip_uint64_t)n-i);

		    ctx->crc = (zip_uint32_t)crc32(ctx->crc, (const Bytef *)data+i, (uInt)nn);
		}
                ctx->size += (zip_uint64_t)n;
            }
            return n;

        case ZIP_SOURCE_CLOSE:
            return 0;

        case ZIP_SOURCE_STAT:
        {
            zip_stat_t *st;

	    st = (zip_stat_t *)data;

	    if (ctx->eof) {
		st->size = ctx->size;
		st->crc = ctx->crc;
		st->comp_size = ctx->size;
		st->comp_method = ZIP_CM_STORE;
		st->encryption_method = ZIP_EM_NONE;
		st->valid |= ZIP_STAT_SIZE|ZIP_STAT_CRC|ZIP_STAT_COMP_SIZE|ZIP_STAT_COMP_METHOD|ZIP_STAT_ENCRYPTION_METHOD;;
	    }
            return 0;
        }
            
        case ZIP_SOURCE_ERROR:
            return zip_error_to_data(&ctx->error, data, len);

        case ZIP_SOURCE_FREE:
            free(ctx);
            return 0;
            
        case ZIP_SOURCE_SUPPORTS:
            return zip_source_make_command_bitmap(ZIP_SOURCE_OPEN, ZIP_SOURCE_READ, ZIP_SOURCE_CLOSE, ZIP_SOURCE_STAT, ZIP_SOURCE_ERROR, ZIP_SOURCE_FREE, -1);
            
        default:
            zip_error_set(&ctx->error, ZIP_ER_OPNOTSUPP, 0);
            return -1;
    }
}
