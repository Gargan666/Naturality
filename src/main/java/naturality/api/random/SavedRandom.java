package naturality.api.random;



/** Java Random's sequence with an explicit, persistable 48-bit state. */
@org.jspecify.annotations.NullMarked
public final class SavedRandom extends java.util.Random {
    private static final long serialVersionUID=1L;
    private long state;
    public SavedRandom(long seed) { super(0); state=(seed ^ 0x5DEECE66DL)&((1L<<48)-1); }
    @Override protected int next(int bits) {
        state=(state*0x5DEECE66DL+0xBL)&((1L<<48)-1);
        return (int)(state >>> (48-bits));
    }
    public long state() { return state; }
    public void restore(long saved) { state=saved&((1L<<48)-1); }

    /** Stable legacy coordinate hash; existing fire patterns must survive API migrations. */
    public static final class BlockSeed {
        private BlockSeed() { }

        public static long of(int x, int y, int z) {
            long seed = x * 3129871 ^ z * 116129781L ^ y;
            seed = seed * seed * 42317861L + seed * 11L;
            return seed >> 16;
        }
    }
}
