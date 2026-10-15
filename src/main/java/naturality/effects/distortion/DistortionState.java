package naturality.effects.distortion;

/** Per-entity rotation, synchronized independently of dimension weather. */
@org.jspecify.annotations.NullMarked
public interface DistortionState {
    float naturality$distortion(boolean render);
}
