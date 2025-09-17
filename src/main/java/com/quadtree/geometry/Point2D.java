package com.quadtree.geometry;

public final class Point2D {
    private final double x;
    private final double y;

    public Point2D(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y) ||
                Double.isInfinite(x) || Double.isInfinite(y)) {
            throw new IllegalArgumentException("Значения x и y должны быть конечными!");
        }

        this.x = x;
        this.y = y;
    }

    public double x() { return x; }
    public double y() { return y; }

    public double distanceSquaredTo(double ox, double oy) {
        double dx = x - ox;
        double dy = y - oy;

        return dx * dx + dy * dy;
    }
}