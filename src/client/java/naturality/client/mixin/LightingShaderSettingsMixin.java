package naturality.client.mixin;

import com.google.common.collect.ImmutableMap;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import naturality.client.config.LightingShaderSettings;
import naturality.client.config.FogShaderSettings;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderManager.class)
public abstract class LightingShaderSettingsMixin {
    @Inject(method = "loadInclude", at = @At("HEAD"), cancellable = true)
    private static void naturality$lightingSettings(Identifier location, Resource resource,
            ImmutableMap.Builder<Identifier, ShaderSource.CachedIncludeSource> output, CallbackInfo ci) {
        String name;
        String source;
        if (location.equals(Identifier.parse("naturality:shaders/include/pixel_lighting_settings.glsl"))) {
            name = "pixel_lighting_settings.glsl";
            source = LightingShaderSettings.source();
        } else if (location.equals(Identifier.parse("naturality:shaders/include/pixel_fog_settings.glsl"))) {
            name = "pixel_fog_settings.glsl";
            source = FogShaderSettings.source();
        } else if (location.equals(Identifier.parse("naturality:shaders/include/feature_settings.glsl"))) {
            name = "feature_settings.glsl";
            source = naturality.client.config.FeatureShaderSettings.source();
        } else return;
        var id = Identifier.parse("naturality:" + name);
        output.put(id, ShaderSource.CachedIncludeSource.create(id, source));
        ci.cancel();
    }
}
