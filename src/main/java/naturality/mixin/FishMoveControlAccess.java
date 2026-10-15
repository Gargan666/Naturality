package naturality.mixin;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Mob.class)
public interface FishMoveControlAccess {
    @Accessor("moveControl") void naturality$control(MoveControl<?> control);
}
