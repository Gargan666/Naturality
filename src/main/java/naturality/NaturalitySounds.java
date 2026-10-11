package naturality;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class NaturalitySounds {
    public static final SoundEvent CAVE_AMBIENCE = register("cave_ambience");
    public static final java.util.List<SoundEvent> END_AMBIENCE_CLIPS = java.util.stream.IntStream.rangeClosed(1, 5)
        .mapToObj(index -> {
            var id = Naturality.id("end_ambience_" + index);
            return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
        }).toList();
    public static final SoundEvent END_AMBIENCE = Registry.register(
        BuiltInRegistries.SOUND_EVENT, Naturality.id("end_ambience"),
        SoundEvent.createVariableRangeEvent(Naturality.id("end_ambience")));
    public static final SoundEvent WIND_WEAK = Registry.register(BuiltInRegistries.SOUND_EVENT,
        Naturality.id("wind_weak"), SoundEvent.createVariableRangeEvent(Naturality.id("wind_weak")));
    public static final SoundEvent WIND_STRONG = Registry.register(BuiltInRegistries.SOUND_EVENT,
        Naturality.id("wind_strong"), SoundEvent.createVariableRangeEvent(Naturality.id("wind_strong")));
    public static final SoundEvent PORTAL_OPEN = Registry.register(
        BuiltInRegistries.SOUND_EVENT, Naturality.id("portal_open"),
        SoundEvent.createVariableRangeEvent(Naturality.id("portal_open")));

    public static final SoundEvent WATER_SPLASH_LOUD = Registry.register(
        BuiltInRegistries.SOUND_EVENT, Naturality.id("watersplash_loud"),
        SoundEvent.createVariableRangeEvent(Naturality.id("watersplash_loud")));

    public static final SoundEvent LIRESTONE_BREAK = register("block.lirestone.break");
    public static final SoundEvent LIRESTONE_STEP = register("block.lirestone.step");
    public static final SoundEvent LIRESTONE_PLACE = register("block.lirestone.place");
    public static final net.minecraft.world.level.block.SoundType LIRESTONE =
        new net.minecraft.world.level.block.SoundType(1.0F, 1.0F,
            LIRESTONE_BREAK, LIRESTONE_STEP, LIRESTONE_PLACE, LIRESTONE_BREAK, LIRESTONE_STEP);

    public static final SoundEvent ECHO_CORAL_BREAK = register("block.echo_coral.break");
    public static final SoundEvent ECHO_CORAL_STEP = register("block.echo_coral.step");
    public static final SoundEvent ECHO_CORAL_PLACE = register("block.echo_coral.place");
    public static final net.minecraft.world.level.block.SoundType ECHO_CORAL =
        new net.minecraft.world.level.block.SoundType(1.0F, 1.0F,
            ECHO_CORAL_BREAK, ECHO_CORAL_STEP, ECHO_CORAL_PLACE, ECHO_CORAL_BREAK, ECHO_CORAL_STEP);

    private static SoundEvent register(String name) {
        var id = Naturality.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    private NaturalitySounds() {}

    public static void initialize() {
        // Load and register the sound before the server can open a portal.
    }
}

