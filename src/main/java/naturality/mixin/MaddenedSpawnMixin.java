package naturality.mixin;

import naturality.entity.MaddenedVariants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
public abstract class MaddenedSpawnMixin {
    // Vanilla first validates the selected biome entry; substitute only its entity factory.
    @Redirect(method = "getMobForSpawn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;"))
    private static @org.jspecify.annotations.Nullable Entity naturality$maddened(EntityType<?> original, Level level, EntitySpawnReason reason) {
        return MaddenedVariants.forSpawn((ServerLevel)level, original).create(level, reason);
    }
}
