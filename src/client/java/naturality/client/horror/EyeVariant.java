package naturality.client.horror;

/** Every dot still samples exactly one white texture pixel. */
public enum EyeVariant {
    NORMAL(.09F, -.14F, 0, .14F, 0),
    UNEVEN(.09F, -.16F, -.055F, .12F, .055F),
    CROOKED(.09F, -.10F, .065F, .20F, -.025F),
    SMALL_WIDE(.045F, -.26F, 0, .26F, 0);

    public final float size, leftX, leftY, rightX, rightY;
    EyeVariant(float size, float leftX, float leftY, float rightX, float rightY) {
        this.size = size; this.leftX = leftX; this.leftY = leftY; this.rightX = rightX; this.rightY = rightY;
    }
}
