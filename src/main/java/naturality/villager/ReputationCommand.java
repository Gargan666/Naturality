package naturality.villager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public final class ReputationCommand {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            var root = Commands.literal("reputation").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> Component.literal("Reputation: " + Reputation.score(player)), false);
                    return Reputation.score(player);
                });
            root.then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
                var player = c.getSource().getPlayerOrException();
                Reputation.set(player, IntegerArgumentType.getInteger(c, "value"));
                c.getSource().sendSuccess(() -> Component.literal("Reputation: " + Reputation.score(player)), true);
                return 1;
            }));
            root.then(Commands.literal("set").then(Commands.argument("players", EntityArgument.players())
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    var players = EntityArgument.getPlayers(c, "players");
                    int value = IntegerArgumentType.getInteger(c, "value");
                    players.forEach(player -> Reputation.set(player, value));
                    c.getSource().sendSuccess(() -> Component.literal("Set reputation to " + value + " for " + players.size() + " player(s)."), true);
                    return players.size();
                }))));
            dispatcher.register(root);
        });
    }
    private ReputationCommand() {}
}
