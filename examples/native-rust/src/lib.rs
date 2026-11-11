#[repr(C)]
pub struct Point {
    pub x: i32,
    pub y: i32,
}

#[no_mangle]
pub extern "C" fn rust_add(left: i32, right: i32) -> i32 {
    left + right
}

#[no_mangle]
pub unsafe extern "C" fn rust_point_sum(point: *const Point) -> i32 {
    if point.is_null() {
        return 0;
    }

    (*point).x + (*point).y
}
