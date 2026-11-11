#include <stddef.h>

typedef struct {
    int x;
    int y;
} Point;

typedef union {
    int integer;
    float decimal;
} Value;

const char *native_message(void) {
    return "Hello from C";
}

int native_string_length(const char *value) {
    int length = 0;

    if (value == NULL) {
        return 0;
    }

    while (value[length] != '\0') {
        length++;
    }

    return length;
}

int native_add(int left, int right) {
    return left + right;
}

int native_increment(int *value) {
    if (value == NULL) {
        return -1;
    }

    (*value)++;
    return *value;
}

int native_point_sum(const Point *point) {
    return point == NULL ? 0 : point->x + point->y;
}

int native_value_integer(const Value *value) {
    return value == NULL ? 0 : value->integer;
}

int native_apply(int (*callback)(int), int value) {
    return callback == NULL ? 0 : callback(value);
}

int native_mode(int mode) {
    return mode;
}
