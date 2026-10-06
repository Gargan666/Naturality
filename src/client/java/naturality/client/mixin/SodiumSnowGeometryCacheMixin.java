package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.snow.SnowGeometryCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

/** A Sodium LevelSlice is reused, so discard every cache at the end of its build. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap=false)
public abstract class SodiumSnowGeometryCacheMixin {
    @WrapMethod(method="execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;")
    @Coerce
    private Object naturality$snowCache(@Coerce Object context, @Coerce Object token, Operation<Object> original) {
        SnowGeometryCache.begin();
        try { return original.call(context,token); }
        finally { SnowGeometryCache.end(); }
    }
}
