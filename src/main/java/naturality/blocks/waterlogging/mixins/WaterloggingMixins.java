package naturality.blocks.waterlogging.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.blocks.waterlogging.BlockWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Required state and fluid hooks for the additional waterloggable vanilla blocks. */
public final class WaterloggingMixins {
    private WaterloggingMixins() {}

    @Mixin(Block.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingDefinitionMixin {
        @ModifyVariable(method = "registerDefaultState", at = @At("HEAD"), argsOnly = true)
        private BlockState naturality$dryDefault(BlockState state) {
            // BooleanProperty lists true first. Constructors using stateDefinition.any() must not create water sources.
            return BlockWaterlogging.supports(state.getBlock())
                ? state.setValue(BlockStateProperties.WATERLOGGED,false) : state;
        }

        @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;createBlockStateDefinition(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V"))
        private void naturality$waterProperties(Block block, StateDefinition.Builder<Block, BlockState> builder, Operation<Void> original) {
            original.call(block, builder);
            if (!BlockWaterlogging.supports(block)) return;
            builder.add(BlockStateProperties.WATERLOGGED);

        }
    }

    @Mixin(AnvilBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingAnvilDamageMixin {
        @Inject(method = "damage", at = @At("RETURN"), cancellable = true)
        private static void naturality$preserveAnvilWater(BlockState previous, CallbackInfoReturnable<@Nullable BlockState> cir) {
            BlockState damaged = cir.getReturnValue();
            if (damaged != null) cir.setReturnValue(damaged.setValue(BlockStateProperties.WATERLOGGED,
                previous.getValue(BlockStateProperties.WATERLOGGED)));
        }
    }

    @Mixin({DoorBlock.class, AnvilBlock.class, StonecutterBlock.class, EnchantingTableBlock.class, BellBlock.class,
        FenceGateBlock.class, HopperBlock.class, EndRodBlock.class,
        DaylightDetectorBlock.class, BrewingStandBlock.class, BedBlock.class, LecternBlock.class})
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingContainersMixin implements SimpleWaterloggedBlock {
        @Override
        public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {

            return fluid == Fluids.WATER
                && !state.getValue(BlockStateProperties.WATERLOGGED);
        }

    }

    @Mixin(BlockBehaviour.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingFluidMixin {
        @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
        private void naturality$sourceWater(BlockState state, CallbackInfoReturnable<FluidState> cir) {
            if (BlockWaterlogging.waterlogged(state)) cir.setReturnValue(Fluids.WATER.getSource(false));
        }
    }

    @Mixin(BlockBehaviour.BlockStateBase.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingUpdatesMixin {
        @Shadow protected abstract BlockState asState();

        @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
        private void naturality$fluidUpdates(LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                BlockPos neighborPos, BlockState neighbor, RandomSource random, CallbackInfoReturnable<BlockState> cir) {
            cir.setReturnValue(BlockWaterlogging.neighborUpdate(asState(), cir.getReturnValue(), level, ticks, pos));
        }
    }

    @Mixin(BlockItem.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingPlacementMixin {
        @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
        private void naturality$underwaterPlacement(BlockPlaceContext context, CallbackInfoReturnable<@Nullable BlockState> cir) {
            BlockState state = cir.getReturnValue();
            if (state != null) cir.setReturnValue(BlockWaterlogging.placement(state, context.getLevel(), context.getClickedPos()));
        }
    }

    @Mixin({DoorBlock.class, AbstractBedBlock.class})
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingSecondHalfMixin {
        @WrapOperation(method = "setPlacedBy", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
        private boolean naturality$secondCell(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
            return original.call(level, pos, BlockWaterlogging.placement(state, level, pos));
        }
    }

    @Mixin(AbstractBedBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingBedRemovalMixin {
        @WrapOperation(method = "playerWillDestroy", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
        private boolean naturality$leaveHeadWater(Level level, BlockPos pos, BlockState replacement, int flags, Operation<Boolean> original) {
            BlockState previous = level.getBlockState(pos);
            if (BlockWaterlogging.waterlogged(previous)) replacement = previous.getFluidState().createLegacyBlock();
            return original.call(level, pos, replacement, flags);
        }
    }

}
