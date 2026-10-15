package naturality.client.entity;
public final class CrystalVisuals {
    private CrystalVisuals() { }
    public static float bob(float ticks) {
        float wave=(float)(Math.sin(ticks*.1)*.5+.5);
        return -1.05F+(wave+wave*wave)*.22F;
    }
    public static double centerHeight(float ticks) { return 2+bob(ticks); }
}

