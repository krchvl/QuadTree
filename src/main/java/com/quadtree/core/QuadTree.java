package com.quadtree.core;

import com.quadtree.api.PointAdapter;
import com.quadtree.api.SpatialIndex;
import com.quadtree.geometry.Circle;
import com.quadtree.geometry.Rect;

import java.util.*;
import java.util.function.Predicate;

public final class QuadTree<T> implements SpatialIndex<T> {

    public static final class Builder<T> {
        private Rect bounds;
        private int capacity = 16;
        private int maxDepth = 16;
        private boolean autoExpand = true;
        private boolean allowDuplicates = true;
        private PointAdapter<T> adapter;

        public Builder<T> bounds(Rect bounds) {
            this.bounds = Objects.requireNonNull(bounds);
            return this;
        }
        public Builder<T> capacity(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("Мощность должна быть > 0!");
            this.capacity = capacity;
            return this;
        }
        public Builder<T> maxDepth(int maxDepth) {
            if (maxDepth < 1) throw new IllegalArgumentException("Максимальная глубина должна быть >= 1!");
            this.maxDepth = maxDepth;
            return this;
        }
        public Builder<T> autoExpand(boolean autoExpand) { this.autoExpand = autoExpand;
            return this;
        }
        public Builder<T> allowDuplicates(boolean allowDuplicates) { this.allowDuplicates = allowDuplicates;
            return this;
        }
        public Builder<T> adapter(PointAdapter<T> adapter) { this.adapter = Objects.requireNonNull(adapter);
            return this;
        }

        public QuadTree<T> build() {
            if (bounds == null) throw new IllegalStateException("Граница должна быть задана!");
            if (adapter == null) throw new IllegalStateException("Адаптер для работы с координатами должен быть задан!");
            return new QuadTree<>(bounds, capacity, maxDepth, autoExpand, allowDuplicates, adapter);
        }
    }

    private static final double EPS = 1e-12;

    private final int capacity;
    private final int maxDepth;
    private final boolean autoExpand;
    private final boolean allowDuplicates;
    private final PointAdapter<T> adapter;

    private Node<T> root;
    private int size;

    private QuadTree(Rect bounds, int capacity, int maxDepth, boolean autoExpand,
                     boolean allowDuplicates, PointAdapter<T> adapter) {
        this.capacity = capacity;
        this.maxDepth = maxDepth;
        this.autoExpand = autoExpand;
        this.allowDuplicates = allowDuplicates;
        this.adapter = adapter;
        this.root = new Node<>(bounds, 0);
        this.size = 0;
    }

    @Override
    public boolean insert(T item) {
        Objects.requireNonNull(item);
        adapter.validate(item);
        double x = adapter.x(item);
        double y = adapter.y(item);

        if (!root.bounds.containsPoint(x, y)) {
            if (!autoExpand) {
                return false;
            }

            Rect nb = root.bounds.expandToFit(x, y);
            rebuild(nb);
        }

        boolean inserted = insertInto(root, item, x, y);
        if (inserted) {
            size++;
        }
        return inserted;
    }

    @Override
    public int insertAll(Collection<T> items) {
        int c = 0;

        for (T i : items) {
            if (insert(i)) {
                c++;
            }
        }
        return c;
    }

    @Override
    public boolean remove(T item) {
        Objects.requireNonNull(item);
        double x = adapter.x(item);
        double y = adapter.y(item);
        boolean removed = removeFrom(root, item, x, y);
        if (removed) {
            size--;
        }
        return removed;
    }

    @Override
    public int removeIf(Predicate<T> predicate) {
        Objects.requireNonNull(predicate);
        int before = size;
        size -= removeIfRec(root, predicate);
        return before - size;
    }

    @Override
    public boolean update(T item) {
        boolean removed = remove(item);
        boolean inserted = insert(item);
        return removed && inserted;
    }

    @Override
    public List<T> query(Rect range) {
        Objects.requireNonNull(range);
        List<T> out = new ArrayList<>();
        queryRecRect(root, range, out);
        return out;
    }

