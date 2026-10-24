package naturality.client.systems.navigation;

import com.mojang.serialization.MapCodec;
import naturality.systems.weather.framework.WeatherSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Selects supplied barometer frames, with bounded random motion outside the Overworld. */
@org.jspecify.annotations.NullMarked
public final class BarometerProperty implements RangeSelectItemModelProperty {
    public static final MapCodec<BarometerProperty> MAP_CODEC = MapCodec.unit(BarometerProperty::new);
    private final RandomSource random = RandomSource.create();
    private @Nullable ClientLevel lastLevel;
    private long lastTick = Long.MIN_VALUE;
    private float frame;
    private float velocity;

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (level == null && owner != null && owner.level() instanceof ClientLevel ownerLevel) level = ownerLevel;
        if (level == null) return 0;
        if (!level.dimension().equals(Level.OVERWORLD)) return randomFrame(level);
        var weather = WeatherSystem.renderState(level);
        float rain = weather == null ? level.getRainLevel(1) * 25 + level.getThunderLevel(1) * 25 : weather.rain();
        return Math.clamp(rain, 0, 100) * 17 / 100;
    }

    private float randomFrame(ClientLevel level) {
        long tick = level.getGameTime();
        if (lastLevel != level) {
            lastLevel = level;
            lastTick = Long.MIN_VALUE;
            velocity = 0;
        }
        if (lastTick != tick) {
            lastTick = tick;
            // Use linear distance rather than the clock's shortest circular distance.
            velocity = (velocity + (random.nextInt(18) - frame) * .1F) * .9F;
            float next = frame + velocity;
            frame = Math.clamp(next, 0, 17);
            if (next <= 0 || next >= 17) velocity = 0;
        }
        return frame;
    }

    @Override
    public MapCodec<BarometerProperty> type() { return MAP_CODEC; }
}
