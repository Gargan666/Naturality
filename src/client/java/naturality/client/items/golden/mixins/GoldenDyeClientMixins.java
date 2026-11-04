package naturality.client.items.golden.mixins;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import java.io.UncheckedIOException;
import java.util.Optional;
import naturality.client.items.golden.GoldenVisuals;
import naturality.items.golden.GoldenDye;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class GoldenDyeClientMixins {
    private GoldenDyeClientMixins() {}

    @Mixin(net.minecraft.server.packs.resources.MultiPackResourceManager.class)
    public static abstract class Resources {
        @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
        private void naturality$fallback(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
            if (cir.getReturnValue().isEmpty()) cir.setReturnValue(GoldenVisuals.fallback((ResourceManager)this, id));
        }
    }

    @Mixin(net.minecraft.client.renderer.ShaderManager.class)
    public static abstract class Palette {
        @WrapOperation(method = "loadConfigs", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderManager;loadInclude(Lnet/minecraft/resources/Identifier;Lnet/minecraft/server/packs/resources/Resource;Lcom/google/common/collect/ImmutableMap$Builder;)V"))
        private static void naturality$palette(Identifier location, Resource resource,
                ImmutableMap.Builder<Identifier, ShaderSource.CachedIncludeSource> output, Operation<Void> original,
                @Local(argsOnly = true) ResourceManager manager) {
            if (location.equals(Identifier.parse("naturality:shaders/include/golden_palette.glsl"))) {
                var id = Identifier.parse("naturality:golden_palette.glsl");
                try { output.put(id, ShaderSource.CachedIncludeSource.create(id, GoldenVisuals.palette(manager))); }
                catch (java.io.IOException error) { throw new UncheckedIOException(error); }
            } else original.call(location, resource, output);
        }
    }

    @Mixin(net.minecraft.client.renderer.entity.state.SheepRenderState.class)
    public static abstract class Wool {
        @Inject(method = "getWoolColor", at = @At("HEAD"), cancellable = true)
        private void naturality$wool(CallbackInfoReturnable<Integer> cir) {
            var state = (net.minecraft.client.renderer.entity.state.SheepRenderState)(Object)this;
            if (!state.isJebSheep && GoldenDye.isGolden(state.woolColor)) cir.setReturnValue(0xFF000000 | GoldenDye.RGB);
        }
    }

    @Mixin(net.minecraft.client.renderer.entity.layers.RenderLayer.class)
    public static abstract class DyedLayers {
        @ModifyArg(method = "renderColoredCutoutModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"), index = 6)
        private static int naturality$maskTint(int color) {
            return GoldenVisuals.maskedTint(color);
        }

        @ModifyArg(method = "renderColoredCutoutModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityCutout(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"), index = 0)
        private static Identifier naturality$ranked(Identifier texture, @Local(argsOnly = true, ordinal = 1) int color) {
            return GoldenVisuals.dyedTexture(texture, color);
        }
    }

    @Mixin(net.minecraft.client.renderer.entity.layers.WolfCollarLayer.class)
    public static abstract class WolfCollar {
        @ModifyArg(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"), index = 6)
        private int naturality$maskTint(int color) {
            return GoldenVisuals.maskedTint(color);
        }

        @ModifyArg(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityCutout(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"), index = 0)
        private Identifier naturality$ranked(Identifier texture,
                @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.WolfRenderState state) {
            var color = state.collarColor;
            return color != null && GoldenDye.isGolden(color) ? GoldenVisuals.dyedTexture(texture, GoldenDye.RGB) : texture;
        }
    }

    @Mixin(net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer.class)
    public static abstract class DyedEquipment {
        @ModifyArg(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"), index = 6)
        private int naturality$maskTint(int color) {
            return GoldenVisuals.maskedTint(color);
        }

        // Select the rendering overload; the shorter overload only forwards its arguments.
        @ModifyArg(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"), index = 0)
        private Identifier naturality$ranked(Identifier texture, @Local(name = "color") int color) {
            return GoldenVisuals.dyedTexture(texture, color);
        }

        @ModifyArg(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"), index = 0)
        private Identifier naturality$rankedGlint(Identifier texture, @Local(name = "color") int color) {
            return GoldenVisuals.dyedTexture(texture, color);
        }
    }
}
