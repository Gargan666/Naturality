package naturality.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import naturality.weather.WeatherSystem;
import naturality.weather.WeatherWorldData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.Level;

public final class WeatherCommandsGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void execute(CommandDispatcher<CommandSourceStack> commands, CommandSourceStack source, String command) {
        try { check(commands.execute(command, source) == 1, "Command did not succeed: " + command); }
        catch (CommandSyntaxException e) { throw new AssertionError(command, e); }
    }
    private static void reject(CommandDispatcher<CommandSourceStack> commands, CommandSourceStack source, String command) {
        try { commands.execute(command, source); }
        catch (CommandSyntaxException expected) { return; }
        throw new AssertionError("Command must reject: " + command);
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(s -> {
                var weatherData = WeatherWorldData.get(s);
                // Even the coldest automatic pool must not freeze a newly created world.
                var overworld = weatherData.profile("minecraft:overworld");
                overworld.minTemperature = 0;
                overworld.maxTemperature = 0;
                weatherData.setDirty();
                var source = s.createCommandSourceStack().withSuppressedOutput();
                var commands = s.getCommands().getDispatcher();
                var root = commands.getRoot().getChild("weather");
                check(root.getChild("clear") == null && root.getChild("thunder") == null, "Vanilla presets must be replaced");
                check(root.getChildren().size() == 1 && root.getChild("dimension") != null,
                    "Dimension selection must be the first and only command branch");
                check(!root.canUse(source.withPermission(PermissionSet.NO_PERMISSIONS)), "Operator permission is required");
                reject(commands, source.withPermission(PermissionSet.NO_PERMISSIONS), "weather minecraft:overworld rain 80");
                check(WeatherSystem.state(s.overworld()).temperature() == 50 && !overworld.overrideTemperature,
                    "New worlds start at neutral temperature in automatic mode, even with a cold weather pool");
                var plains = s.overworld().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value();
                check(WeatherSystem.precipitation(s.overworld(), plains, new net.minecraft.core.BlockPos(0, 64, 0))
                    == net.minecraft.world.level.biome.Biome.Precipitation.RAIN, "New temperate worlds must not immediately become snowy");
                var nether = weatherData.profile("minecraft:the_nether");
                var end = weatherData.profile("minecraft:the_end");
                execute(commands, source, "weather minecraft:overworld rain 10");
                execute(commands, source, "weather minecraft:overworld wind 75");
                execute(commands, source, "weather minecraft:overworld temperature 20");
                check(overworld.rain == 10 && overworld.wind == 75 && overworld.temperature == 20
                    && overworld.overrideRain && overworld.overrideWind && overworld.overrideTemperature, "Stacked slider overrides");
                for (String bad : new String[]{"weather", "weather rain", "weather rain 10", "weather wind 90 minecraft:the_nether",
                        "weather clear", "weather thunder", "weather minecraft:overworld rain -1",
                        "weather minecraft:overworld wind 101", "weather minecraft:overworld temperature 0.5", "weather minecraft:overworld rain 50s",
                        "weather missing:dimension rain 80", "weather minecraft:overworld rain 80 missing:dimension"})
                    reject(commands, source, bad);
                check(overworld.rain == 10 && overworld.wind == 75 && overworld.temperature == 20, "Invalid commands cannot mutate settings");
                execute(commands, source, "weather minecraft:the_nether wind 90");
                execute(commands, source, "weather minecraft:the_end temperature 5");
                execute(commands, source.withLevel(s.getLevel(Level.NETHER)), "weather minecraft:the_nether rain 45");
                check(nether.rain == 45 && nether.wind == 90 && end.temperature == 5, "Explicit dimension routing");
                check(!nether.enabled && !end.enabled && overworld.rain == 10, "Reserved pools stay disabled and independent");
                execute(commands, source, "weather minecraft:overworld rain auto");
                check(!overworld.overrideRain && overworld.overrideWind && overworld.overrideTemperature, "Individual auto releases only one slider");
                execute(commands, source, "weather minecraft:the_nether auto");
                check(!nether.overrideRain && !nether.overrideWind && end.overrideTemperature && overworld.overrideWind, "All-auto affects only target dimension");
                var suggestions = commands.getCompletionSuggestions(commands.parse("weather ", source)).join();
                check(suggestions.getList().stream().anyMatch(v -> v.getText().equals("minecraft:the_nether")), "Dimension completions");
                check(suggestions.getList().stream().noneMatch(v -> v.getText().equals("rain")), "No sliders before a dimension is selected");
                for (String dimension : new String[]{"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}) {
                    var sliders = commands.getCompletionSuggestions(commands.parse("weather " + dimension + " ", source)).join()
                        .getList().stream().map(v -> v.getText()).toList();
                    check(sliders.containsAll(java.util.List.of("rain", "wind", "temperature")), "Selected pool suggests its sliders: " + dimension);
                    execute(commands, source, "weather " + dimension);
                }
                execute(commands, source, "weather minecraft:overworld");
                execute(commands, source, "weather minecraft:the_end status");
                execute(commands, source, "weather minecraft:overworld rain 0");
                execute(commands, source, "weather minecraft:overworld wind 100");
                execute(commands, source, "weather minecraft:overworld temperature 0");
                check(weatherData.profile("minecraft:overworld").wind == 100
                    && weatherData.profile("minecraft:overworld").overrideWind
                    && weatherData.profile("minecraft:the_end").temperature == 5
                    && !weatherData.profile("minecraft:the_end").enabled, "Commands persist per-world targets and disabled status");
                try (var reader = java.nio.file.Files.newBufferedReader(net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getConfigDir().resolve("naturality-server.json"))) {
                    var stored = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    check(!stored.has("weather"), "Weather targets are not stored in the server config");
                } catch (java.io.IOException e) { throw new AssertionError(e); }
            });
            context.waitTicks(220);
            server.runOnServer(s -> {
                var state = WeatherSystem.state(s.overworld());
                check(state.rain() == 0 && state.wind() == 100 && state.temperature() == 0, "Commands drive live server channels");
                check(WeatherSystem.state(s.getLevel(Level.NETHER)) == null && WeatherSystem.state(s.getLevel(Level.END)) == null, "Reserved dimension effects remain off");
            });
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> {
                var state = WeatherSystem.state(client.level);
                check(state != null && state.rain() == 0 && state.wind() == 100 && state.temperature() == 0, "Command changes synchronize to client");
            });
        }
    }
}
