package com.nirenr;


public class Point {
    public int x;
    public int y;
    public int t;



    public Point(int x, int y,int t) {
        this.x = x;
        this.y = y;
        this.t = t;
    }
    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point src) {
        this.x = src.x;
        this.y = src.y;
    }

    public void set(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public final void negate() {
        x = -x;
        y = -y;
    }

    public final void offset(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public final boolean equals(int x, int y) {
        return this.x == x && this.y == y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Point point = (Point) o;

        if (x != point.x) return false;
        if (y != point.y) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        return result;
    }

    @Override
    public String toString() {
        return "Point(" + x + ", " + y + ": " + t +")";
    }

}
