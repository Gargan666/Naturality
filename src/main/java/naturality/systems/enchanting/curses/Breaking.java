package naturality.systems.enchanting.curses;

import net.minecraft.world.item.ItemStack;

@org.jspecify.annotations.NullMarked
public final class Breaking {
    private Breaking() {}
    public static boolean destroys(ItemStack stack) {
        // This curse must remain effective on the final durability point itself.
        return CurseLevels.stored(stack, "breaking") > 0;
    }
}
