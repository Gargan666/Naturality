package naturality.client.api.audio;

/** Audio-thread control for combined weather shelter and listener immersion. */
public interface WeatherAudioFilter {
    void naturality$setEnvironmentAmounts(float indoor, float underwater);
}
