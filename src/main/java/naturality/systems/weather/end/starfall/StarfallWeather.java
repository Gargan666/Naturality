package naturality.systems.weather.end.starfall;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import naturality.api.worlds.LoadedChunks;
import naturality.Naturality;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Bounded per-player weather volume in loaded chunks; no terrain generation requests. */
@org.jspecify.annotations.NullMarked
public final class StarfallWeather {
    private StarfallWeather() {}
    public static void tick(ServerLevel level,int strength) {
        if(strength<=0 || !level.dimension().equals(Level.END))return;
        var random=level.getRandom();
        for(var player:level.players()) {
            if(player.isSpectator())continue;
            double chance=.025+.225*Math.pow(strength/100.0,2);
            if(random.nextDouble()>chance)continue;
            double angle=random.nextDouble()*Math.PI*2, radius=48+random.nextDouble()*28;
            double x=player.getX()+Math.cos(angle)*radius,z=player.getZ()+Math.sin(angle)*radius;
            boolean above=random.nextBoolean();
            // The End's islands generally sit near Y=64. Both streams aim through that band.
            var start=new Vec3(x,above?240+random.nextDouble()*24:-48,z);
            double heading=angle+Math.PI+(random.nextDouble()-.5)*1.2;
            double slope=.35+random.nextDouble()*.3;
            double horizontal=Math.abs(start.y-64)*slope;
            var target=new Vec3(x+Math.cos(heading)*horizontal,64,z+Math.sin(heading)*horizontal);
            if(!LoadedChunks.has(level,BlockPos.containing(start)) || !LoadedChunks.has(level,BlockPos.containing(target)))continue;
            if(!level.getWorldBorder().isWithinBounds(BlockPos.containing(start)))continue;
            // Avoid multiplying the same local shower when players stand together.
            boolean duplicate=false;
            for(var other:level.players())if(other!=player && other.getId()<player.getId() && !other.isSpectator()
                    && other.position().distanceToSqr(start)<player.position().distanceToSqr(start)) {duplicate=true;break;}
            if(duplicate)continue;
            int nearby=level.getEntitiesOfClass(FallingStar.class,new net.minecraft.world.phys.AABB(player.getX()-112,level.getMinY()-64,player.getZ()-112,
                player.getX()+112,level.getMaxY()+32,player.getZ()+112)).size();
            if(nearby>=64)continue;
            var type=random.nextFloat()<.65?StarfallEntities.FIZZLE:StarfallEntities.STAR;
            var star=new FallingStar(type,level);
            star.launch(start,target.subtract(start).normalize().scale(1.4+random.nextDouble()*.8),8+random.nextInt(20));
            level.addFreshEntity(star);
        }
    }

    public static final class StarfallEntities {
        public static final EntityType<FallingStar> STAR = register("falling_star");
        public static final EntityType<FallingStar> FIZZLE = register("fizzle");
        private static EntityType<FallingStar> register(String name) {
            var id=Naturality.id(name);
            return Registry.register(BuiltInRegistries.ENTITY_TYPE,id,EntityType.Builder.<FallingStar>of(FallingStar::new,MobCategory.MISC)
                .sized(.65F,.65F).clientTrackingRange(16).updateInterval(1).noSave()
                .build(ResourceKey.create(Registries.ENTITY_TYPE,id)));
        }
        public static void initialize() {}
        private StarfallEntities() {}
    }

    /** Straight, swept projectiles; fizzles never apply damage, fire or explosion effects. */
    public static final class FallingStar extends Projectile {
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
}
