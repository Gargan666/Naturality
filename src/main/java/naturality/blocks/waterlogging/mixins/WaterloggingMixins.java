package naturality.blocks.waterlogging.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.blocks.waterlogging.BlockWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Required state and fluid hooks for the additional waterloggable vanilla blocks. */
public final class WaterloggingMixins {
    private WaterloggingMixins() {}

    @Mixin(Block.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingDefinitionMixin {
        @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;createBlockStateDefinition(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V"))
        private void naturality$waterProperties(Block block, StateDefinition.Builder<Block, BlockState> builder, Operation<Void> original) {
            original.call(block, builder);
            if (!BlockWaterlogging.supports(block)) return;
            builder.add(BlockStateProperties.WATERLOGGED);
            if (block instanceof AbstractCauldronBlock) builder.add(BlockWaterlogging.WATER_SURFACE);
        }
    }

    @Mixin({DoorBlock.class, AnvilBlock.class, StonecutterBlock.class, EnchantingTableBlock.class, BellBlock.class,
        AbstractCauldronBlock.class, FenceGateBlock.class, HopperBlock.class, EndRodBlock.class,
        DaylightDetectorBlock.class, BrewingStandBlock.class, BedBlock.class, LecternBlock.class})
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingContainersMixin implements SimpleWaterloggedBlock {
        @Override
        public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
            // Cauldrons waterlog only through placement, never buckets, dispensers or incoming flow.
            return !(state.getBlock() instanceof AbstractCauldronBlock) && fluid == Fluids.WATER
                && !state.getValue(BlockStateProperties.WATERLOGGED);
        }

        @Override
        public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
            return !(state.getBlock() instanceof AbstractCauldronBlock)
                && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid);
        }

        @Override
        public ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
            return state.getBlock() instanceof AbstractCauldronBlock ? ItemStack.EMPTY
                : SimpleWaterloggedBlock.super.pickupBlock(user, level, pos, state);
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

    @Mixin(Level.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingCauldronStateMixin {
        @ModifyVariable(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true)
        private BlockState naturality$externalWaterOverridesFill(BlockState replacement, BlockPos pos, BlockState state, int flags, int limit) {
            return BlockWaterlogging.cauldronChange(replacement, (Level)(Object)this, pos);
        }
    }

    @Mixin(AbstractCauldronBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingCauldronInteractionMixin {
        @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
        private void naturality$noStoredFill(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
            if (BlockWaterlogging.waterlogged(state)) cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Mixin({CauldronBlock.class, LayeredCauldronBlock.class})
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterloggingCauldronFillMixin {
        @Inject(method = "handlePrecipitation", at = @At("HEAD"), cancellable = true)
        private void naturality$noWeatherFill(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation, CallbackInfo ci) {
            if (BlockWaterlogging.waterlogged(state)) ci.cancel();
        }

        @Inject(method = "receiveStalactiteDrip", at = @At("HEAD"), cancellable = true)
        private void naturality$noDripFill(BlockState state, Level level, BlockPos pos, Fluid fluid, CallbackInfo ci) {
            if (BlockWaterlogging.waterlogged(state)) ci.cancel();
        }
    }
}
