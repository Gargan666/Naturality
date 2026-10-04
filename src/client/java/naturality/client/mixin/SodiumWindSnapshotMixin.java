package naturality.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Keep exposure rays inside Sodium's immutable meshing snapshot. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.world.LevelSlice", remap=false)
public abstract class SodiumWindSnapshotMixin {
    @ModifyArgs(method="prepare", at=@At(value="INVOKE",
        target="Lnet/minecraft/world/level/levelgen/structure/BoundingBox;<init>(IIIIII)V"))
    private static void naturality$exposureBorder(Args args) {
        // Sodium already clones the 3x3x3 section neighborhood, but normally
        // unpacks only two neighboring blocks. Wind rays need eight. This
        // expands the copied volume without querying the live world off-thread.
        for(int i=0;i<3;i++) args.set(i, (int)args.get(i)-6);
        for(int i=3;i<6;i++) args.set(i, (int)args.get(i)+6);
    }
}
