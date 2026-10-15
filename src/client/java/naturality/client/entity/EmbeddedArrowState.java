package naturality.client.entity;

@org.jspecify.annotations.NullMarked
public interface EmbeddedArrowState {
    int naturality$arrowCount();
    void naturality$setArrowCount(int count);
    int naturality$arrowSeed();
    void naturality$setArrowSeed(int seed);
    java.util.List<MobArrowImpacts.Impact> naturality$impacts();
    void naturality$setImpacts(java.util.List<MobArrowImpacts.Impact> hits);
    org.joml.Matrix4f naturality$origin();
}
