package naturality.weather;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Operator commands edit persistent weather targets stored with each world. */
public final class WeatherCommands {
    private WeatherCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("weather")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        var target = Commands.argument("dimension", DimensionArgument.dimension())
            .executes(c -> status(c.getSource(), DimensionArgument.getDimension(c, "dimension")));
        for (var channel : Channel.values()) target.then(channel(channel));
        target.then(Commands.literal("auto").executes(c -> {
            var source = c.getSource();
            var level = DimensionArgument.getDimension(c, "dimension");
            var p = editableProfile(level);
            p.overrideRain = p.overrideWind = p.overrideTemperature = p.overrideDirection = false;
            WeatherSystem.saveProfile(level);
            source.sendSuccess(() -> Component.literal("Weather in " + dimension(level)
                + ": all sliders automatic." + disabledNotice(p)), true);
            return Command.SINGLE_SUCCESS;
        }));
        target.then(Commands.literal("status")
            .executes(c -> status(c.getSource(), DimensionArgument.getDimension(c, "dimension"))));
        dispatcher.register(root.then(target));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> channel(Channel channel) {
        var node = Commands.literal(channel.name().toLowerCase(java.util.Locale.ROOT));
        var value = Commands.argument("value", IntegerArgumentType.integer(0, channel.maximum()));
        value.executes(c -> set(c, DimensionArgument.getDimension(c, "dimension"), channel));
        node.then(value);
        node.then(Commands.literal("auto").executes(c -> {
            var source = c.getSource();
            var level = DimensionArgument.getDimension(c, "dimension");
            var p = editableProfile(level);
            channel.automatic(p);
            WeatherSystem.saveProfile(level);
            source.sendSuccess(() -> Component.literal("Weather in " + dimension(level) + ": "
                + channel.label() + " automatic." + disabledNotice(p)), true);
            return Command.SINGLE_SUCCESS;
        }));
        return node;
    }

    private static int set(CommandContext<CommandSourceStack> context, ServerLevel level, Channel channel) {
        int value = IntegerArgumentType.getInteger(context, "value");
        var p = editableProfile(level);
        channel.set(p, value);
        WeatherSystem.saveProfile(level);
        context.getSource().sendSuccess(() -> Component.literal("Weather in " + dimension(level) + ": "
            + channel.label() + " target " + value + " (override)." + disabledNotice(p)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static WeatherProfile editableProfile(ServerLevel level) {
        // Reserved/custom dimension profiles default to disabled in world data.
        return WeatherSystem.profile(level);
    }
    private static String dimension(ServerLevel level) { return level.dimension().identifier().toString(); }
    private static String disabledNotice(WeatherProfile p) {
        return p.enabled ? "" : " Weather is disabled in this dimension; settings are saved for later.";
    }
    private static int status(CommandSourceStack source, ServerLevel level) {
        var p = WeatherSystem.profile(level);
        var state = WeatherSystem.state(level);
        StringBuilder text = new StringBuilder("Weather in " + dimension(level) + ": ");
        for (var channel : Channel.values()) {
            if (channel != Channel.RAIN) text.append(", ");
            text.append(channel.label()).append("=")
                .append(channel.overridden(p) ? channel.value(p) + " (override)" : "auto");
        }
        if (state != null) text.append(String.format(java.util.Locale.ROOT,
            ". Current: rain %.1f, wind %.1f, temperature %.1f, direction %.1f", state.rain(), state.wind(), state.temperature(), state.direction()));
        text.append(disabledNotice(p));
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return Command.SINGLE_SUCCESS;
    }
    private enum Channel {
        RAIN, WIND, TEMPERATURE, DIRECTION;
        String label() { return name().toLowerCase(java.util.Locale.ROOT); }
        int maximum() { return this == DIRECTION ? 360 : 100; }
        void set(WeatherProfile p, int value) {
            switch (this) {
                case RAIN -> { p.rain = value; p.overrideRain = true; }
                case WIND -> { p.wind = value; p.overrideWind = true; }
                case TEMPERATURE -> { p.temperature = value; p.overrideTemperature = true; }
                case DIRECTION -> { p.direction = value; p.overrideDirection = true; }
            }
        }
        void automatic(WeatherProfile p) {
            switch (this) {
                case RAIN -> p.overrideRain = false;
                case WIND -> p.overrideWind = false;
                case TEMPERATURE -> p.overrideTemperature = false;
                case DIRECTION -> p.overrideDirection = false;
            }
        }
        boolean overridden(WeatherProfile p) {
            return switch (this) { case RAIN -> p.overrideRain; case WIND -> p.overrideWind; case TEMPERATURE -> p.overrideTemperature; case DIRECTION -> p.overrideDirection; };
        }
        int value(WeatherProfile p) {
            return switch (this) { case RAIN -> p.rain; case WIND -> p.wind; case TEMPERATURE -> p.temperature; case DIRECTION -> p.direction; };
        }
    }
}
