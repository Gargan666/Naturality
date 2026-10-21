package naturality.systems.enchanting.curses;

import net.minecraft.world.item.ItemStack;

@org.jspecify.annotations.NullMarked
public final class Lethargy {
    private Lethargy() {}
    public static float speed(ItemStack tool, float speed) {
        return speed * (1.0F - 0.2F * CurseLevels.active(tool, "lethargy"));
    }
}
