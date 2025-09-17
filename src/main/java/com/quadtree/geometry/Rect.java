package com.quadtree.geometry;

public final class Rect {
    private final double minX, minY, maxX, maxY;

    public Rect(double minX, double minY, double maxX, double maxY) {
        if (Double.isNaN(minX) || Double.isNaN(minY) || Double.isNaN(maxX) || Double.isNaN(maxY)) {
            throw new IllegalArgumentException("Какой-то из переданных аргументов не является числом!");
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Нарушен порядок определения величин!");
        }

        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public double minX() { return minX; }
    public double minY() { return minY; }
    public double maxX() { return maxX; }
    public double maxY() { return maxY; }
    public double width() { return maxX - minX; }
    public double height() { return maxY - minY; }
    public double centerX() { return (minX + maxX) * 0.5; }
    public double centerY() { return (minY + maxY) * 0.5; }

    public Rect expandToFit(double x, double y) {
        double nMinX = Math.min(minX, x);
        double nMinY = Math.min(minY, y);
        double nMaxX = Math.max(maxX, x);
        double nMaxY = Math.max(maxY, y);

        if (nMinX == minX && nMinY == minY && nMaxX == maxX && nMaxY == maxY) {
            return this;
        }

        return new Rect(nMinX, nMinY, nMaxX, nMaxY);
    }

    public static Rect ofCenter(double cx, double cy, double halfWidth, double halfHeight) {
        if (halfWidth < 0 || halfHeight < 0) {
            throw new IllegalArgumentException("Размер половин должен быть >= 0!");
        }
        return new Rect(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
    }

    public boolean containsPoint(double x, double y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public boolean intersects(Rect other) {
        return this.minX <= other.maxX && this.maxX >= other.minX &&
                this.minY <= other.maxY && this.maxY >= other.minY;
    }

    public double distanceSquaredToPoint(double x, double y) {
        double dx = 0.0;
        if (x < minX) {
            dx = minX - x;
        }
        else if (x > maxX) {
            dx = x - maxX;
        }

        double dy = 0.0;
        if (y < minY) {
            dy = minY - y;
        }
        else if (y > maxY) {
            dy = y - maxY;
        }

        return dx * dx + dy * dy;
    }

    @Override public String toString() {
        return "Rect[" + minX + "," + minY + " .. " + maxX + "," + maxY + "]";
    }
}
