#include <inttypes.h>
#include <stdlib.h>
#include <limits.h>
#include <stdio.h>
#include <errno.h>

static const size_t INIT_STRING_BUFFER_SIZE = 8;

static const int RUNTIME_ERROR_CODE = 1;

typedef struct string_t {
    char *data;
    size_t length;
} string_t;

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

void error() {
    error_exit("runtime error\n", RUNTIME_ERROR_CODE);
}

void printInt(int32_t value) {
    fprintf(stdout, "%" PRId32 "\n", value);
}

void printString(string_t value) {
    fprintf(stdout, "%s", value.data);
}

string_t readString() {
    size_t size = INIT_STRING_BUFFER_SIZE;
    char *data = checked_malloc(size + 1);
    size_t length = 0;
    int c;

    while ((c = fgetc(stdin)) != EOF && c != '\n') {
        if (length == size) {
            size *= 2;
            data = checked_malloc(size + 1);
        }
        *(data + length) = (char) c;
        length += 1;
    }
    *(data + length) = '\0';
    string_t result = {data, length};
    return result;
}

int32_t readInt() {
    char *data = readString().data;
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

