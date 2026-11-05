package naturality.blocks.darkoak.mixins;

import java.util.function.Function;
import naturality.blocks.darkoak.ConnectedDarkOakLog;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

public final class DarkOakMixins {
    private DarkOakMixins() {}

    @Mixin(Blocks.class)
    public static abstract class Registration {
        @ModifyVariable(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Function;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)Lnet/minecraft/world/level/block/Block;",
            at = @At("HEAD"), argsOnly = true)
        private static Function<BlockBehaviour.Properties, Block> naturality$connectedLog(
                Function<BlockBehaviour.Properties, Block> factory, ResourceKey<Block> key,
                Function<BlockBehaviour.Properties, Block> original, BlockBehaviour.Properties properties) {
            return key.identifier().toString().equals("minecraft:dark_oak_log") ? ConnectedDarkOakLog::new : factory;
        }
    }
}
