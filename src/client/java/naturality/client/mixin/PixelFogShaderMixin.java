package naturality.client.mixin;

import com.google.common.collect.ImmutableMap;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shared fog includes also compile in vertex shaders, where derivatives are unavailable. */
@Mixin(ShaderManager.class)
public abstract class PixelFogShaderMixin {
    @ModifyVariable(method = "loadShader", at = @At("STORE"), ordinal = 0)
    private static String naturality$fragmentFog(String source, Identifier location, Resource resource,
            ShaderType type, ImmutableMap.Builder<?, ?> output) {
        if (type != ShaderType.FRAGMENT) return source;
        int lineEnd = source.indexOf('\n');
        if (!source.startsWith("#version") || lineEnd < 0) return source;
        return source.substring(0, lineEnd + 1) + "#define NATURALITY_FRAGMENT_FOG\n" + source.substring(lineEnd + 1);
    }
}

