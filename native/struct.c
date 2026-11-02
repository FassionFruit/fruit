#include <math.h>

typedef struct {
    float x;
    float y;
    float z;
} Vector3d;

float calculate_length(const Vector3d* vector) {
    return sqrtf(
        vector->x * vector->x +
        vector->y * vector->y +
        vector->z * vector->z
    );
}