package me.stageguard.aruku.util

fun <T, M : Comparable<M>> List<T>.indexFirstBE(
    value: M,
    mapping: (T) -> M
): Int {
    var (left, right) = 0 to size - 1
    var index = -1
    while (left <= right) {
        val middle = left + (right - left) / 2
        if (mapping(get(middle)) >= value) {
            index = middle
            right = middle - 1
        } else {
            left = middle + 1
        }
    }
    return index
}