    @Override
    public List<T> query(Circle range) {
        Objects.requireNonNull(range);
        List<T> out = new ArrayList<>();
        queryRecCircle(root, range, out);
        return out;
    }

    @Override
    public List<T> queryKnn(double x, double y, int k) {
        if (k <= 0) return List.of();
        PriorityQueue<NodeDist<T>> nodeQ = new PriorityQueue<>(Comparator.comparingDouble(n -> n.dist2));
        nodeQ.add(new NodeDist<>(root, root.bounds.distanceSquaredToPoint(x, y)));

        PriorityQueue<ItemDist<T>> best = new PriorityQueue<>((a, b) -> Double.compare(b.dist2, a.dist2));

        while (!nodeQ.isEmpty()) {
            NodeDist<T> nd = nodeQ.poll();
            if (best.size() == k && nd.dist2 > best.peek().dist2) {
                continue;
            }
            Node<T> n = nd.node;
            if (n.isLeaf()) {
                for (T it : n.items) {
                    double dx = adapter.x(it) - x, dy = adapter.y(it) - y;
                    double d2 = dx*dx + dy*dy;
                    if (best.size() < k) {
                        best.add(new ItemDist<>(it, d2));
                    } else if (d2 < best.peek().dist2) {
                        best.poll();
                        best.add(new ItemDist<>(it, d2));
                    }
                }
            } else {
                for (Node<T> ch : n.children()) {
                    if (ch != null) {
                        double cd2 = ch.bounds.distanceSquaredToPoint(x, y);
                        if (best.size() < k || cd2 <= best.peek().dist2) {
                            nodeQ.add(new NodeDist<>(ch, cd2));
                        }
                    }
                }
            }
        }
        List<T> res = new ArrayList<>(best.size());
        while (!best.isEmpty()) {
            res.add(best.poll().item);
        }
        return res;
    }

    @Override
    public void clear() {
        this.root = new Node<>(root.bounds, 0);
        this.size = 0;
    }

    @Override
    public int size() { return size; }

    @Override
    public Rect bounds() { return root.bounds; }


    private static final class Node<T> {
        final Rect bounds;
        final int depth;
        List<T> items;
        Node<T> nw, ne, sw, se;

        Node(Rect bounds, int depth) {
            this.bounds = bounds;
            this.depth = depth;
            this.items = new ArrayList<>();
        }

        boolean isLeaf() { return nw == null; }

        Node<T>[] children() {
            @SuppressWarnings("unchecked")
            Node<T>[] arr = (Node<T>[]) new Node<?>[] { nw, ne, sw, se };
            return arr;
        }
    }

    private boolean insertInto(Node<T> node, T item, double x, double y) {
        if (!node.bounds.containsPoint(x, y)) return false;

        if (node.isLeaf()) {
            if (!allowDuplicates) {
                for (T it : node.items) {
                    if (Objects.equals(it, item)) {
                        return false;
                    }
                }
            }
            node.items.add(item);
            if (node.items.size() > capacity && node.depth < maxDepth) {
                subdivide(node);
                Iterator<T> it = node.items.iterator();
                while (it.hasNext()) {
                    T cur = it.next();
                    double cx = adapter.x(cur), cy = adapter.y(cur);
                    Node<T> child = chooseChild(node, cx, cy);
                    if (child != null) {
                        child.items.add(cur);
                        it.remove();
                    }
                }
            }
            return true;
        } else {
            Node<T> child = chooseChild(node, x, y);
            if (child != null) {
                return insertInto(child, item, x, y);
            } else {
                if (!allowDuplicates) {
                    for (T it : node.items) {
                        if (Objects.equals(it, item)) {
                            return false;
                        }
                    }
                }
                node.items.add(item);
                return true;
            }
        }
    }

    private void subdivide(Node<T> node) {
        double mx = node.bounds.centerX();
        double my = node.bounds.centerY();
        Rect b = node.bounds;
        node.nw = new Node<>(new Rect(b.minX(), my, mx, b.maxY()), node.depth + 1);
        node.ne = new Node<>(new Rect(mx, my, b.maxX(), b.maxY()), node.depth + 1);
        node.sw = new Node<>(new Rect(b.minX(), b.minY(), mx, my), node.depth + 1);
        node.se = new Node<>(new Rect(mx, b.minY(), b.maxX(), my), node.depth + 1);
    }

