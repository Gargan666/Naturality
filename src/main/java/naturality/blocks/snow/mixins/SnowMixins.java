package naturality.blocks.snow.mixins;

import naturality.blocks.snow.SnowGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.ClipContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.blocks.snow.SnowGeometry.SnowWorldgen;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.SnowyBlock;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class SnowMixins {
    private SnowMixins() {}

    @Mixin(BlockBehaviour.BlockStateBase.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowCollisionMixin {
        @Inject(method = "isViewBlocking", at = @At("HEAD"), cancellable = true)
        private void naturality$displacedSnowDoesNotBlockCamera(BlockGetter level, BlockPos pos, AABB nearPlane,
                CallbackInfoReturnable<Boolean> cir) {

            var state = (BlockState)(Object)this;
            if (naturality.blocks.ash.Ash.isLayer(state) || state.is(Blocks.SNOW) && naturality.config.gameplay.GameplaySettings.snowWrapping(level) && !SnowGeometry.usesVanillaGeometry(level, pos))
                cir.setReturnValue(false);
        }
        @ModifyReturnValue(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"))
        private VoxelShape naturality$cachedSnowCollision(VoxelShape original, BlockGetter level, BlockPos pos) {
            return naturality$fitCollision(original,level,pos);
        }
        @ModifyReturnValue(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"))
        private VoxelShape naturality$snowCollision(VoxelShape original, BlockGetter level, BlockPos pos, CollisionContext context) {
            return naturality$fitCollision(original,level,pos);
        }
        private VoxelShape naturality$fitCollision(VoxelShape result, BlockGetter level, BlockPos pos) {
            var state=(BlockState)(Object)this;
            // Keep the ordinary solid-block path small enough to inline through
            // cooperative wrappers; the actual fitting belongs to the feature.
            if(result==Shapes.block() && !state.is(Blocks.SNOW) && !naturality.blocks.ash.Ash.isLayer(state))return result;
            return SnowGeometry.fitCollision(result,level,pos,state);
        }
    }

    @Mixin(ServerGamePacketListenerImpl.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowInteractionMixin {
        @SuppressWarnings("null") @Shadow public ServerPlayer player;
        private boolean naturality$validSnowHit(ServerboundUseItemOnPacket packet) {
            var hit=packet.hitResult();var pos=hit.getBlockPos();var state=player.level().getBlockState(pos);
            boolean displaced=!naturality.systems.weather.wind.WindPush.WindShapes.offset(player.level(),pos,state).equals(Vec3.ZERO);
            if(!displaced && (!naturality.config.gameplay.GameplaySettings.snowWrapping(player.level()) || !state.is(Blocks.SNOW)) && !naturality.blocks.ash.Ash.isLayer(state))return false;
            var point=hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
            return state.getShape(player.level(),pos).toAabbs()
                .stream().anyMatch(b -> b.inflate(1e-5).contains(point));
        }
        @WrapOperation(method="handleUseItemOn",at=@At(value="INVOKE",
            target="Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"))
        private boolean naturality$snowReach(ServerPlayer player,BlockPos pos,double padding,Operation<Boolean> original,
                ServerboundUseItemOnPacket packet) {
            boolean result=original.call(player,naturality$validSnowHit(packet)?BlockPos.containing(packet.hitResult().getLocation()):pos,padding);
            return result;
        }
        @ModifyExpressionValue(method="handleUseItemOn",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
        private Vec3 naturality$snowHitBounds(Vec3 distance,ServerboundUseItemOnPacket packet) {
            // Only a point on the actual snow or wind-shifted shape may bypass unit-cell bounds.
            return naturality$validSnowHit(packet)?Vec3.ZERO:distance;
        }
    }

    @Mixin(SnowLayerBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowLayerMixin {
        @Inject(method="updateShape",at=@At("HEAD"),cancellable=true)
        private void naturality$unchangedSupport(BlockState state,LevelReader level,net.minecraft.world.level.ScheduledTickAccess ticks,
                BlockPos pos,net.minecraft.core.Direction direction,BlockPos neighborPos,BlockState neighbor,
                net.minecraft.util.RandomSource random,CallbackInfoReturnable<BlockState> cir) {
            // Partial snow beside this cell cannot change this column's support.
            // Vertical updates (including removing the support) always take the normal path.
            if(naturality.config.gameplay.GameplaySettings.snowWrapping(level)
                    && direction.getAxis()!=net.minecraft.core.Direction.Axis.Y
                    && neighbor.is(net.minecraft.world.level.block.Blocks.SNOW)
                    && neighbor.getValue(SnowLayerBlock.LAYERS)<8)cir.setReturnValue(state);
        }
        @Inject(method = "canBeReplaced(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/context/BlockPlaceContext;)Z", at = @At("HEAD"), cancellable = true)
        private void naturality$replaceEmptyOwner(BlockState state, BlockPlaceContext context, CallbackInfoReturnable<Boolean> cir) {
            var level = context.getLevel();
            var pos = context.getClickedPos();
            if (naturality.config.gameplay.GameplaySettings.snowWrapping(level) && level.getBlockState(pos) == state
                    && SnowGeometry.emptyOwnerCell(level, pos, state.getValue(SnowLayerBlock.LAYERS)))
                cir.setReturnValue(true);
        }
        @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
        private void naturality$support(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            if (naturality.blocks.snow.SnowGeometry.NoSnowBlocks.contains(level.getBlockState(pos))
                    || naturality.blocks.snow.SnowGeometry.NoSnowBlocks.contains(level.getBlockState(pos.below()))) {
                cir.setReturnValue(false);
                return;
            }
            if (naturality.api.geometry.ShapeRecursionGuard.active()) return;
            if (!naturality.config.gameplay.GameplaySettings.snowWrapping(level)) return;
            cir.setReturnValue(!SnowGeometry.surfaces(level, pos).isEmpty());
        }
        @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
        private void naturality$outline(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
            if (!naturality.config.gameplay.GameplaySettings.snowWrapping(level)) return;
            cir.setReturnValue(SnowGeometry.shape(level, pos, state.getValue(SnowLayerBlock.LAYERS)));
        }
        @Inject(method = "getVisualShape", at = @At("HEAD"), cancellable = true)
        private void naturality$lightingShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
            if (!naturality.config.gameplay.GameplaySettings.snowWrapping(level) || SnowGeometry.usesVanillaGeometry(level, pos)) return;
            // The snow mesh may be below this block. Its saved cell must not shade
            // neighboring faces as though the fitted mesh were still here.
            cir.setReturnValue(Shapes.empty());
        }
        @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
        private void naturality$lightingBrightness(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
            if (naturality.config.gameplay.GameplaySettings.snowWrapping(level) && !SnowGeometry.usesVanillaGeometry(level, pos))
                cir.setReturnValue(1.0F);
        }
        @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
        private void naturality$collision(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
            if (!naturality.config.gameplay.GameplaySettings.snowWrapping(level)) return;
            cir.setReturnValue(SnowGeometry.collisionShape(level, pos, state.getValue(SnowLayerBlock.LAYERS)));
        }
        @Inject(method = "getBlockSupportShape", at = @At("HEAD"), cancellable = true)
        private void naturality$blockSupport(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
            if (!naturality.config.gameplay.GameplaySettings.snowWrapping(level)) return;
            cir.setReturnValue(SnowGeometry.shape(level, pos, state.getValue(SnowLayerBlock.LAYERS)));
        }
    }

    @Mixin(LightEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowLightEngineMixin {
        @WrapOperation(method="getState", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/chunk/LightChunk;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
        private BlockState naturality$snowLight(LightChunk chunk, BlockPos pos, Operation<BlockState> original) {
            return naturality.blocks.snow.SnowGeometry.SnowLighting.state(chunk,pos,original.call(chunk,pos));
        }
    }

    @Mixin(BlockGetter.class)
    @org.jspecify.annotations.NullMarked
    public interface SnowPickingMixin {
        @WrapMethod(method="clip")
        private BlockHitResult naturality$snowContext(ClipContext context, Operation<BlockHitResult> original) {

            var previous=SnowGeometry.PICK.get(); SnowGeometry.PICK.set(context);
            try { return original.call(context); }
            finally { if(previous==null) SnowGeometry.PICK.remove(); else SnowGeometry.PICK.set(previous); }
        }
        @Inject(method="clipWithInteractionOverride",at=@At("RETURN"),cancellable=true)
        private void naturality$snowHit(Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state,
                CallbackInfoReturnable<BlockHitResult> cir) {

            var context=SnowGeometry.PICK.get(); if(context==null)return;
            BlockGetter level=(BlockGetter)this;
            if(!naturality.api.geometry.RaycastShapePresence.mayContain(level,naturality.api.geometry.RaycastShapePresence.SNOW,
                    pos.getX(),pos.getY()+1,pos.getZ(),pos.getX(),pos.getY()+SnowGeometry.MAX_DEPTH,pos.getZ()))return;
            var best=cir.getReturnValue(); double distance=best==null?Double.POSITIVE_INFINITY:best.getLocation().distanceToSqr(from);
            for(int offset=1;offset<=SnowGeometry.MAX_DEPTH;offset++) {
                var owner=pos.above(offset);var snow=level.getBlockState(owner);if(!naturality.blocks.ash.Ash.isLayer(snow) && !(snow.is(Blocks.SNOW) && naturality.config.gameplay.GameplaySettings.snowWrapping(level))) { if(offset>=2 && !SnowGeometry.exposesGround(level,owner)) break; continue; }
                var candidateShape=context.getBlockShape(snow,level,owner);
                if(!naturality.api.geometry.RaycastShapePresence.mayIntersect(candidateShape,from,to,owner))continue;
                var hit=candidateShape.clip(from,to,owner);
                if(hit!=null && new AABB(pos).inflate(1e-6).contains(hit.getLocation()) && hit.getLocation().distanceToSqr(from)<distance) {
                    best=hit;distance=hit.getLocation().distanceToSqr(from);
                }
            }
            if (best != cir.getReturnValue()) cir.setReturnValue(best);
        }
    }

    @Mixin(BlockItem.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowPlacementMixin {
        @org.spongepowered.asm.mixin.Unique
        private InteractionResult naturality$placeSnow(BlockPlaceContext context, Operation<InteractionResult> original) {
            var pos=context.getClickedPos();
            var state=context.getLevel().getBlockState(pos);
            var below=context.getLevel().getBlockState(pos.below());
            if(!state.is(Blocks.SNOW) && below.is(Blocks.SNOW)
                    && below.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)>=7
                    && naturality.blocks.snow.SnowGeometry.maxLayers(context.getLevel(),pos.below())==7)
                return InteractionResult.FAIL;
            if(state.is(Blocks.SNOW) && state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)>=7
                    && naturality.blocks.snow.SnowGeometry.maxLayers(context.getLevel(),pos)==7)
                return InteractionResult.FAIL;
            return original.call(context);
        }
        @WrapMethod(method="place")
        private InteractionResult naturality$fitSnowPlacement(BlockPlaceContext context, Operation<InteractionResult> original) {
            if (naturality.api.geometry.ShapeRecursionGuard.active()) return original.call(context);
            if (!naturality.config.gameplay.GameplaySettings.snowWrapping(context.getLevel())) return original.call(context);
            if (((BlockItem)(Object)this).getBlock() != Blocks.SNOW) {
                var level=context.getLevel();var pos=context.getClickedPos();
                // A side hit on snow displaced into a lower cell still names its
                // saved owner. Place against the neighboring cell at the hit height.
                var face=context.getClickedFace();
                var hit=context.getClickLocation();
                var owner=context.replacingClickedOnBlock() ? pos : pos.relative(face.getOpposite());
                var ownerState=level.getBlockState(owner);
                if(face.getAxis().isHorizontal() && ownerState.is(Blocks.SNOW) && hit.y < owner.getY()) {
                    var shape=naturality.blocks.snow.SnowGeometry.shape(level,owner,
                        ownerState.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
                    var local=hit.subtract(owner.getX(),owner.getY(),owner.getZ());
                    if(shape.toAabbs().stream().anyMatch(b -> local.y>=b.minY-1e-5 && local.y<=b.maxY+1e-5
                            && local.x>=b.minX-1e-5 && local.x<=b.maxX+1e-5
                            && local.z>=b.minZ-1e-5 && local.z<=b.maxZ+1e-5)) {
                        var side=new BlockPos(owner.getX(),net.minecraft.util.Mth.floor(hit.y),owner.getZ()).relative(face);
                        var redirected=new BlockPlaceContext(level,context.getPlayer(),context.getHand(),context.getItemInHand(),
                            new BlockHitResult(hit,face,side,false));
                        if(!redirected.getClickedPos().equals(side)) return InteractionResult.FAIL;
                        return original.call(redirected);
                    }
                }
                var previous=level.getBlockState(pos);
                boolean emptyOwner=previous.is(Blocks.SNOW) && naturality.blocks.snow.SnowGeometry.emptyOwnerCell(level,pos,
                    previous.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
                var result=original.call(context);
                if(result.consumesAction() && previous.is(Blocks.SNOW)
                        && !emptyOwner
                        && !level.getBlockState(pos).is(Blocks.SNOW)
                        && naturality.blocks.snow.SnowGeometry.exposesGround(level,pos)) {
                    var destination=pos.above();
                    if(level.getBlockState(destination).isAir() && previous.canSurvive(level,destination))
                        level.setBlock(destination,previous,3);
                }
                return result;
            }
            var level = context.getLevel();
            var target = context.getClickedPos();
            if (naturality.blocks.snow.SnowGeometry.NoSnowBlocks.contains(level.getBlockState(target))) return InteractionResult.FAIL;
            // A displaced snow hit still names its saved owner. Vanilla offsets side
            // hits to an adjacent cell before we get here, so recover that owner first.
            var clicked=context.replacingClickedOnBlock() ? target : target.relative(context.getClickedFace().getOpposite());
            var clickedState=level.getBlockState(clicked);
            // Snow used on a flame extinguishes it at its saved position.
            if(clickedState.getBlock() instanceof BaseFireBlock)
                return naturality$placeSnow(BlockPlaceContext.at(context,clicked,Direction.UP),original);
            if(level.getBlockState(target).getBlock() instanceof BaseFireBlock)
                return naturality$placeSnow(BlockPlaceContext.at(context,target,Direction.UP),original);
            if(clickedState.is(Blocks.SNOW) && naturality.blocks.snow.SnowGeometry.surfaces(level,clicked).stream().anyMatch(p -> p.y()<0))
                return naturality$placeSnow(BlockPlaceContext.at(context,clicked,Direction.UP),original);
            // Placement can also target an air/support cell beneath the saved snow.
            // Resolve it before the air fast path creates a short-lived second block.
            var hit=context.getClickLocation();
            for(int up=0;up<naturality.blocks.snow.SnowGeometry.MAX_DEPTH;up++) {
                var owner=target.above(up);var candidate=level.getBlockState(owner);
                if(candidate.is(Blocks.SNOW)) {
                    var shape=naturality.blocks.snow.SnowGeometry.shape(level,owner,
                        candidate.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
                    var local=hit.subtract(owner.getX(),owner.getY(),owner.getZ());
                    if(shape.toAabbs().stream().anyMatch(b -> local.x>=b.minX-1e-5 && local.x<=b.maxX+1e-5
                            && local.z>=b.minZ-1e-5 && local.z<=b.maxZ+1e-5
                            && local.y>=b.minY-.125-1e-5 && local.y<=b.maxY+1e-5))
                        return naturality$placeSnow(BlockPlaceContext.at(context,owner,Direction.UP),original);
                    break;
                }
                if(up>=2 && !naturality.blocks.snow.SnowGeometry.exposesGround(level,owner))break;
            }
            var state = level.getBlockState(target);
            if (state.isAir() || state.is(Blocks.SNOW)) return naturality$placeSnow(context,original);
            // Snow must never replace a plant. Resolve the occupied partial cell to
            // the saved snow above it before vanilla rejects/replaces the target.
            for (int step=0; step<naturality.blocks.snow.SnowGeometry.MAX_DEPTH-1; step++) {
                // Double plants use a full-cell selection outline even though they
                // are foliage. Keep scanning through both halves to the snow owner.
                if (state.getBlock() instanceof LiquidBlock || !naturality.blocks.snow.SnowGeometry.isFoliage(state)
                        && Block.isShapeFullBlock(state.getShape(level,target)))
                    return InteractionResult.FAIL;
                if (step>=2 && !naturality.blocks.snow.SnowGeometry.exposesGround(level,target)) return InteractionResult.FAIL;
                target = target.above();
                state = level.getBlockState(target);
                if (state.isAir() || state.is(Blocks.SNOW))
                    return naturality$placeSnow(BlockPlaceContext.at(context,target,Direction.UP),original);
            }
            return InteractionResult.FAIL;
        }
    }

    @Mixin(ChunkSkyLightSources.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowSkySourcesMixin {
        @WrapOperation(method={"update","findLowestSourceBelow"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
        private BlockState naturality$updatedSnow(BlockGetter level, BlockPos pos, Operation<BlockState> original) {
            return naturality.blocks.snow.SnowGeometry.SnowLighting.state(level,pos,original.call(level,pos));
        }
        @WrapOperation(method="findLowestSourceY", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/chunk/LevelChunkSection;getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;"))
        private BlockState naturality$initialSnow(LevelChunkSection section, int x, int y, int z, Operation<BlockState> original,
                @Local(argsOnly=true) ChunkAccess chunk, @Local(ordinal=1) BlockPos.MutableBlockPos bottomPos) {
            var state=original.call(section,x,y,z);
            if(!state.is(net.minecraft.world.level.block.Blocks.SNOW))return state;
            var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,bottomPos.getY(),chunk.getPos().getMinBlockZ()+z);
            return naturality.blocks.snow.SnowGeometry.SnowLighting.state(chunk,pos,state);
        }
    }

    @Mixin(Level.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowStateMixin {
        @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
        private boolean naturality$snowChange(BlockPos pos, BlockState state, int flags, int limit, Operation<Boolean> original) {
            Level level = (Level)(Object)this;
            if (!naturality.config.gameplay.GameplaySettings.snowWrapping(level)) return original.call(pos, state, flags, limit);
            // Every support allows at least seven layers; only an eighth needs geometry resolution.
            if (state.is(Blocks.SNOW) && state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==8) {
                int maximum=SnowGeometry.maxLayers(level,pos);
                if(maximum<8) state=state.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS,maximum);
            }
            var previous=level.getBlockState(pos);
            boolean wasSnow = previous.is(Blocks.SNOW);
            boolean sameCoverage=wasSnow && state.is(Blocks.SNOW)
                && previous.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)<8
                && state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)<8;
            // Move the saved snow before neighbor updates can destroy it as unsupported.
            // Keep the layer count (including overflow blocks), rather than dropping
            // snow items and trying to reconstruct the coating afterward.
            java.util.List<BlockState> snowColumn = java.util.List.of();
            if (!level.isClientSide() && state.isAir() && level.getBlockState(pos.above()).is(Blocks.SNOW)
                    && SnowGeometry.exposesGround(level,pos)
                    && level.getBlockState(pos.above()).canSurvive(level,pos)) {
                snowColumn = new java.util.ArrayList<>();
                for (int i=1;i<=SnowGeometry.MAX_DEPTH;i++) {
                    var snow=level.getBlockState(pos.above(i));
                    if(!snow.is(Blocks.SNOW))break;
                    snowColumn.add(snow);
                }
                // Suppress shape updates until the column is seated at its new origin.
                for(int i=0;i<snowColumn.size();i++)
                    level.setBlock(pos.above(i+1),Blocks.AIR.defaultBlockState(),18);
            }
            boolean result = original.call(pos, snowColumn.isEmpty()?state:snowColumn.getFirst(), flags, limit);
            if(!snowColumn.isEmpty()) {
                if(result) {
                    for(int i=1;i<snowColumn.size();i++)level.setBlock(pos.above(i),snowColumn.get(i),flags);
                } else {
                    for(int i=0;i<snowColumn.size();i++)level.setBlock(pos.above(i+1),snowColumn.get(i),flags);
                }
            }
            if (result && !sameCoverage && !level.isClientSide() && (wasSnow || state.is(Blocks.SNOW)
                    || level.getBlockState(pos.above()).is(Blocks.SNOW))) SnowGeometry.refreshGrass(level, pos);
            return result;
        }
    }

    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowStepSoundMixin {
        @Shadow protected abstract void playStepSound(BlockPos pos, BlockState state);

        @ModifyExpressionValue(method = "spawnSprintParticle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
        private BlockState naturality$contactSnowParticles(BlockState state) {
            return (Object)this instanceof net.minecraft.world.entity.player.Player player
                ? SnowGeometry.walkingParticleState(player, state) : state;
        }

        @Inject(method = "walkingStepSound", at = @At("HEAD"), cancellable = true)
        private void naturality$displacedSnowStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
            Entity entity = (Entity)(Object)this;
            if (state.is(Blocks.SNOW) || naturality.blocks.ash.Ash.isLayer(state)) return;
            var contact = naturality.api.geometry.SurfaceLayers.footstepState(entity.level(),pos,entity.getBoundingBox(),
                naturality.config.gameplay.GameplaySettings.snowWrapping(entity.level()));
            if (contact != null) {
                this.playStepSound(pos, contact);
                ci.cancel();
            }
        }
    }

    @Mixin(SnowAndFreezeFeature.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowWorldgenMixin {
        @Inject(method="place",at=@At("RETURN"))
        private void naturality$snowOverPlants(WorldGenLevel level,ChunkGenerator generator,RandomSource random,
                BlockPos origin,CallbackInfoReturnable<Boolean> cir) {
            if(cir.getReturnValueZ())SnowWorldgen.placeOnFoliage(level,origin);
        }
    }

    @Mixin(SnowyBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowyGrassMixin {
        @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
        private void naturality$covered(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                BlockPos pos, Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random,
                CallbackInfoReturnable<BlockState> cir) {
            if (!naturality.config.gameplay.GameplaySettings.snowWrapping(level)) return;
            if (cir.getReturnValue().hasProperty(SnowyBlock.SNOWY))
                cir.setReturnValue(cir.getReturnValue().setValue(SnowyBlock.SNOWY, SnowGeometry.coveredGrass(level, pos)));
        }
    }
}
