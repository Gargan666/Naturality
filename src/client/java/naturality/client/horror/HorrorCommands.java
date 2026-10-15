package naturality.client.horror;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Private effects can be tested on multiplayer servers without operator permission. */
public final class HorrorCommands {
    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(
            literal("naturalityhorror")
                .then(literal("walker").executes(context -> trigger(context.getSource(), false)))
                .then(literal("sprinter").executes(context -> {
                    if (!AmbientHorror.testSprinter()) {
                        context.getSource().sendError(Component.literal("No offscreen solid terrain 24-36 blocks away."));
                        return 0;
                    }
                    context.getSource().sendFeedback(Component.literal("spawned walker"));
                    return 1;
                }))
                .then(literal("eyes").executes(context -> trigger(context.getSource(), true)))
                .then(literal("clear").executes(context -> {
                    AmbientHorror.clearEffects();
                    context.getSource().sendFeedback(Component.literal("Cleared ambient horror effects."));
                    return 1;
                }))));
    }
    private static int trigger(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, boolean eyes) {
        if (!AmbientHorror.test(eyes)) {
            source.sendError(Component.literal(eyes
                ? "No clear space 8-24 blocks ahead. Face an open area while awake and outside spectator mode."
                : "No offscreen solid terrain 24-36 blocks away."));
            return 0;
        }
        source.sendFeedback(Component.literal(eyes ? "spawned eyes" : "spawned walker"));
        return 1;
    }
    private HorrorCommands() {}
}

