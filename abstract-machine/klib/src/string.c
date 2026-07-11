#include <klib.h>
#include <klib-macros.h>
#include <stdint.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

size_t strlen(const char *s)
{
	const char *p = s;
	while (*p) {
		p++;
	}
	return p - s;
}

char *strcpy(char *dst, const char *src)
{
	char *d = dst;
	while ((*d++ = *src++))
		;
	return dst;
}

char *strncpy(char *dst, const char *src, size_t n)
{
	char *d = dst;
	while (n && (*d++ = *src++)) {
		n--;
	}
	if (n) {
		while (--n) {
			*d++ = '\0';
		}
	}
	return dst;
}

char *strcat(char *dst, const char *src)
{
	char *d = dst;
	while (*d) {
		d++;
	}
	while ((*d++ = *src++))
		;
	return dst;
}

int strcmp(const char *s1, const char *s2)
{
	while (*s1 && (*s1 == *s2)) {
		s1++;
		s2++;
	}
	return *(unsigned char *)s1 - *(unsigned char *)s2;
}

int strncmp(const char *s1, const char *s2, size_t n)
{
	while (n && *s1 && (*s1 == *s2)) {
		s1++;
		s2++;
		n--;
	}
	if (n == 0) {
		return 0;
	}
	return *(unsigned char *)s1 - *(unsigned char *)s2;
}

void *memset(void *s, int c, size_t n)
{
	unsigned char *p = s;
	while (n--) {
		*p++ = (unsigned char)c;
	}
	return s;
}

void *memmove(void *dst, const void *src, size_t n)
{
	unsigned char *d = dst;
	const unsigned char *s = src;

	if (d < s) {
		while (n--) {
			*d++ = *s++;
		}
	} else if (d > s) {
		d += n;
		s += n;
		while (n--) {
			*(--d) = *(--s);
		}
	}
	return dst;
}

void *memcpy(void *out, const void *in, size_t n)
{
	unsigned char *d = out;
	const unsigned char *s = in;
	while (n--) {
		*d++ = *s++;
	}
	return out;
}

int memcmp(const void *s1, const void *s2, size_t n)
{
	const unsigned char *p1 = s1;
	const unsigned char *p2 = s2;
	while (n--) {
		if (*p1 != *p2) {
			return *p1 - *p2;
		}
		p1++;
		p2++;
	}
	return 0;
}

#endif
