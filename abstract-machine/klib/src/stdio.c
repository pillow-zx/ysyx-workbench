#include <am.h>
#include <klib.h>
#include <klib-macros.h>
#include <stdarg.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

static int getlength(int num)
{
	int len = 0;
	if (num == 0)
		return 1;
	if (num < 0) {
		len = 1;
		num = -num;
	}
	while (num != 0) {
		num /= 10;
		len++;
	}
	return len;
}

static void reverse_string(char *str, int len, int is_negative)
{
	int start = is_negative ? 1 : 0;
	int end = len - 1;
	while (start < end) {
		char temp = str[start];
		str[start] = str[end];
		str[end] = temp;
		start++;
		end--;
	}
}

static int is_digit(char c)
{
	return c >= '0' && c <= '9';
}

static int string_to_int(const char *str)
{
	int num = 0;
	int sign = 1;
	if (*str == '-') {
		sign = -1;
		str++;
	}
	while (is_digit(*str)) {
		num = num * 10 + (*str - '0');
		str++;
	}
	return num * sign;
}

static char *int_format(int num, const char *width)
{
	char *ptr = (char *)width + 1;
	int number = string_to_int(ptr);
	int len = getlength(num);
	static char str[20];
	int is_negative = 0;
	if (num < 0) {
		is_negative = 1;
		num = -num;
	}
	int i = 0;
	if (len < number) {
		while (i < number - len) {
			str[i++] = width[0]; // Fill with the specified width
					     // character
		}
	}
	if (is_negative) {
		str[i++] = '-';
	}
	// Convert the number to string
	do {
		str[i++] = (num % 10) + '0';
		num /= 10;
	} while (num > 0);
	str[i] = '\0';
	reverse_string(str, i, is_negative);
	return str;
}

static char *string_format(const char *str, const char *width)
{
	char *ptr = (char *)width + 1;
	int number = string_to_int(ptr);
	static char formatted[100];
	int len = strlen(str);
	if (len < number) {
		int i;
		for (i = 0; i < number - len; i++) {
			formatted[i] = width[0]; // Fill with the specified
						 // width character
		}
		strcpy(formatted + i, str);
	} else {
		strcpy(formatted, str);
	}
	return formatted;
}

static char *hex_format(unsigned int num, const char *width)
{
	static char str[20];
	int i = 0;

	// Handle zero case
	if (num == 0) {
		str[i++] = '0';
	} else {
		// Convert to hex
		while (num > 0) {
			int digit = num % 16;
			if (digit < 10) {
				str[i++] = digit + '0';
			} else {
				str[i++] = digit - 10 + 'a';
			}
			num /= 16;
		}
	}

	// Apply width formatting if needed and width is specified
	if (width[0] != '\0') {
		char *ptr = (char *)width + 1;
		int number = string_to_int(ptr);
		if (i < number) {
			for (int j = i; j < number; j++) {
				str[j] = width[0]; // Fill with the specified
						   // width character
			}
			i = number;
		}
	}

	str[i] = '\0';

	// Reverse the string
	int start = 0;
	int end = i - 1;
	while (start < end) {
		char temp = str[start];
		str[start] = str[end];
		str[end] = temp;
		start++;
		end--;
	}

	return str;
}

int vsprintf(char *out, const char *fmt, va_list ap)
{
	char *p = out;
	char *temp = (char *)fmt;
	while (*temp) {
		if (*temp == '%') {
			temp++;
			char width[10] = {
				0}; // Initialize buffer for format specifier
			if (is_digit(*temp)) {
				char *ptr = width;
				while (is_digit(*temp)) {
					*ptr++ = *temp++;
				}
				*ptr = '\0'; // Null-terminate the buffer
			}
			switch (*temp) {
			case 'd': {
				int num = va_arg(ap, int);
				char *str = int_format(num, width);
				while (*str) {
					*p++ = *str++;
				}
				break;
			}
			case 's': {
				char *str = string_format(va_arg(ap, char *),
							  width);
				while (*str) {
					*p++ = *str++;
				}
				break;
			}
			case 'x': {
				unsigned int num = va_arg(ap, unsigned int);
				char *str = hex_format(num, width);
				while (*str) {
					*p++ = *str++;
				}
				break;
			}
			case 'c': {
				char ch = (char)va_arg(ap, int);
				*p++ = ch;
				break;
			}
			default:
				// Handle unknown format specifier by just
				// copying it
				*p++ = '%';
				*p++ = *temp;
				break;
			}
		} else {
			// Copy regular characters
			*p++ = *temp;
		}
		temp++;
	}
	*p = '\0';	// Only set null terminator at the end
	return p - out; // Return the number of characters written
}

int sprintf(char *out, const char *fmt, ...)
{
	va_list ap;
	va_start(ap, fmt);
	int ret = vsprintf(out, fmt, ap);
	va_end(ap);
	return ret;
}

int vsnprintf(char *out, size_t n, const char *fmt, va_list ap)
{
	int ret = vsprintf(out, fmt, ap);
	return ret < n ? ret
		       : n; // Ensure we do not write more than n characters
}

int snprintf(char *out, size_t n, const char *fmt, ...)
{
	va_list ap;
	va_start(ap, fmt);
	int ret = vsnprintf(out, n, fmt, ap);
	return ret;
}

int printf(const char *fmt, ...)
{
	va_list ap;
	va_start(ap, fmt);

	char buffer[4096]; // Temporary buffer for output
	int ret = vsprintf(buffer, fmt, ap);
	va_end(ap);

	putstr(buffer); // Output to console

	return ret;
}

#endif
