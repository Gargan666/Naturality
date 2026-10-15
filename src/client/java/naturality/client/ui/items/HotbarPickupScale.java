package naturality.client.ui.items;

/** Keeps the hotbar pickup pop centered on the item. */
public final class HotbarPickupScale {
    private HotbarPickupScale() {}
    public static float centeredOffset(float offsetY, int y) {
        if (offsetY == y + 12) offsetY = y + 8;
        else if (offsetY == -(y + 12)) offsetY = -(y + 8);
        return offsetY;
    }
    public static float uniformScale(float yScale) { return yScale; }
}
