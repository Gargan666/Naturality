package naturality.weather;

/** Per-entity rotation, synchronized independently of dimension weather. */
public interface DistortionState {
    float naturality$distortion(boolean render);
}
