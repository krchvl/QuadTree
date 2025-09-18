package com.quadtree.api;

import com.quadtree.geometry.Circle;
import com.quadtree.geometry.Rect;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public interface SpatialIndex<T> {
    boolean insert(T item);
    int insertAll(Collection<T> items);
    boolean remove(T item);
    int removeIf(Predicate<T> predicate);
    boolean update(T item);
    List<T> query(Rect range);
    List<T> query(Circle range);
    List<T> queryKnn(double x, double y, int k);
    void clear();
    int size();
    Rect bounds();
}