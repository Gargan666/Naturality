package naturality.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SnowInteractionMixin {
    @SuppressWarnings("null") @Shadow public ServerPlayer player;
    private boolean naturality$validSnowHit(ServerboundUseItemOnPacket packet) {
        var hit=packet.hitResult();var pos=hit.getBlockPos();var state=player.level().getBlockState(pos);
        boolean displaced=!naturality.weather.WindShapes.offset(player.level(),pos,state).equals(Vec3.ZERO);
        if(!displaced && (!naturality.config.GameplaySettings.snowWrapping(player.level()) || !state.is(Blocks.SNOW)))return false;
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
