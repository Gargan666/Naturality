package naturality.weather;

/** Java Random's sequence with an explicit, persistable 48-bit state. */
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
}
