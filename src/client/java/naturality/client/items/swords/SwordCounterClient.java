package naturality.client.items.swords;

import naturality.items.swords.SwordCounter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import org.jspecify.annotations.NullMarked;

/** Only a new use-button press can predict a sword counter or send its use packet. */
@NullMarked
public final class SwordCounterClient {
    private static boolean freshPress;

    private SwordCounterClient() {}

    public static void onPress(Runnable use) {
        freshPress = true;
        try {
            use.run();
        } finally {
            freshPress = false;
        }
    }

    public static boolean allowUse(Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).is(ItemTags.SWORDS)) return true;
        return freshPress && SwordCounter.canAttempt(player, hand);
    }
}
