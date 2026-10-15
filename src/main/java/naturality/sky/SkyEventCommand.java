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
                if (strength > type.maxStrength()) throw new SimpleCommandExceptionType(
                    Component.literal(type.label + " strength must be between 0 and " + type.maxStrength())).create();
                SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer())).override(level, type, true, strength);
                String visibility = "";
                if (type == SkyEventType.PROXIMITY && strength > 0) {
                    visibility = " Planet: " + ProximityCycle.PLANETS[strength - 1] + "."
                        + (!SkyEvents.isNight(level) ? " Currently hidden during daytime." : "");
                }
                if (type == SkyEventType.RAINBOW && strength > 0) {
                    float rain = SkyEvents.rainfall(level);
                    if (!RainbowCycle.validRain(rain))
                        visibility += String.format(Locale.ROOT,
                            " Currently hidden: rain is %.1f/100; Rainbow needs more than 1 and less than 20.", rain);
                    if (SkyEvents.isNight(level)) visibility += " Currently hidden at night.";
                }
                String reply = "Sky event " + type.label + " in " + id(level)
                    + (type == SkyEventType.BLOOD_MOON ? " set " + (strength > 0 ? "on" : "off")
                        : " set to strength " + strength) + " (override)." + visibility;
                context.getSource().sendSuccess(() -> Component.literal(reply), true);
                return Command.SINGLE_SUCCESS;
            }));
            for (String toggle : java.util.List.of("on", "off")) {
                event.then(Commands.literal(toggle).executes(context -> {
                    var level = DimensionArgument.getDimension(context, "dimension");
                    var type = find(level, StringArgumentType.getString(context, "event"));
                    if (type != SkyEventType.BLOOD_MOON) throw new SimpleCommandExceptionType(
                        Component.literal("Use a numeric strength for " + type.label + ".")).create();
                    SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer())).override(level, type, true, toggle.equals("on") ? 1 : 0);
                    context.getSource().sendSuccess(() -> Component.literal("Blood Moon " + toggle + " (override)."), true);
                    return Command.SINGLE_SUCCESS;
                }));
            }
            event.then(Commands.literal("auto").executes(context -> {
                var level = DimensionArgument.getDimension(context, "dimension");
                var type = find(level, StringArgumentType.getString(context, "event"));
                var data = SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer()));
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
            source.sendSuccess(() -> Component.literal("Sky Events: " + dimension + " — none."), false);
            return Command.SINGLE_SUCCESS;
        }
        ServerPlayer observer = source.getEntity() instanceof ServerPlayer player && player.level() == level
            ? player : level.players().isEmpty() ? null : level.players().getFirst();
        var happening = new ArrayList<String>();
        var lines = new StringBuilder("Sky Events: ").append(dimension);
        boolean night = SkyEvents.isNight(level);
        for (var type : pool) {
            var settings = SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer())).settings(level, type);
            float value = SkyEvents.strength(level, type);
            boolean active;
            boolean visible;
            String extra = "";
            String chance;
            if (type == SkyEventType.PROXIMITY) {
                var cycle = SkyEvents.proximityCycle(level);
                int planet = Math.clamp((int)value, 0, 7);
                active = planet > 0;
                visible = active && night;
                if (active) extra += "; " + ProximityCycle.PLANETS[planet - 1];
                if (!settings.override && active && cycle != null) extra += "; " + duration(cycle.remaining()) + " left";
                if (!night) extra += "; hidden: day";
                chance = percent(ProximityCycle.START_CHANCE) + "/night";
            } else if (type == SkyEventType.BLOOD_MOON) {
                active = value > 0;
                visible = active && night;
                if (!night) extra += "; hidden: day";
                else if (active && !settings.override) extra += "; until dawn";
                chance = percent(BloodMoonCycle.START_CHANCE) + "/night";
            } else if (type == SkyEventType.METEOR_SHOWER || type == SkyEventType.END_FLASHES) {
                var cycle = SkyEvents.cycle(level, type);
                active = settings.override ? settings.strength > 0 : cycle != null && cycle.active();
                visible = (active || value > .01F) && (type == SkyEventType.END_FLASHES || night);
                if (!settings.override && active && cycle != null) extra += "; " + duration(cycle.remaining()) + " left";
                if (type == SkyEventType.METEOR_SHOWER && !night) extra += "; hidden: day";
                double roll = type == SkyEventType.END_FLASHES ? SkyEventCycle.END_FLASH_CHANCE_PER_TICK : SkyEventCycle.START_CHANCE_PER_TICK;
                chance = percent(1 - Math.pow(1 - roll, 1200)) + "/min" + (type == SkyEventType.METEOR_SHOWER ? " (night)" : "");
                if (type == SkyEventType.METEOR_SHOWER) extra += "; boost " + percent(SkyEventCycle.METEOR_BOOST_CHANCE) + "/min";
            } else if (type == SkyEventType.RAINBOW) {
                var cycle = SkyEvents.rainbowCycle(level);
                float rain = SkyEvents.rainfall(level);
                active = settings.override ? value > 0 : cycle != null && cycle.active() && !night && RainbowCycle.validRain(rain);
                visible = value > .01F;
                extra += "; rain " + number(rain) + "/100";
                if (night) extra += "; hidden: night";
                if (!RainbowCycle.validRain(rain)) extra += "; needs rain 1–20";
                if (!settings.override && active && cycle != null) extra += "; " + duration(cycle.remaining()) + " left";
                chance = percent(1 - Math.pow(1 - RainbowCycle.startChance(rain), 1200)) + "/min (day)";
            } else {
                var local = observer == null ? null : AuroraEvents.status(level, observer.blockPosition());
                value = settings.override ? settings.strength : local == null ? 0 : local.strength();
                active = settings.override ? settings.strength > 0 : local != null && local.active();
                visible = active || value > .01F;
                if (local == null) extra += "; no local players";
                double roll = local == null ? 0 : Math.clamp(local.chance(), 0F, 1F);
                chance = percent(1 - Math.pow(1 - roll, 2)) + "/min";
            }
            if (visible) happening.add(type.label);
            lines.append('\n').append(type.label).append(": ")
                .append(type == SkyEventType.BLOOD_MOON ? (active ? "on" : "off")
                    : (active ? "active" : value > .01F ? "fading" : "quiet")
                        + " " + number(Math.round(value * 10) / 10.0) + "/" + type.maxStrength())
                .append(settings.override ? " [override]" : "")
                .append("; ").append(chance).append(extra);
        }
        lines.append("\nCurrent: ").append(happening.isEmpty() ? "none" : String.join(", ", happening));
        if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER)
                && !level.dimension().equals(net.minecraft.world.level.Level.END))
            lines.append("\nTimers: paused (weather)");
        String message = lines.toString();
        source.sendSuccess(() -> Component.literal(message), false);
        return Command.SINGLE_SUCCESS;
    }
    private static String number(double value) {
        return new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
    }
    private static String percent(double chance) {
        return chance > 0 && chance < .0001 ? "<0.01%" : number(chance * 100) + "%";
    }
    private static String duration(int ticks) {
        return ticks < 1200 ? number(ticks / 20.0) + "s" : number(ticks / 1200.0) + "m";
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
