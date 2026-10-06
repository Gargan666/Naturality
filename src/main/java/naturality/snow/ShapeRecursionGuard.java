package naturality.snow;

/** Prevent fitted fire and snow geometry from asking for each other's shapes. */
public final class ShapeRecursionGuard {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[2]);
    private ShapeRecursionGuard() { }

    public static boolean enterFire() {
        var depth = DEPTH.get();
        if (depth[1] != 0) return false;
        depth[0]++;
        return true;
    }
    public static void exitFire() {
        var depth = DEPTH.get();
        --depth[0];
    }
    public static boolean enterSnow() {
        var depth = DEPTH.get();
        if (depth[0] != 0) return false;
        depth[1]++;
        return true;
    }
    public static void exitSnow() {
        var depth = DEPTH.get();
        --depth[1];
    }
    public static boolean active() {
        var depth = DEPTH.get();
        boolean active = depth[0] != 0 || depth[1] != 0;
        return active;
    }
    public static boolean fireActive() { return DEPTH.get()[0] != 0; }
}
