package com.quadtree.api;

public interface PointAdapter<T> {
    double x(T item);
    double y(T item);

    default void validate(T item) {
        double x = x(item);
        double y = y(item);
        if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y)) {
            throw new IllegalArgumentException("Координаты должны быть конечными!");
        }
    }
}