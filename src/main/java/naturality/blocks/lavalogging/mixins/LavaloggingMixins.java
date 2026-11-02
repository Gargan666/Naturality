package naturality.blocks.lavalogging.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import java.util.Optional;
import naturality.blocks.lavalogging.BlockLavalogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DriedGhastBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** State, container and placement hooks; ordinary behavior stays in BlockLavalogging. */
public final class LavaloggingMixins {
    private LavaloggingMixins() {}

    @Mixin(BlockBehaviour.Properties.class)
    public interface LavaloggingPropertiesAccessor {
        @Accessor("ignitedByLava") boolean naturality$ignitedByLava();
    }

    @Mixin(StateDefinition.Builder.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingDefinitionMixin<O, S extends StateHolder<O, S>> {
        @SuppressWarnings("null") // Initialized by the target constructor, not by this mixin.
        @Shadow @Final private O owner;
        @SuppressWarnings("null") // Initialized by the target constructor, not by this mixin.
        @Shadow @Final private Map<String, Property<?>> properties;
        @Shadow public abstract StateDefinition.Builder<O, S> add(Property<?>... added);

        @Inject(method = "create", at = @At("HEAD"))
        private void naturality$define(CallbackInfoReturnable<StateDefinition<O, S>> cir) {
            // Read construction properties, before block states and vanilla's fire table exist.
            if (owner instanceof Block block && block instanceof SimpleWaterloggedBlock
                    && !(block instanceof BarrierBlock)
                    && properties.containsValue(BlockStateProperties.WATERLOGGED)
                    && !((LavaloggingPropertiesAccessor) block.properties()).naturality$ignitedByLava()) {
                add(BlockLavalogging.LAVALOGGED);
            }
        }
    }

    @Mixin(Block.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingDefaultsMixin {
        @ModifyVariable(method = "registerDefaultState", at = @At("HEAD"), argsOnly = true)
        private BlockState naturality$dryDefault(BlockState state) {
            return state.hasProperty(BlockLavalogging.LAVALOGGED) ? state.setValue(BlockLavalogging.LAVALOGGED, false) : state;
        }
    }

    @Mixin(StateHolder.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingExclusiveMixin {
        @ModifyReturnValue(method = "setValueInternal", at = @At("RETURN"))
        private <T extends Comparable<T>, V extends T> Object naturality$oneFluid(Object result, Property<T> property, int index, V value) {
            // This erased return also covers trySetValue, cycling, codecs and command property parsing.
            return result instanceof BlockState state ? BlockLavalogging.exclusive(state, property) : result;
        }
    }

    @Mixin(BlockBehaviour.BlockStateBase.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingStateMixin {
        @Shadow protected abstract BlockState asState();

        @ModifyExpressionValue(method = "initCache", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getFluidState(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/material/FluidState;"))
        private FluidState naturality$source(FluidState original) {
            return BlockLavalogging.lavalogged(asState()) ? Fluids.LAVA.getSource(false) : original;
        }

        @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
        private void naturality$light(CallbackInfoReturnable<Integer> cir) {
            if (BlockLavalogging.lavalogged(asState())) cir.setReturnValue(15);
        }

        @ModifyReturnValue(method = "updateShape", at = @At("RETURN"))
        private BlockState naturality$updates(BlockState result, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                BlockPos neighborPos, BlockState neighbor, RandomSource random) {
            return BlockLavalogging.neighborUpdate(asState(), result, level, ticks, pos);
        }
    }

    @Mixin(SimpleWaterloggedBlock.class)
    @org.jspecify.annotations.NullMarked
    public interface LavaloggingContainerMixin {
        @Inject(method = "canPlaceLiquid", at = @At("HEAD"), cancellable = true)
        private void naturality$canFill(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state,
                Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
            if (fluid == Fluids.LAVA || BlockLavalogging.lavalogged(state)) cir.setReturnValue(BlockLavalogging.canFill(state, fluid));
        }

        @Inject(method = "placeLiquid", at = @At("HEAD"), cancellable = true)
        private void naturality$fill(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
            if (fluid.is(Fluids.LAVA) || BlockLavalogging.lavalogged(state)) cir.setReturnValue(BlockLavalogging.fill(level, pos, state, fluid));
        }

        @Inject(method = "pickupBlock", at = @At("HEAD"), cancellable = true)
        private void naturality$pickup(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state, CallbackInfoReturnable<ItemStack> cir) {
            if (BlockLavalogging.lavalogged(state)) cir.setReturnValue(BlockLavalogging.pickup(level, pos, state));
        }
    }

    @Mixin({CandleBlock.class, DriedGhastBlock.class})
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingSpecialContainersMixin {
        @Inject(method = "placeLiquid", at = @At("HEAD"), cancellable = true)
        private void naturality$fill(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
            if (fluid.is(Fluids.LAVA) || BlockLavalogging.lavalogged(state)) cir.setReturnValue(BlockLavalogging.fill(level, pos, state, fluid));
        }
    }

    @Mixin(BucketItem.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingBucketMixin {
        @SuppressWarnings("null") // BucketItem initializes the shadowed fluid in its constructor.
        @Shadow @Final protected Fluid content;

        @ModifyExpressionValue(method = {"use", "emptyContents"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/material/Fluids;WATER:Lnet/minecraft/world/level/material/FlowingFluid;"))
        private net.minecraft.world.level.material.FlowingFluid naturality$containerFluid(net.minecraft.world.level.material.FlowingFluid water) {
            return content == Fluids.LAVA ? Fluids.LAVA : water;
        }

        @WrapOperation(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BucketPickup;getPickupSound()Ljava/util/Optional;"))
        private Optional<SoundEvent> naturality$pickupSound(BucketPickup container, Operation<Optional<SoundEvent>> original,
                @Local(name = "blockState") BlockState state) {
            return BlockLavalogging.lavalogged(state) ? Fluids.LAVA.getPickupSound() : original.call(container);
        }
    }

    @Mixin(BlockItem.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingPlacementMixin {
        @ModifyReturnValue(method = "getPlacementState", at = @At("RETURN"))
        private @Nullable BlockState naturality$placement(@Nullable BlockState state, BlockPlaceContext context) {
            return state == null ? null : BlockLavalogging.placement(state, context.getLevel(), context.getClickedPos());
        }
    }

    @Mixin(DoorBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingDoorMixin {
        @WrapOperation(method = "setPlacedBy", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
        private boolean naturality$secondCell(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
            // The lower half's lava must not leak into a dry upper half.
            if (state.hasProperty(BlockLavalogging.LAVALOGGED)) state = state.setValue(BlockLavalogging.LAVALOGGED, false);
            return original.call(level, pos, BlockLavalogging.placement(state, level, pos));
        }
    }

    @Mixin(AnvilBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingAnvilMixin {
        @ModifyReturnValue(method = "damage", at = @At("RETURN"))
        private static @Nullable BlockState naturality$damage(@Nullable BlockState damaged, BlockState previous) {
            return damaged != null && BlockLavalogging.lavalogged(previous) ? damaged.setValue(BlockLavalogging.LAVALOGGED, true) : damaged;
        }
    }

    @Mixin(FallingBlockEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingFallingMixin {
        @ModifyExpressionValue(method = "fall", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;setValue(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Object;"))
        private static Object naturality$leaveLavaBehind(Object original) {
            return original instanceof BlockState state && state.hasProperty(BlockLavalogging.LAVALOGGED)
                ? state.setValue(BlockLavalogging.LAVALOGGED, false) : original;
        }

        @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
        private boolean naturality$landing(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
            return original.call(level, pos, BlockLavalogging.placement(state, level, pos));
        }
    }

    @Mixin(StructureTemplate.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingTemplateMixin {
        @ModifyExpressionValue(method = "placeInWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;rotate(Lnet/minecraft/world/level/block/Rotation;)Lnet/minecraft/world/level/block/state/BlockState;"))
        private BlockState naturality$sourcePlacement(BlockState state, @Local(argsOnly = true) ServerLevelAccessor level,
                @Local(argsOnly = true) StructurePlaceSettings settings, @Local(name = "blockPos") BlockPos pos) {
            // Capture before the temporary barrier used for block-entity NBT replaces the source.
            return settings.shouldApplyWaterlogging() ? BlockLavalogging.placement(state, level, pos) : state;
        }

        @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/LiquidBlockContainer;placeLiquid(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Z"))
        private boolean naturality$noLavaSourceExpansion(LiquidBlockContainer container, LevelAccessor level, BlockPos pos,
                BlockState state, FluidState fluid, Operation<Boolean> original) {
            // Vanilla's water refill pass also visits air/flowing cells adjacent to sources.
            return !fluid.is(Fluids.LAVA) && original.call(container, level, pos, state, fluid);
        }

        @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
        private boolean naturality$schedule(ServerLevelAccessor level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
            boolean placed = original.call(level, pos, state, flags);
            if (placed && BlockLavalogging.lavalogged(state)) level.scheduleTick(pos, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
            return placed;
        }
    }

    @Mixin(StructurePiece.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LavaloggingPieceMixin {
        @WrapOperation(method = {"placeBlock", "fillColumnDown"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
        private boolean naturality$sourcePlacement(WorldGenLevel level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
            BlockState placed = BlockLavalogging.placement(state, level, pos);
            boolean changed = original.call(level, pos, placed, flags);
            if (changed && BlockLavalogging.lavalogged(placed)) level.scheduleTick(pos, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
            return changed;
        }
    }
}
