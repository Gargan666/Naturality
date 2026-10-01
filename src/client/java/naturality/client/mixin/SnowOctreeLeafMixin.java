package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.snow.SnowSectionVisibility;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets="net.minecraft.client.renderer.Octree$Leaf")
public abstract class SnowOctreeLeafMixin {
    @Shadow @Final private SectionRenderDispatcher.RenderSection section;

    @WrapOperation(method="visitNodes", at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean naturality$includeLowerSnow(Frustum frustum, AABB bounds, Operation<Boolean> original) {
        if (original.call(frustum,bounds)) return true;
        if (!naturality.config.GameplaySettings.clientSnowWrapping()) return false;
        int drop=SnowSectionVisibility.below(section.getSectionNode());
        return drop>0 && original.call(frustum,new AABB(bounds.minX,bounds.minY-drop,bounds.minZ,
            bounds.maxX,bounds.maxY,bounds.maxZ));
    }
}
