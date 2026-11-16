



#include <sys/types.h>
#include <sys/stat.h>

#include <assert.h>
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#ifndef O_BINARY
#define O_BINARY 0
#endif



int
_zip_mkstemp(char *path)
{
	int fd;
	char *start, *trv;
	struct stat sbuf;
	pid_t pid;

	static char xtra[2] = "aa";
	int xcnt = 0;

	pid = getpid();

	for (trv = path; *trv; ++trv)
		if (*trv == 'X')
			xcnt++;
		else
			xcnt = 0;

	if (*(trv - 1) == 'X')
		*--trv = xtra[0];
	if (xcnt > 6 && *(trv - 1) == 'X')
		*--trv = xtra[1];

	while (*--trv == 'X') {
		*trv = (pid % 10) + '0';
		pid /= 10;
	}

	if (xtra[0] != 'z')
		xtra[0]++;
	else {
		xtra[0] = 'a';
		if (xtra[1] != 'z')
			xtra[1]++;
		else
			xtra[1] = 'a';
	}

	for (start = trv + 1;; --trv) {
		if (trv <= path)
			break;
		if (*trv == '/') {
			*trv = '\0';
			if (stat(path, &sbuf))
				return (0);
			if (!S_ISDIR(sbuf.st_mode)) {
				errno = ENOTDIR;
				return (0);
			}
			*trv = '/';
			break;
		}
	}

	for (;;) {
		if ((fd=open(path, O_CREAT|O_EXCL|O_RDWR|O_BINARY, 0600)) >= 0)
			return (fd);
		if (errno != EEXIST)
			return (0);

		for (trv = start;;) {
			if (!*trv)
				return (0);
			if (*trv == 'z')
				*trv++ = 'a';
			else {
				if (isdigit((unsigned char)*trv))
					*trv = 'a';
				else
					++*trv;
				break;
			}
		}
	}
}
