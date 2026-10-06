package naturality.mixin;

import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.dimension.end.DragonRespawnStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnderDragonFight.class)
public interface EndDragonFightAccess {
    @Accessor("dragonKilled") boolean naturality$dragonKilled();
    @Accessor("respawnStage") DragonRespawnStage naturality$respawnStage();
}