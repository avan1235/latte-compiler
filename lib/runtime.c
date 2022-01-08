#include <inttypes.h>
#include <stdlib.h>
#include <limits.h>
#include <stdio.h>
#include <errno.h>

static const size_t INIT_STRING_BUFFER_SIZE = 8;

static const int RUNTIME_ERROR_CODE = 1;

static void error_exit(char *message, int exit_code) {
    fprintf(stderr, "%s", message);
    exit(exit_code);
}

static void *checked_malloc(size_t size) {
    void *result = malloc(size);
    if (result == NULL) {
        error_exit("Error in memory allocation.\n", RUNTIME_ERROR_CODE);
    }
    return result;
}

static void *checked_realloc(void * ptr, size_t size) {
    void *result = realloc(ptr, size);
    if (result == NULL) {
        error_exit("Error in memory reallocation.\n", RUNTIME_ERROR_CODE);
    }
    return result;
}

void __error() {
    error_exit("runtime error\n", RUNTIME_ERROR_CODE);
}

void __printInt(int32_t value) {
    fprintf(stdout, "%" PRId32 "\n", value);
}

void __printString(char* value) {
    fprintf(stdout, "%s\n", value);
}

char* __readString() {
    size_t size = INIT_STRING_BUFFER_SIZE;
    char *data = checked_malloc(size + 1);
    size_t length = 0;
    int c;

    while ((c = fgetc(stdin)) != EOF && c != '\n') {
        if (length == size) {
            size *= 2;
            data = checked_realloc(data, size + 1);
        }
        *(data + length) = (char) c;
        length += 1;
    }
    *(data + length) = '\0';
    return data;
}

int32_t __readInt() {
    char *data = __readString();
    char *end_ptr;
    long long int v = strtoll(data, &end_ptr, 10);
    if ((errno == ERANGE && (v == LONG_MAX || v == LONG_MIN)) || (errno != 0 && v == 0)) {
        error_exit("Cannot parse int value from read input line.\n", RUNTIME_ERROR_CODE);
    }
    if (*end_ptr != '\0') {
        error_exit("There is data left in read string that couldn't be parsed to int.\n", RUNTIME_ERROR_CODE);
    }
    free(data);
    if (v < INT32_MIN || v > INT32_MAX) {
        error_exit("Read int value is too big to fit in 32 bits.\n", RUNTIME_ERROR_CODE);
    }
    return (int32_t) v;
}

char* __concatString(char* l, char* r) {
    size_t length = 0;
    for (char* c = l; *c != 0; c++) length++;
    for (char* c = r; *c != 0; c++) length++;
    char *data = checked_malloc(length + 1);
    size_t idx = 0;
    for (char* c = l; *c != 0; c++) data[idx++] = *c;
    for (char* c = r; *c != 0; c++) data[idx++] = *c;
    data[length] = 0;
    return data;
}

int __equalString(char* l, char* r) {
    while (*l != 0 && *r != 0) {
        if (*l != *r) return 0;
        l++;
        r++;
    }
    if (*l != *r) return 0;
    return 1;
}

