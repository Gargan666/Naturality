package naturality.client.entities.creepers.mixins;

import naturality.client.entities.creepers.CreeperTintState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import naturality.entities.creepers.CreeperBiome;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class CreepersMixins {
    private CreepersMixins() {}

    @Mixin(LivingEntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class CreeperModelTintMixin {
        @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
        private void naturality$tint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
            if (state instanceof CreeperTintState creeper)
                cir.setReturnValue(net.minecraft.util.ARGB.multiply(cir.getReturnValue(), creeper.naturality$foliageTint()));
        }
    }

    @Mixin(CreeperRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class CreeperTextureMixin {
        @Unique private static final Identifier naturality$TEXTURE =
            Identifier.fromNamespaceAndPath("naturality", "textures/entity/creeper.png");

        @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
        private void naturality$texture(CreeperRenderState state, CallbackInfoReturnable<Identifier> cir) {
            cir.setReturnValue(naturality$TEXTURE);
        }

        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Creeper;Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;F)V", at = @At("TAIL"))
        private void naturality$tint(Creeper creeper, CreeperRenderState state, float partial, CallbackInfo ci) {
            var biome = creeper.level().getBiome(creeper.blockPosition()).value();
            // A vanilla server has no Naturality biome metadata; use the observed biome.
            var id = creeper instanceof CreeperBiome source ? Identifier.tryParse(source.naturality$spawnBiome()) : null;
            if (id != null) {
                var original = creeper.level().registryAccess().lookupOrThrow(Registries.BIOME).getValue(id);
                if (original != null) biome = original;
            }
            // Resolve on the client: its foliage colormap includes resource-pack changes.
            ((CreeperTintState)state).naturality$foliageTint(0xFF000000 | biome.getFoliageColor());
        }
    }

    @Mixin(CreeperRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class CreeperTintStateMixin implements CreeperTintState {
        @Unique private int naturality$color = -1;
        public int naturality$foliageTint() { return naturality$color; }
        public void naturality$foliageTint(int color) { naturality$color = color; }
    }
}
