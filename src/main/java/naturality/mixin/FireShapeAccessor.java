package naturality.mixin;

import java.util.function.Function;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireBlock.class)
public interface FireShapeAccessor {
    @Accessor("shapes") Function<BlockState, VoxelShape> naturality$vanillaShapes();
}
