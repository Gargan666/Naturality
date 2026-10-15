package naturality.entities.creepers.mixins;

import naturality.entities.creepers.CreeperBiome;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
@org.jspecify.annotations.NullMarked
public abstract class CreeperBiomeMixin implements CreeperBiome {
    @Unique private static final EntityDataAccessor<String> naturality$BIOME =
        SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.STRING);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void naturality$define(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(naturality$BIOME, "");
    }

    @Unique private void naturality$capture() {
        var creeper = (Creeper)(Object)this;
        if (creeper.level().isClientSide() || !naturality$spawnBiome().isEmpty()) return;
        creeper.level().getBiome(creeper.blockPosition()).unwrapKey().ifPresent(key ->
            creeper.getEntityData().set(naturality$BIOME, key.identifier().toString()));
    }

    // Capture before AI/movement, once the entity has its actual spawn position.
    @Inject(method = "tick", at = @At("HEAD"))
    private void naturality$spawn(CallbackInfo ci) { naturality$capture(); }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void naturality$save(ValueOutput output, CallbackInfo ci) {
        naturality$capture();
        output.putString("naturality:spawn_biome", naturality$spawnBiome());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void naturality$load(ValueInput input, CallbackInfo ci) {
        ((Creeper)(Object)this).getEntityData().set(naturality$BIOME,
            input.getStringOr("naturality:spawn_biome", ""));
    }

    public String naturality$spawnBiome() {
        return ((Creeper)(Object)this).getEntityData().get(naturality$BIOME);
    }
}
