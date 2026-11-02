#include <math.h>
#include <stdint.h>

typedef struct {
    float x;
    float y;
    float z;
} Vector3d;

static Vector3d vector;

Vector3d* create_vector(float x, float y, float z) {
    vector.x = x;
    vector.y = y;
    vector.z = z;

    return &vector;
}

float calculate_length(const Vector3d* vector) {
    return sqrtf(
        vector->x * vector->x +
        vector->y * vector->y +
        vector->z * vector->z
    );
}