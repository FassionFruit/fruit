#include <stdint.h>

typedef struct {
    void* context;

    int64_t (*get_score)(void*);
    float (*get_speed)(void*);
    double (*calculate)(void*, int, double);
    int16_t (*get_grade)(void*);
    int32_t (*is_alive)(void*);
    int32_t (*get_mode)(void*);
} PlayerTypes;

int test_player_types(PlayerTypes* player) {
    if (player->get_score(player->context) != 9876543210LL) {
        return 1;
    }

    if (player->get_speed(player->context) != 4.5f) {
        return 2;
    }

    if (player->calculate(player->context, 10, 2.5) != 25.0) {
        return 3;
    }

    if (player->get_grade(player->context) != 'A') {
        return 4;
    }

    if (player->is_alive(player->context) != 1) {
        return 5;
    }

    if (player->get_mode(player->context) != 1) {
        return 6;
    }

    return 0;
}