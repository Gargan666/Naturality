package naturality.util;

/** Stable legacy coordinate hash; existing fire patterns must survive API migrations. */
public final class BlockSeed {
    private BlockSeed() { }

    public static long of(int x, int y, int z) {
        long seed = x * 3129871 ^ z * 116129781L ^ y;
        seed = seed * seed * 42317861L + seed * 11L;
        return seed >> 16;
    }
}
