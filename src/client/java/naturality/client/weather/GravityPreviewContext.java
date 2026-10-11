package naturality.client.weather;

/** GUI entity previews own their orientation and cursor-driven head angles. */
public final class GravityPreviewContext {
    private static final ThreadLocal<Boolean> INVENTORY=ThreadLocal.withInitial(()->false);
    private GravityPreviewContext() {}
    public static boolean active(){return INVENTORY.get();}
    public static boolean enter(){boolean previous=active();INVENTORY.set(true);return previous;}
    public static void restore(boolean previous){INVENTORY.set(previous);}
}