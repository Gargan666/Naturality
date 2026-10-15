package naturality.entities.ghasts.mixins;

import naturality.entities.ghasts.GhastCrash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.entities.ghasts.GhastCrash.GhastCrashBody;
import naturality.api.motion.GhastMotion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Unique;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class GhastsMixins {
    private GhastsMixins() {}

    @Mixin(ExperienceOrb.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GhastCrashExperienceMixin {
        @Inject(method="awardWithDirection",at=@At("HEAD"),cancellable=true)
        private static void naturality$holdXP(ServerLevel level,Vec3 pos,Vec3 direction,int amount,CallbackInfo ci) {
            if (GhastCrash.captureExperience(amount)) ci.cancel();
        }
    }

    /** Also holds equipment/leads shed outside the main death-loot call. */
    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GhastCrashItemMixin {
        @Inject(method="spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("HEAD"),cancellable=true)
        private void naturality$holdAttachedDrops(ServerLevel level,ItemStack stack,Vec3 offset,CallbackInfoReturnable<ItemEntity> ci) {
            if (!((Object)this instanceof LivingEntity entity) || !GhastMotion.supported(entity) || !entity.isDeadOrDying()) return;
            GhastCrash.Flight flight=((GhastCrashBody)entity).naturality$flight();
            if (flight==null || stack.isEmpty()) return;
            flight.drops.add(stack.copy());
            ci.setReturnValue(new ItemEntity(level,entity.getX(),entity.getY(),entity.getZ(),stack.copy()));
        }
    }

    @Mixin(ServerLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GhastCrashLootMixin {
        @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true)
        private void naturality$holdLoot(Entity entity,CallbackInfoReturnable<Boolean> ci) {
            if (GhastCrash.captureSpawn(entity)) ci.setReturnValue(true);
        }
    }

    @Mixin(LivingEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GhastCrashMixin implements GhastCrashBody {
        @Unique private static final EntityDataAccessor<Integer> IMPACT_TICKS=SynchedEntityData.defineId(LivingEntity.class,EntityDataSerializers.INT);
        public int naturality$impactTicks() { return ((LivingEntity)(Object)this).getEntityData().get(IMPACT_TICKS); }
        public void naturality$impactTicks(int ticks) { ((LivingEntity)(Object)this).getEntityData().set(IMPACT_TICKS,ticks); }
        @Unique private static final EntityDataAccessor<Float> KICK_X=SynchedEntityData.defineId(LivingEntity.class,EntityDataSerializers.FLOAT);
        @Unique private static final EntityDataAccessor<Float> KICK_Y=SynchedEntityData.defineId(LivingEntity.class,EntityDataSerializers.FLOAT);
        @Unique private static final EntityDataAccessor<Float> KICK_Z=SynchedEntityData.defineId(LivingEntity.class,EntityDataSerializers.FLOAT);
        public Vec3 naturality$crashImpulse() {
            var data=((LivingEntity)(Object)this).getEntityData();
            return new Vec3(data.get(KICK_X),data.get(KICK_Y),data.get(KICK_Z));
        }
        public void naturality$crashImpulse(Vec3 impulse) {
            var data=((LivingEntity)(Object)this).getEntityData();
            data.set(KICK_X,(float)impulse.x);data.set(KICK_Y,(float)impulse.y);data.set(KICK_Z,(float)impulse.z);
        }
        @Unique private static final EntityDataAccessor<Float> TURN_X=SynchedEntityData.defineId(LivingEntity.class,EntityDataSerializers.FLOAT);
        @Unique private static final EntityDataAccessor<Float> TURN_Y=SynchedEntityData.defineId(LivingEntity.class,EntityDataSerializers.FLOAT);
        @Unique private static final EntityDataAccessor<Float> TURN_Z=SynchedEntityData.defineId(LivingEntity.class,EntityDataSerializers.FLOAT);
        public Vec3 naturality$crashRotation() {
            var data=((LivingEntity)(Object)this).getEntityData();
            return new Vec3(data.get(TURN_X),data.get(TURN_Y),data.get(TURN_Z));
        }
        public void naturality$crashRotation(Vec3 rotation) {
            var data=((LivingEntity)(Object)this).getEntityData();
            data.set(TURN_X,(float)rotation.x); data.set(TURN_Y,(float)rotation.y); data.set(TURN_Z,(float)rotation.z);
        }
        @Unique private GhastCrash.@org.jspecify.annotations.Nullable Flight naturality$flight;
        public GhastCrash.@org.jspecify.annotations.Nullable Flight naturality$flight() { return naturality$flight; }
        public void naturality$flight(GhastCrash.@org.jspecify.annotations.Nullable Flight flight) { naturality$flight = flight; }
        @Inject(method="defineSynchedData", at=@At("TAIL"))
        private void naturality$data(SynchedEntityData.Builder builder, CallbackInfo ci) {
            builder.define(IMPACT_TICKS,-1);
            builder.define(TURN_X,0F);builder.define(TURN_Y,0F);builder.define(TURN_Z,0F);
            builder.define(KICK_X,0F);builder.define(KICK_Y,0F);builder.define(KICK_Z,0F);
        }
        @Inject(method="die",at=@At("HEAD"))
        private void naturality$die(DamageSource source, CallbackInfo ci) { GhastCrash.begin((LivingEntity)(Object)this,source); }
        @WrapMethod(method="dropAllDeathLoot")
        private void naturality$loot(ServerLevel level, DamageSource source, Operation<Void> original) {
            if (naturality$flight == null) original.call(level,source);
            else GhastCrash.capture(naturality$flight, () -> original.call(level,source));
        }
        @Inject(method="tickDeath",at=@At("HEAD"),cancellable=true)
        private void naturality$deathTick(CallbackInfo ci) {
            LivingEntity entity = (LivingEntity)(Object)this;
            if (!GhastMotion.supported(entity)) return;
            GhastCrash.begin(entity); entity.deathTime++; ci.cancel();
        }
        @Inject(method="aiStep",at=@At("HEAD"),cancellable=true)
        private void naturality$noDeathPhysics(CallbackInfo ci) {
            LivingEntity entity = (LivingEntity)(Object)this;
            if (GhastMotion.supported(entity) && entity.isDeadOrDying()) ci.cancel();
        }
        @Inject(method="tick",at=@At("TAIL"))
        private void naturality$tick(CallbackInfo ci) {
            LivingEntity entity = (LivingEntity)(Object)this;
            if (!GhastMotion.supported(entity)) return;
            if (entity.isDeadOrDying()) GhastCrash.tick(entity);
            GhastMotion.tick(entity);
        }
        @Inject(method="getPassengerRidingPosition",at=@At("RETURN"),cancellable=true)
        private void naturality$seat(Entity rider, CallbackInfoReturnable<Vec3> ci) {
            if ((Object)this instanceof HappyGhast ghast && ghast.isAlive()) ci.setReturnValue(GhastMotion.seat(ghast,ci.getReturnValue()));
        }
        @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
        private void naturality$save(ValueOutput output,CallbackInfo ci) { GhastCrash.save((LivingEntity)(Object)this,output); }
        @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
        private void naturality$load(ValueInput input,CallbackInfo ci) { GhastCrash.load((LivingEntity)(Object)this,input); }
    }
}
