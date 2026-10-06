package naturality.starfall;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import naturality.util.LoadedChunks;
import net.minecraft.core.BlockPos;

/** Straight, swept projectiles; fizzles never apply damage, fire or explosion effects. */
public final class FallingStar extends Projectile {
    private int age;
    private int burnTicks=16;
    public FallingStar(EntityType<? extends FallingStar> type,Level level) { super(type,level); setNoGravity(true); }
    /** Smooth fade shared by the model and its attached trail. */
    public float opacity(float partial) {
        float t=Math.clamp((tickCount+partial)/10F,0,1);return t*t*(3-2*t);
    }
    public boolean fizzle() { return getType()==StarfallEntities.FIZZLE; }
    public void launch(Vec3 position,Vec3 velocity,int lifetime) {
        setPos(position);setDeltaMovement(velocity);burnTicks=Math.max(1,lifetime);
        setYRot((float)Math.toDegrees(Math.atan2(velocity.x,velocity.z)));
        setXRot((float)-Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance())));
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance<256*256; }
    @Override public void tick() {
        super.tick();
        if(level().isClientSide()) { setPos(position().add(getDeltaMovement()));return; }
        var server=(ServerLevel)level();
        if(++age>300 || !LoadedChunks.has(server,BlockPos.containing(position().add(getDeltaMovement())))) { discard();return; }
        var hit=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);
        if(fizzle() && (age>=burnTicks || hit.getType()!=HitResult.Type.MISS)) { burst(server);discard();return; }
        if(hit.getType()!=HitResult.Type.MISS) {
            setPos(hit.getLocation());
            if(hit instanceof EntityHitResult entityHit)
                entityHit.getEntity().hurtServer(server,damageSources().magic(),24);
            server.explode(this,getX(),getY(),getZ(),2.5F,Level.ExplosionInteraction.NONE);
            discard();return;
        }
        setPos(position().add(getDeltaMovement()));
    }
    private void burst(ServerLevel level) {
        level.sendParticles(ParticleTypes.END_ROD,getX(),getY(),getZ(),28,.65,.65,.65,.12);
        level.sendParticles(ParticleTypes.POOF,getX(),getY(),getZ(),12,.35,.35,.35,.06);
    }
}