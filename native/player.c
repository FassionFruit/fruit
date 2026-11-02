#include <stdio.h>

typedef struct {
    void* context;

    void (*show_death_animation)(void* context);
    int (*get_health)(void* context);
    int (*add_health)(void* context, int amount);
} Player;

int test_player(Player* player) {
    int health = player->get_health(player->context);

    printf("(C) player's health: %ld\n", (long) health);
    fflush(stdout);

    player->add_health(player->context, 50);
    player->show_death_animation(player->context);

    return health;
}

int test_player_exception(Player* player) {
    int health = player->get_health(player->context);

    player->show_death_animation(player->context);

    return health;
}

int test_player_concurrency(Player* player) {
    return player->get_health(player->context);
}
