package naturality.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.util.SodiumChunkSection", remap=false)
public abstract class SodiumWaterSectionMixin implements naturality.client.fluid.SodiumWaterBridge { }
