package naturality.client.effects.stunned;

import naturality.effects.stunned.Stunned;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NullMarked;

/** Suppress gameplay controls while leaving look, chat, camera and interface controls to vanilla. */
@NullMarked
public final class StunnedClient {
    private StunnedClient() {}

    public static boolean active() { return Stunned.active(Minecraft.getInstance().player); }

    public static void suppressControls(Minecraft client) {
        var player = client.player;
        if (!Stunned.active(player)) return;
        if (client.gameMode != null) client.gameMode.stopDestroyBlock();
        var options = client.options;
        // Only discard gameplay presses; normal key handling still opens chat/screens and changes the view.
        for (var key : new KeyMapping[]{options.keyAttack, options.keyUse, options.keyPickItem,
                options.keyDrop, options.keySwapOffhand, options.keySaveHotbarActivator, options.keyLoadHotbarActivator})
            discardClicks(key);
        for (var key : options.keyHotbarSlots) discardClicks(key);
    }

    private static void discardClicks(KeyMapping key) { while (key.consumeClick()) { } }
}
