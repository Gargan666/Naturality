package naturality.systems.enchanting.curses;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@org.jspecify.annotations.NullMarked
public final class Perfection {
    private Perfection() {}
    public static boolean blocks(@Nullable ItemStack weapon, boolean critical) {
        return !critical && CurseLevels.active(weapon, "perfection") > 0;
    }
}