    private Node<T> chooseChild(Node<T> node, double x, double y) {
        double mx = node.bounds.centerX();
        double my = node.bounds.centerY();

        if (Math.abs(x - mx) <= EPS || Math.abs(y - my) <= EPS) {
            return null;
        }

        if (x < mx) {
            if (y >= my) {
                return node.nw;
            }
            else {
                return node.sw;
            }
        } else {
            if (y >= my) {
                return node.ne;
            }
            else {
                return node.se;
            }
        }
    }

    private boolean removeFrom(Node<T> node, T item, double x, double y) {
        if (!node.bounds.containsPoint(x, y)) {
            return false;
        }

        if (!node.items.isEmpty()) {
            List<T> list = node.items;
            for (int i = 0; i < list.size(); i++) {
                if (Objects.equals(list.get(i), item)) {
                    list.remove(i);
                    return true;
                }
            }
        }
        if (node.isLeaf()) {
            return false;
        }

        Node<T> child = chooseChild(node, x, y);
        if (child != null) {
            return removeFrom(child, item, x, y);
        } else {
            return false;
        }
    }

    private int removeIfRec(Node<T> node, Predicate<T> predicate) {
        int removed = 0;
        if (!node.items.isEmpty()) {
            Iterator<T> it = node.items.iterator();
            while (it.hasNext()) {
                if (predicate.test(it.next())) {
                    it.remove();
                    removed++;
                }
            }
        }
        if (!node.isLeaf()) {
            if (node.nw != null) removed += removeIfRec(node.nw, predicate);
            if (node.ne != null) removed += removeIfRec(node.ne, predicate);
            if (node.sw != null) removed += removeIfRec(node.sw, predicate);
            if (node.se != null) removed += removeIfRec(node.se, predicate);
        }
        return removed;
    }

    private void queryRecRect(Node<T> node, Rect range, List<T> out) {
        if (!node.bounds.intersects(range)) {
            return;
        }

        if (!node.items.isEmpty()) {
            for (T it : node.items) {
                double x = adapter.x(it), y = adapter.y(it);
                if (range.containsPoint(x, y)) {
                    out.add(it);
                }
            }
        }
        if (!node.isLeaf()) {
            queryRecRect(node.nw, range, out);
            queryRecRect(node.ne, range, out);
            queryRecRect(node.sw, range, out);
            queryRecRect(node.se, range, out);
        }
    }

    private void queryRecCircle(Node<T> node, Circle range, List<T> out) {
        if (!range.intersects(node.bounds)) {
            return;
        }

        if (!node.items.isEmpty()) {
            for (T it : node.items) {
                double x = adapter.x(it), y = adapter.y(it);
                if (range.containsPoint(x, y)) {
                    out.add(it);
                }
            }
        }
        if (!node.isLeaf()) {
            queryRecCircle(node.nw, range, out);
            queryRecCircle(node.ne, range, out);
            queryRecCircle(node.sw, range, out);
            queryRecCircle(node.se, range, out);
        }
    }

    private void rebuild(Rect newBounds) {
        List<T> all = new ArrayList<>(size);
        collectAll(root, all);
        this.root = new Node<>(newBounds, 0);
        this.size = 0;
        for (T it : all) insert(it);
    }

    private void collectAll(Node<T> node, List<T> out) {
        out.addAll(node.items);
        if (!node.isLeaf()) {
            collectAll(node.nw, out);
            collectAll(node.ne, out);
            collectAll(node.sw, out);
            collectAll(node.se, out);
        }
    }

    private static final class NodeDist<T> {
        final Node<T> node;
        final double dist2;
        NodeDist(Node<T> node, double dist2) { this.node = node; this.dist2 = dist2; }
    }

    private static final class ItemDist<T> {
        final T item;
        final double dist2;
        ItemDist(T item, double dist2) { this.item = item; this.dist2 = dist2; }
    }
}