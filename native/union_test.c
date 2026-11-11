#include <stddef.h>

typedef union NativeValue {
    int integer;
    double decimal;
    char bytes[16];
} NativeValue;

size_t native_value_size(void) {
    return sizeof(NativeValue);
}

size_t native_value_alignment(void) {
    return _Alignof(NativeValue);
}

void native_value_set_integer(NativeValue* value, int number) {
    value->integer = number;
}

int native_value_get_integer(NativeValue* value) {
    return value->integer;
}

void native_value_set_decimal(NativeValue* value, double number) {
    value->decimal = number;
}

double native_value_get_decimal(NativeValue* value) {
    return value->decimal;
}