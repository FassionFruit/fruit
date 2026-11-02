#include <stdint.h>
#include <stdio.h>

const char* get_message(void) {
    return "Hello from native!";
}

const char* echo_string(const char* value) {
    return value;
}

int32_t get_string_length(const char* value) {
    int32_t length = 0;

    while (value[length] != '\0') {
        length++;
    }

    return length;
}

void* get_pointer(void) {
    static int32_t value = 1234;
    return &value;
}