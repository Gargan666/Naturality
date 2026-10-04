package naturality.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap=false)
public abstract class SodiumWaterUniformMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void naturality$bind(CallbackInfo ci,@Local(argsOnly=true) RenderPass pass) {
        com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
        naturality.client.fluid.ProceduralFluids.bind(pass);
        naturality.client.fire.ProceduralFire.bindSodium(pass);
        naturality.client.weather.WindRendering.bind(pass);
        naturality.client.fluid.WaterVisuals.bind(pass);
        naturality.client.fluid.WaterVisuals.bindScene(pass);
    }
}
