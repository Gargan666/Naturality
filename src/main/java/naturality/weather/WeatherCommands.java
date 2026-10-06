package naturality.weather;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.List;

/** Dimension-first commands with completions restricted to that dimension's pool. */
public final class WeatherCommands {
    private WeatherCommands() {}
    private static List<String> pool(ServerLevel level) {
        return level.dimension().equals(Level.END) ? List.of("gravity","starfall","rise")
            : List.of("rain","wind","temperature","direction");
    }
    private static CommandSyntaxException error(String text) {
        return new SimpleCommandExceptionType(Component.literal(text)).create();
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var target = Commands.argument("dimension", DimensionArgument.dimension())
            .executes(c -> status(c.getSource(),DimensionArgument.getDimension(c,"dimension")));
        target.then(Commands.literal("status").executes(c -> status(c.getSource(),DimensionArgument.getDimension(c,"dimension"))));
        target.then(Commands.literal("auto").executes(c -> {
            var level = DimensionArgument.getDimension(c,"dimension");
            if (level.dimension().equals(Level.END)) {
                var cycle=EndWeatherSystem.cycle(level);
                for(String name:pool(level))cycle.set(name,-1);
                EndWeatherSystem.save(level);
            } else {
                var p=WeatherSystem.profile(level);
                p.overrideRain=p.overrideWind=p.overrideTemperature=p.overrideDirection=false;
                WeatherSystem.saveProfile(level);
            }
            c.getSource().sendSuccess(() -> Component.literal("Weather in " + level.dimension().identifier()+": all sliders automatic."),true);
            return Command.SINGLE_SUCCESS;
        }));
        target.then(Commands.argument("slider",StringArgumentType.word())
            .suggests((c,b) -> SharedSuggestionProvider.suggest(pool(DimensionArgument.getDimension(c,"dimension")),b))
            .then(Commands.argument("value",StringArgumentType.word())
                .suggests((c,b) -> SharedSuggestionProvider.suggest(List.of("0","50","100","auto"),b))
                .executes(WeatherCommands::set)));
        dispatcher.register(Commands.literal("weather").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(target));
    }
    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var level=DimensionArgument.getDimension(c,"dimension");
        String name=StringArgumentType.getString(c,"slider"), text=StringArgumentType.getString(c,"value");
        if(!pool(level).contains(name))throw error("That slider is not in this dimension's weather pool.");
        boolean auto=text.equals("auto");
        int value=-1;
        if (!auto) {
            try { value=Integer.parseInt(text); } catch(NumberFormatException e) { throw error("Expected a whole-number slider value or auto."); }
            int max=name.equals("direction")?360:100;
            if(value<0 || value>max)throw error("Slider must be between 0 and "+max+".");
        }
        if(level.dimension().equals(Level.END)) {
            EndWeatherSystem.cycle(level).set(name,value);
            EndWeatherSystem.save(level);
        } else {
            var p=WeatherSystem.profile(level);
            switch(name) {
                case "rain" -> { p.overrideRain=!auto; if(!auto)p.rain=value; }
                case "wind" -> { p.overrideWind=!auto; if(!auto)p.wind=value; }
                case "temperature" -> { p.overrideTemperature=!auto; if(!auto)p.temperature=value; }
                case "direction" -> { p.overrideDirection=!auto; if(!auto)p.direction=value; }
                default -> throw error("Unknown weather slider.");
            }
            WeatherSystem.saveProfile(level);
        }
        String result=auto?"automatic":"target "+value+" (override)";
        String note=
            !level.dimension().equals(Level.END) && !WeatherSystem.profile(level).enabled?" Weather is disabled in this dimension; settings are saved for later.":"";
        c.getSource().sendSuccess(() -> Component.literal("Weather in "+level.dimension().identifier()+": "+name+" "+result+"."+note),true);
        return Command.SINGLE_SUCCESS;
    }
    private static int status(CommandSourceStack source,ServerLevel level) {
        String text;
        if(level.dimension().equals(Level.END)) {
            var cycle=EndWeatherSystem.cycle(level); var s=cycle.state();
            text=String.format(java.util.Locale.ROOT,"gravity %.1f (%s), starfall %d (%s), rise %.1f (%s).",
                s.gravity(),mode(cycle.override("gravity")),s.starfall(),mode(cycle.override("starfall")),s.rise(),mode(cycle.override("rise")));
        } else {
            var p=WeatherSystem.profile(level); var s=WeatherSystem.state(level);
            text="rain="+(p.overrideRain?p.rain+" (override)":"auto")+", wind="+(p.overrideWind?p.wind+" (override)":"auto")
                +", temperature="+(p.overrideTemperature?p.temperature+" (override)":"auto")+", direction="+(p.overrideDirection?p.direction+" (override)":"auto");
            if(s!=null)text+=String.format(java.util.Locale.ROOT,". Current: rain %.1f, wind %.1f, temperature %.1f, direction %.1f",s.rain(),s.wind(),s.temperature(),s.direction());
            if(!p.enabled)text+=". Weather is disabled in this dimension; settings are saved for later.";
        }
        String message="Weather in "+level.dimension().identifier()+": "+text;
        source.sendSuccess(() -> Component.literal(message),false);
        return Command.SINGLE_SUCCESS;
    }
    private static String mode(int override) { return override<0?"auto":"override"; }
}
