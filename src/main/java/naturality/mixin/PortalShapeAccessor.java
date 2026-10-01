package naturality.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PortalShape.class)
public interface PortalShapeAccessor {
    @Accessor("bottomLeft") BlockPos naturality$bottomLeft();
    @Accessor("width") int naturality$width();
    @Accessor("height") int naturality$height();
    @Accessor("axis") Direction.Axis naturality$axis();
    @Accessor("rightDir") Direction naturality$rightDir();
}
