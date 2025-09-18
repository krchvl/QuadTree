package com.quadtree.geometry;

public final class Circle {
    private final double cx, cy, r;
    private final double r2;

    public Circle(double cx, double cy, double r) {
        if (r < 0) {
            throw new IllegalArgumentException("Радиус должен быть >= 0!");
        }
        if (!Double.isFinite(cx) || !Double.isFinite(cy)) {
            throw new IllegalArgumentException("Координаты центра должны быть конечными!");
        }

        this.cx = cx;
        this.cy = cy;
        this.r = r;
        this.r2 = r*r;
    }

    public double cx() { return cx; }
    public double cy() { return cy; }
    public double r() { return r; }

    public boolean containsPoint(double x, double y) {
        double dx = x - cx;
        double dy = y - cy;
        return dx * dx + dy * dy <= r2;
    }

    public boolean intersects(Rect rect) {
        return rect.distanceSquaredToPoint(cx, cy) <= r2;
    }
}
