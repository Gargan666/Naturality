package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockItem.class)
public abstract class SnowPlacementMixin {
    @WrapMethod(method="place")
    private InteractionResult naturality$fitSnowPlacement(BlockPlaceContext context, Operation<InteractionResult> original) {
        if (naturality.snow.ShapeRecursionGuard.active()) return original.call(context);
        if (!naturality.config.GameplaySettings.snowWrapping(context.getLevel())) return original.call(context);
        if (((BlockItem)(Object)this).getBlock() != Blocks.SNOW) {
            var level=context.getLevel();var pos=context.getClickedPos();
            // A side hit on snow displaced into a lower cell still names its
            // saved owner. Place against the neighboring cell at the hit height.
            var face=context.getClickedFace();
            var hit=context.getClickLocation();
            var owner=context.replacingClickedOnBlock() ? pos : pos.relative(face.getOpposite());
            var ownerState=level.getBlockState(owner);
            if(face.getAxis().isHorizontal() && ownerState.is(Blocks.SNOW) && hit.y < owner.getY()) {
                var shape=naturality.snow.SnowGeometry.shape(level,owner,
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
            boolean emptyOwner=previous.is(Blocks.SNOW) && naturality.snow.SnowGeometry.emptyOwnerCell(level,pos,
                previous.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
            var result=original.call(context);
            if(result.consumesAction() && previous.is(Blocks.SNOW)
                    && !emptyOwner
                    && !level.getBlockState(pos).is(Blocks.SNOW)
                    && naturality.snow.SnowGeometry.exposesGround(level,pos)) {
                var destination=pos.above();
                if(level.getBlockState(destination).isAir() && previous.canSurvive(level,destination))
                    level.setBlock(destination,previous,3);
            }
            return result;
        }
        var level = context.getLevel();
        var target = context.getClickedPos();
        if (naturality.snow.NoSnowBlocks.contains(level.getBlockState(target))) return InteractionResult.FAIL;
        // A displaced snow hit still names its saved owner. Vanilla offsets side
        // hits to an adjacent cell before we get here, so recover that owner first.
        var clicked=context.replacingClickedOnBlock() ? target : target.relative(context.getClickedFace().getOpposite());
        var clickedState=level.getBlockState(clicked);
        // Snow used on a flame extinguishes it at its saved position.
        if(clickedState.getBlock() instanceof BaseFireBlock)
            return original.call(BlockPlaceContext.at(context,clicked,Direction.UP));
        if(level.getBlockState(target).getBlock() instanceof BaseFireBlock)
            return original.call(BlockPlaceContext.at(context,target,Direction.UP));
        if(clickedState.is(Blocks.SNOW) && naturality.snow.SnowGeometry.surfaces(level,clicked).stream().anyMatch(p -> p.y()<0))
            return original.call(BlockPlaceContext.at(context,clicked,Direction.UP));
        // Placement can also target an air/support cell beneath the saved snow.
        // Resolve it before the air fast path creates a short-lived second block.
        var hit=context.getClickLocation();
        for(int up=0;up<naturality.snow.SnowGeometry.MAX_DEPTH;up++) {
            var owner=target.above(up);var candidate=level.getBlockState(owner);
            if(candidate.is(Blocks.SNOW)) {
                var shape=naturality.snow.SnowGeometry.shape(level,owner,
                    candidate.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
                var local=hit.subtract(owner.getX(),owner.getY(),owner.getZ());
                if(shape.toAabbs().stream().anyMatch(b -> local.x>=b.minX-1e-5 && local.x<=b.maxX+1e-5
                        && local.z>=b.minZ-1e-5 && local.z<=b.maxZ+1e-5
                        && local.y>=b.minY-.125-1e-5 && local.y<=b.maxY+1e-5))
                    return original.call(BlockPlaceContext.at(context,owner,Direction.UP));
                break;
            }
            if(up>=2 && !naturality.snow.SnowGeometry.exposesGround(level,owner))break;
        }
        var state = level.getBlockState(target);
        if (state.isAir() || state.is(Blocks.SNOW)) return original.call(context);
        // Snow must never replace a plant. Resolve the occupied partial cell to
        // the saved snow above it before vanilla rejects/replaces the target.
        for (int step=0; step<naturality.snow.SnowGeometry.MAX_DEPTH-1; step++) {
            if (state.getBlock() instanceof LiquidBlock || Block.isShapeFullBlock(state.getShape(level,target)))
                return InteractionResult.FAIL;
            if (step>=2 && !naturality.snow.SnowGeometry.exposesGround(level,target)) return InteractionResult.FAIL;
            target = target.above();
            state = level.getBlockState(target);
            if (state.isAir() || state.is(Blocks.SNOW))
                return original.call(BlockPlaceContext.at(context,target,Direction.UP));
        }
        return InteractionResult.FAIL;
    }
}





