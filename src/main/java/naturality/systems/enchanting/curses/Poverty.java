package naturality.systems.enchanting.curses;

import java.util.function.Consumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

@org.jspecify.annotations.NullMarked
public final class Poverty {
    private Poverty() {}
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void generate(LootContext context, Consumer<ItemStack> output,
            Consumer<Consumer<ItemStack>> generate) {
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);
        try {
            var tool = context.getOptional(LootContextParams.TOOL);
            var source = context.getOptional(LootContextParams.DAMAGE_SOURCE);
            var weapon = tool != null ? tool : source == null ? null : source.getWeaponItem();
            boolean applies = depth == 0 && CurseLevels.active(weapon, "poverty") > 0
                && (source != null || context.getOptional(LootContextParams.BLOCK_STATE) != null);
            generate.accept(applies ? stack -> {
                ItemStack reduced = reduce(stack, context.getRandom());
                if (!reduced.isEmpty()) output.accept(reduced);
            } : output);
        } finally {
            if (depth == 0) DEPTH.remove(); else DEPTH.set(depth);
        }
    }

    public static ItemStack reduce(ItemStack stack, RandomSource random) {
        if (stack.isEmpty()) return stack;
        ItemStack result = stack.copy();
        if (result.getCount() > 1 || random.nextBoolean()) result.shrink(1);
        return result;
    }
}

