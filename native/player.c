typedef struct {
    void* context;

    void (*show_death_animation)(void* context);
    int (*get_health)(void* context);
    int (*add_health)(void* context, int amount);
} Player;

int test_player(Player* player) {
    int health = player->get_health(player->context);

    player->add_health(player->context, 50);
    player->show_death_animation(player->context);

    return health;
}