package naturality.client.particle;

/** Per-particle texture crop and pixel holes for vanilla terrain fragments. */
public interface BlockFragmentPixels {
    int naturality$cropStartX();
    int naturality$cropStartY();
    int naturality$cropWidth();
    int naturality$cropHeight();
    boolean naturality$isPixelRemoved(int x, int y);
}
