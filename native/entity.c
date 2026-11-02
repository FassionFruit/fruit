#include <stdint.h>
#include <stddef.h>

#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#else
#define EXPORT __attribute__((visibility("default")))
#endif

typedef struct {
    float x;
    float y;
} Vector2;

typedef struct {
    Vector2 position;
    Vector2 velocity;
} Entity;

EXPORT void initialize_entity(Entity* entity) {
    entity->position.x = 10.0f;
    entity->position.y = 20.0f;

    entity->velocity.x = 1.0f;
    entity->velocity.y = 2.0f;
}

EXPORT float calculate_entity(Entity* entity) {
    return entity->position.x
         + entity->position.y
         + entity->velocity.x
         + entity->velocity.y;
}

EXPORT void move_entity(Entity* entity, float x, float y) {
    entity->position.x += x;
    entity->position.y += y;
}

EXPORT uint64_t get_entity_size(void) {
    return sizeof(Entity);
}

EXPORT uint64_t get_position_offset(void) {
    return offsetof(Entity, position);
}

EXPORT uint64_t get_velocity_offset(void) {
    return offsetof(Entity, velocity);
}