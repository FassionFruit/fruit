use std::ffi::c_char;

#[repr(C)]
pub struct Point {
    pub x: i32,
    pub y: i32,
}

#[no_mangle]
pub extern "C" fn add(left: i32, right: i32) -> i32 {
    left + right
}

#[no_mangle]
pub extern "C" fn multiply(left: i32, right: i32) -> i32 {
    left * right
}

#[no_mangle]
pub extern "C" fn subtract(left: i32, right: i32) -> i32 {
    left - right
}

#[no_mangle]
pub extern "C" fn get_version() -> *const c_char {
    c"0.5.0".as_ptr()
}

#[no_mangle]
pub unsafe extern "C" fn rust_point_sum(point: *const Point) -> i32 {
    if point.is_null() {
        return 0;
    }

    (*point).x + (*point).y
}

#[repr(C)]
pub struct PointArray {
    pub values: [i32; 3],
}

#[no_mangle]
pub unsafe extern "C" fn rust_array_sum(array: *const PointArray) -> i32 {
    if array.is_null() {
        return 0;
    }

    (*array).values.iter().sum()
}
