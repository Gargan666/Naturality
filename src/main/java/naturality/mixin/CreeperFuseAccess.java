package naturality.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperFuseAccess {
    @Accessor("maxSwell") void naturality$setFuse(int ticks);
}
