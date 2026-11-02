#include <stdint.h>
#include <stdio.h>

int32_t calculate_value(int32_t input) {
    return input * 2;
}

int32_t add_values(int32_t a, int32_t b) {
    return a + b;
}

int32_t is_positive(int32_t value) {
    return value > 0;
}

uint16_t get_character(void) {
    return 65;
}

uint16_t echo_character(uint16_t value) {
    return value;
}

int32_t set_render_mode(int32_t mode) {
    printf("render_mode: %d\n", mode);
    fflush(stdout);

    return mode;
}

typedef int (*Callback)(int value);

int execute_callback(Callback callback, int value) {
    return callback(value);
}