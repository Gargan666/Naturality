package naturality.client.ui.items;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Centered pickup pops and eased selection/use scaling for hotbar sprites. */
@org.jspecify.annotations.NullMarked
public final class HotbarPickupScale {
    private HotbarPickupScale() {}
    public static float centeredOffset(float offsetY, int y) {
        if (offsetY == y + 12) offsetY = y + 8;
        else if (offsetY == -(y + 12)) offsetY = -(y + 8);
        return offsetY;
    }
    public static float uniformScale(float yScale) { return yScale; }

    public static final class Animation {
        private final float[] selection = new float[9];
        private @Nullable Player previousPlayer;
        private ItemStack previousStack = ItemStack.EMPTY;
        private int previousSlot = -1;
        private float previousSwing;
        private boolean previousUsing;
        private double lastTime;
        private double pulseStart = Double.NEGATIVE_INFINITY;
        private float useScale = 1;

        public void update(Player player, float partial) {
            double time = player.tickCount + partial;
            int slot = player.getInventory().getSelectedSlot();
            ItemStack stack = player.getInventory().getSelectedItem();
            float swing = player.getSwingAnimation(partial);
            boolean using = player.isUsingItem();
            boolean reset = previousPlayer != player || time < lastTime || time - lastTime > 20;
            if (reset) {
                java.util.Arrays.fill(selection, 0);
                previousSlot = -1;
                previousStack = ItemStack.EMPTY;
                pulseStart = Double.NEGATIVE_INFINITY;
                useScale = 1;
            }
            double elapsed = reset ? 0 : Math.max(0, time - lastTime);
            boolean sameSlot = previousSlot == slot;
            var client = net.minecraft.client.Minecraft.getInstance();
            var gameMode = client.gameMode;
            boolean mining = gameMode != null && gameMode.isDestroying()
                && stack.is(net.minecraft.tags.ItemTags.MINING_ENCHANTABLE);
            boolean miningStroke = mining && swing > 0 && (previousSwing == 0 || swing < previousSwing);
            boolean consumed = ItemStack.isSameItemSameComponents(stack, previousStack)
                && stack.getCount() < previousStack.getCount();
            if (!reset && sameSlot && (miningStroke || (using && !previousUsing) || consumed)) {
                pulseStart = time;
            }
            if (!sameSlot) pulseStart = Double.NEGATIVE_INFINITY;
            double age = time - pulseStart;
            // Compress immediately; ease only the six-tick return to the previous size.
            useScale = .9F + .1F * smooth(age / 6);
            float blend = (float)(1 - Math.exp(-elapsed / 2));
            for (int i = 0; i < selection.length; i++) {
                selection[i] += ((i == slot ? 1 : 0) - selection[i]) * blend;
            }
            previousPlayer = player;
            previousSlot = slot;
            previousStack = stack.copy();
            previousSwing = swing;
            previousUsing = using;
            lastTime = time;
        }

        private static float smooth(double value) {
            float t = (float)Math.clamp(value, 0, 1);
            return t * t * (3 - 2 * t);
        }

        public float scale(int seed) {
            int slot = seed - 1;
            if (slot < 0 || slot >= selection.length) return 1;
            return (1 + .2F * selection[slot]) * (slot == previousSlot ? useScale : 1);
        }
    }
}