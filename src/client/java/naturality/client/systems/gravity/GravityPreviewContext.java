package naturality.client.systems.gravity;



/** GUI entity previews own their orientation and cursor-driven head angles. */
@org.jspecify.annotations.NullMarked
public final class GravityPreviewContext {
    private static final ThreadLocal<Boolean> INVENTORY=ThreadLocal.withInitial(()->false);
    private GravityPreviewContext() {}
    public static boolean active(){return INVENTORY.get();}
    public static boolean enter(){boolean previous=active();INVENTORY.set(true);return previous;}
    public static void restore(boolean previous){INVENTORY.set(previous);}

    public interface EndGravityRenderState {
        float naturality$inversion();
        void naturality$inversion(float value);
    }
}
