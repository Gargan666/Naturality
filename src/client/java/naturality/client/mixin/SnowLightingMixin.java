package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Run the normal lighter in the cell occupied by each fitted snow volume. */
@Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl",remap=false)
public abstract class SnowLightingMixin {
    @SuppressWarnings("null") @Shadow private BlockAndTintGetter level;
    @SuppressWarnings("null") @Shadow private BlockPos pos;
    @SuppressWarnings("null") @Shadow private BlockState blockState;
    @SuppressWarnings("null") @Shadow @Final private AoCalculator aoCalc;

    @WrapMethod(method="shadeQuad")
    private void naturality$surfaceLight(MutableQuadViewImpl quad,boolean ao,boolean emissive,boolean vanillaShade,
            Operation<Void> original) {
        if((quad.tag() & 0xFFFFFF00)!=0x534E0000) { original.call(quad,ao,emissive,vanillaShade);return; }
        int shift=(quad.tag()&255)-64;
        if(shift==0) { original.call(quad,ao,emissive,vanillaShade);return; }
        var owner=pos;
        pos=owner.above(shift);
        quad.translate(0,-shift,0);
        aoCalc.prepare(level,blockState,pos);
        try { original.call(quad,ao,emissive,vanillaShade); }
        finally {
            quad.translate(0,shift,0);
            pos=owner;
            aoCalc.prepare(level,blockState,owner);
        }
    }
}
