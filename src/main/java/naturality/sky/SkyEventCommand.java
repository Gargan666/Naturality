package naturality.sky;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Operator command for per-dimension visual event overrides. */
public final class SkyEventCommand {
    private SkyEventCommand() {}
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            var root = Commands.literal("skyevent")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
            root.then(Commands.literal("status").executes(context -> status(context.getSource(), context.getSource().getLevel())));
            var event = Commands.argument("event", StringArgumentType.word())
                .suggests((context, builder) -> {
                    var dimension = DimensionArgument.getDimension(context, "dimension");
                    return SharedSuggestionProvider.suggest(SkyEventType.pool(id(dimension)).stream().map(type -> type.id), builder);
                });
            event.then(Commands.argument("strength", IntegerArgumentType.integer(0, 20)).executes(context -> {
                var level = DimensionArgument.getDimension(context, "dimension");
                var type = find(level, StringArgumentType.getString(context, "event"));
                int strength = IntegerArgumentType.getInteger(context, "strength");
                SkyEventWorldData.get(level.getServer()).override(level, type, true, strength);
                String visibility = "";
                if (type == SkyEventType.RAINBOW && strength > 0) {
                    float rain = SkyEvents.rainfall(level);
                    if (!RainbowCycle.validRain(rain))
                        visibility += String.format(Locale.ROOT,
                            " Currently hidden: rain is %.1f/100; Rainbow needs more than 1 and less than 20.", rain);
                    if (SkyEvents.isNight(level)) visibility += " Currently hidden at night.";
                }
                String reply = "Sky event " + type.label + " in " + id(level)
                    + " set to strength " + strength + " (override)." + visibility;
                context.getSource().sendSuccess(() -> Component.literal(reply), true);
                return Command.SINGLE_SUCCESS;
            }));
            event.then(Commands.literal("auto").executes(context -> {
                var level = DimensionArgument.getDimension(context, "dimension");
                var type = find(level, StringArgumentType.getString(context, "event"));
                var data = SkyEventWorldData.get(level.getServer());
                data.override(level, type, false, data.settings(level, type).strength);
                context.getSource().sendSuccess(() -> Component.literal("Sky event " + type.label + " in " + id(level)
                    + " returned to automatic scheduling."), true);
                return Command.SINGLE_SUCCESS;
            }));
            var dimension = Commands.argument("dimension", DimensionArgument.dimension())
                .executes(context -> status(context.getSource(), DimensionArgument.getDimension(context, "dimension")))
                .then(Commands.literal("status").executes(context -> status(context.getSource(), DimensionArgument.getDimension(context, "dimension"))))
                .then(event);
            dispatcher.register(root.then(dimension));
        });
    }
    private static int status(CommandSourceStack source, ServerLevel level) {
        String dimension = id(level);
        var pool = SkyEventType.pool(dimension);
        if (pool.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Sky Events in " + dimension + ": none configured."), false);
            return Command.SINGLE_SUCCESS;
        }
        ServerPlayer observer = source.getEntity() instanceof ServerPlayer player && player.level() == level
            ? player : level.players().isEmpty() ? null : level.players().getFirst();
        var happening = new ArrayList<String>();
        var lines = new StringBuilder("Sky Events in " + dimension);
        if (observer != null && (pool.contains(SkyEventType.AURORA_BOREALIS) || pool.contains(SkyEventType.END_AURORA)))
            lines.append(" (local aurora near ").append(observer.getName().getString()).append(')');
        lines.append(':');
        boolean advance = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER);
        for (var type : pool) {
            var settings = SkyEventWorldData.get(level.getServer()).settings(level, type);
            float value = SkyEvents.strength(level, type);
            boolean active;
            String detail;
            if (type == SkyEventType.END_FLASHES) {
                var cycle = SkyEvents.cycle(level, type);
                active = settings.override ? settings.strength > 0 : cycle != null && cycle.active();
                if (active || value > .01F) happening.add(type.label);
                lines.append('\n').append(type.label).append(": ").append(state(active, value, settings.override))
                    .append(settings.override ? "; override; automatic starts suppressed" : cycle == null
                        ? "; scheduler initializing" : cycle.active()
                        ? "; active for up to " + cycle.remaining() + " more ticks" : "; rolls each quiet tick");
                double roll = SkyEventCycle.END_FLASH_CHANCE_PER_TICK;
                lines.append(". Start chance while quiet: tick ").append(percent(roll))
                    .append(", second ").append(percent(1 - Math.pow(1 - roll, 20)))
                    .append(", minute ").append(percent(1 - Math.pow(1 - roll, 1200))).append('.');
            } else if (type == SkyEventType.METEOR_SHOWER) {
                var cycle = SkyEvents.cycle(level, type);
                active = settings.override ? settings.strength > 0 : cycle != null && cycle.active();
                boolean visible = (active || value > .01F) && SkyEvents.isNight(level);
                if (visible) happening.add(type.label);
                detail = settings.override ? "override; automatic starts suppressed"
                    : cycle == null ? "scheduler initializing"
                    : cycle.active() ? "active for up to " + cycle.remaining() + " more ticks"
                    : "rolls each eligible night tick";
                lines.append('\n').append(type.label).append(": ").append(state(active, value, settings.override))
                    .append(!SkyEvents.isNight(level) ? " (hidden during day)" : "")
                    .append("; ").append(detail).append('.');
                double meteorRoll = SkyEventCycle.START_CHANCE_PER_TICK;
                lines.append(" Start chance while quiet at night: tick ")
                    .append(percent(meteorRoll)).append(", second ")
                    .append(percent(1 - Math.pow(1 - meteorRoll, 20))).append(", minute ")
                    .append(percent(1 - Math.pow(1 - meteorRoll, 1200))).append('.');
            } else if (type == SkyEventType.RAINBOW) {
                var cycle = SkyEvents.rainbowCycle(level);
                float rain = SkyEvents.rainfall(level);
                boolean eligible = !SkyEvents.isNight(level) && RainbowCycle.validRain(rain);
                active = settings.override ? value > 0 : cycle != null && cycle.active() && eligible;
                if (value > .01F) happening.add(type.label);
                double roll = RainbowCycle.startChance(rain);
                lines.append('\n').append(type.label).append(": ").append(state(active, value, settings.override))
                    .append(String.format(Locale.ROOT, "; rain %.1f/100", rain));
                if (SkyEvents.isNight(level)) lines.append("; hidden at night");
                if (!RainbowCycle.validRain(rain)) lines.append("; rain must be between 1 and 20");
                if (settings.override) lines.append("; automatic starts suppressed");
                else if (cycle != null && cycle.active()) lines.append("; active for up to ").append(cycle.remaining()).append(" more ticks");
                lines.append(". Start chance while quiet in daylight: tick ")
                    .append(percent(roll)).append(", second ")
                    .append(percent(1 - Math.pow(1 - roll, 20))).append(", minute ")
                    .append(percent(1 - Math.pow(1 - roll, 1200))).append('.');
            } else {
                var local = observer == null ? null : AuroraEvents.status(level, observer.blockPosition());
                value = settings.override ? settings.strength : local == null ? 0 : local.strength();
                active = settings.override ? settings.strength > 0 : local != null && local.active();
                if (active || value > .01F) happening.add(type.label);
                lines.append('\n').append(type.label).append(": ").append(state(active, value, settings.override));
                if (settings.override) lines.append("; automatic starts suppressed.");
                else if (local == null) lines.append("; no occupied local region; start chance 0% until a player enters.");
                else {
                    double roll = Math.clamp(local.chance(), 0F, 1F);
                    lines.append("; ").append(local.active() ? "active" : "next roll in " + local.nextAttempt() + " ticks")
                        .append(type == SkyEventType.END_AURORA ? ". Fixed End roll: " : ". Climate-adjusted roll: ").append(percent(roll))
                        .append(" every 600 ticks; estimated while idle: tick ").append(percent(roll / 600))
                        .append(", second ").append(percent(roll / 30))
                        .append(", minute ").append(percent(1 - Math.pow(1 - roll, 2))).append('.');
                }
            }
        }
        lines.append("\nCurrently happening here: ").append(happening.isEmpty() ? "none" : String.join(", ", happening));
        if (!advance && !level.dimension().equals(net.minecraft.world.level.Level.END))
            lines.append(". Automatic sky event timers are paused by the weather gamerule");
        String message = lines.toString();
        source.sendSuccess(() -> Component.literal(message), false);
        return Command.SINGLE_SUCCESS;
    }
    private static String state(boolean active, float strength, boolean override) {
        return (active ? "active" : strength > .01F ? "fading" : "quiet")
            + String.format(Locale.ROOT, " (strength %.1f/20%s)", strength, override ? ", override" : "");
    }
    private static String percent(double chance) {
        return String.format(Locale.ROOT, "%.5f%%", chance * 100);
    }
    private static SkyEventType find(ServerLevel level, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String dimension = id(level);
        if (!SkyEventType.supported(dimension))
            throw new SimpleCommandExceptionType(Component.literal("Sky Events are not supported in " + dimension + ".")).create();
        return SkyEventType.pool(dimension).stream().filter(type -> type.id.equals(name)).findFirst()
            .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Event '" + name + "' is not available in " + dimension + ".")).create());
    }
    private static String id(ServerLevel level) { return level.dimension().identifier().toString(); }
}
