package naturality.client.particle;

/** Only droplets launched by a large impact opt out of vanilla's short timer. */
public interface ImpactDroplet {
    void naturality$landBeforeExpiring();
    default void naturality$impactRing(boolean enabled) {}
    default void naturality$waterDroplet() {}
    default void naturality$rainSplash() {}
}


