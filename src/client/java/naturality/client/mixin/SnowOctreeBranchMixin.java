package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.snow.SnowSectionVisibility;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets="net.minecraft.client.renderer.Octree$Branch")
public abstract class SnowOctreeBranchMixin {
    @WrapOperation(method="visitNodes", at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/culling/Frustum;cubeInFrustum(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)I"))
    private int naturality$includeLowerSnow(Frustum frustum, BoundingBox bounds, Operation<Integer> original) {
        int visible=original.call(frustum,bounds);
        if (!naturality.config.GameplaySettings.clientSnowWrapping() || visible==-2 || visible==-1) return visible;
        return original.call(frustum,new BoundingBox(bounds.minX(),bounds.minY()-SnowSectionVisibility.MAX_DROP,
            bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()));
    }
}
