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

typedef struct {
    float x;
    float y;
} Vector2;

typedef struct {
    Vector2 points[3];
    int count;
} Polygon;

float calculate_polygon_sum(Polygon* polygon) {
    float sum = 0;

    for (int i = 0; i < polygon->count; i++) {
        sum += polygon->points[i].x;
        sum += polygon->points[i].y;
    }

    return sum;
}

typedef struct {
    int id;
    float* values;
    int count;
} Data;

float calculate_data_sum(Data* data) {
    float sum = 0;

    for (int i = 0; i < data->count; i++) {
        sum += data->values[i];
    }

    return sum;
}