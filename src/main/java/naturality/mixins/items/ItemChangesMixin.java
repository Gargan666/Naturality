package naturality.mixins.items;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import naturality.items.damage.ItemDamageSources;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class ItemChangesMixin {
    private ItemChangesMixin() {}

    @Mixin(ItemStack.class)
    public abstract static class ItemDamageRegistryMixin {
        @ModifyReturnValue(method = "getDamageSource", at = @At("RETURN"))
        private DamageSource naturality$currentRegistry(DamageSource source, LivingEntity attacker) {
            return ItemDamageSources.inWorld(source, attacker);
        }
    }
}
