package naturality.client.mixin;

import naturality.client.entity.CreeperTintState;
import naturality.entity.CreeperBiome;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreeperRenderer.class)
public abstract class CreeperTextureMixin {
    @Unique private static final Identifier naturality$TEXTURE =
        Identifier.fromNamespaceAndPath("naturality", "textures/entity/creeper.png");

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
    private void naturality$texture(CreeperRenderState state, CallbackInfoReturnable<Identifier> cir) {
        cir.setReturnValue(naturality$TEXTURE);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Creeper;Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;F)V", at = @At("TAIL"))
    private void naturality$tint(Creeper creeper, CreeperRenderState state, float partial, CallbackInfo ci) {
        var biome = creeper.level().getBiome(creeper.blockPosition()).value();
        var id = Identifier.tryParse(((CreeperBiome)creeper).naturality$spawnBiome());
        if (id != null) {
            var original = creeper.level().registryAccess().lookupOrThrow(Registries.BIOME).getValue(id);
            if (original != null) biome = original;
        }
        // Resolve on the client: its foliage colormap includes resource-pack changes.
        ((CreeperTintState)state).naturality$foliageTint(0xFF000000 | biome.getFoliageColor());
    }
}
