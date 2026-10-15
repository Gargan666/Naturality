package naturality.entities.ghasts;

import naturality.api.motion.GhastMotion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Server-driven death flight with vanilla loot captured until the impact. */
public final class GhastCrash {
    private static final ThreadLocal<Flight> CAPTURE = new ThreadLocal<>();
    private GhastCrash() { }
    public static final class Flight {
        public Vec3 impulse=Vec3.ZERO, landing;
        public Vec3 velocity=Vec3.ZERO, rotation=Vec3.ZERO, angularVelocity=Vec3.ZERO;
        public int age, experience, impactAge=-1;
        public final List<ItemStack> drops=new ArrayList<>();
    }
    /** Speed, not age, determines when aerodynamic tipping overcomes upright stability. */
    public static double tipping(double downwardSpeed) {
        if (downwardSpeed<=.25) return 0;
        if (downwardSpeed>=1.65) return 1;
        return GhastMotion.expoInOut((downwardSpeed-.25)/1.4);
    }
    public static Vec3 integrateVelocity(Vec3 velocity, int age) {
        double speed=velocity.length();
        // Residual lift decays as the balloon loses its ability to sustain itself.
        return velocity.add(0,-.055+.035*Math.exp(-age/25.0),0)
            .subtract(velocity.scale(.006*speed));
    }
    public static void rotate(Flight flight) {
        // Keep the tumble axis aligned with the hit or existing horizontal travel.
        Vec3 heading=new Vec3(flight.impulse.x,0,flight.impulse.z);
        if (heading.lengthSqr()<.00001) heading=flight.velocity.multiply(1,0,1);
        if (heading.lengthSqr()<.00001) heading=new Vec3(0,0,1);
        Vec3 axis=new Vec3(0,1,0).cross(heading.normalize());
        Vec3 equilibrium=axis.scale(Math.PI*tipping(Math.max(0,-flight.velocity.y)));
        // Spring torque and angular drag preserve angular velocity through the entire fall.
        flight.angularVelocity=flight.angularVelocity.add(equilibrium.subtract(flight.rotation).scale(.006))
            .scale(.87);
        flight.rotation=flight.rotation.add(flight.angularVelocity);
    }
    public static org.joml.Quaternionf worldRotation(Vec3 rotation) {
        double angle=rotation.length();
        return angle<.000001 ? new org.joml.Quaternionf() : new org.joml.Quaternionf()
            .rotationAxis((float)angle,(float)(rotation.x/angle),(float)(rotation.y/angle),(float)(rotation.z/angle));
    }
    public static org.joml.Quaternionf modelRotation(float yaw,Vec3 rotation) {
        var frame=new org.joml.Quaternionf().rotationY((float)Math.toRadians(180-yaw)).rotateZ((float)Math.PI);
        return new org.joml.Quaternionf(frame).conjugate().mul(worldRotation(rotation)).mul(frame);
    }
    private static HitResult trace(ServerLevel level, LivingEntity entity, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity));
    }
    public static void begin(LivingEntity entity) { begin(entity,null); }
    public static void begin(LivingEntity entity, net.minecraft.world.damagesource.@org.jspecify.annotations.Nullable DamageSource source) {
        if (!GhastMotion.supported(entity) || !(entity.level() instanceof ServerLevel)) return;
        GhastCrashBody body = (GhastCrashBody)entity;
        if (body.naturality$flight() != null || entity.isRemoved()) return;
        Flight flight = new Flight();
        var sourcePosition = source == null ? null : source.getSourcePosition();
        if (sourcePosition != null) {
            Vec3 away=entity.position().add(0,GhastMotion.bodyCenter(entity),0).subtract(sourcePosition);
            if (away.lengthSqr()>.00001) flight.impulse=away.normalize().scale(.55);
        }
        // Preserve actual travel; deltaMovement already includes vanilla killing-hit knockback.
        Vec3 travel=new Vec3(entity.getX()-entity.xo,entity.getY()-entity.yo,entity.getZ()-entity.zo);
        if (entity.tickCount==0 || travel.lengthSqr()>4) travel=Vec3.ZERO;
        flight.velocity=travel.add(flight.impulse);
        flight.angularVelocity=new Vec3(0,1,0).cross(flight.impulse).scale(.11);
        body.naturality$crashImpulse(flight.impulse);
        body.naturality$flight(flight);
        entity.ejectPassengers(); entity.noPhysics = true; entity.setNoGravity(true);
        if (entity instanceof net.minecraft.world.entity.Mob mob) { mob.setPersistenceRequired(); mob.setNoAi(true); }
        entity.setDeltaMovement(flight.velocity); entity.needsSync = true;
    }
    public static void capture(Flight flight, Runnable action) {
        Flight previous = CAPTURE.get(); CAPTURE.set(flight);
        try { action.run(); } finally { if (previous == null) CAPTURE.remove(); else CAPTURE.set(previous); }
    }
    public static boolean captureSpawn(Entity entity) {
        Flight flight = CAPTURE.get();
        if (flight == null) return false;
        if (entity instanceof ItemEntity item) { flight.drops.add(item.getItem().copy()); return true; }
        return false;
    }
    public static boolean captureExperience(int amount) {
        Flight flight = CAPTURE.get();
        if (flight == null) return false;
        flight.experience += amount; return true;
    }
    public static float fallStretch(double downwardSpeed) {
        return 1+.06F*(float)Math.clamp(downwardSpeed/4,0,1);
    }
    /** Vertical support of the rotated cube; slow falls can land feet/body first. */
    public static double support(LivingEntity entity,Vec3 rotation,double downwardSpeed) {
        var q=worldRotation(rotation);
        float extent=(entity instanceof net.minecraft.world.entity.monster.Ghast ? 2.25F : 2*(entity.isBaby()?.2375F:1))*entity.getScale();
        float sideways=(float)(1/Math.sqrt(fallStretch(downwardSpeed)));
        var x=q.transform(new org.joml.Vector3f(extent*sideways,0,0));
        var y=q.transform(new org.joml.Vector3f(0,extent*fallStretch(downwardSpeed),0));
        var z=q.transform(new org.joml.Vector3f(0,0,extent*sideways));
        return Math.abs(x.y)+Math.abs(y.y)+Math.abs(z.y);
    }
    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        GhastCrashBody body=(GhastCrashBody)entity;
        Flight flight=body.naturality$flight();
        if (flight==null || entity.isRemoved()) return;
        entity.noPhysics=true; entity.setNoGravity(true);
        ++flight.age;
        if (flight.impactAge>=0) {
            entity.setDeltaMovement(Vec3.ZERO); entity.syncPosition=true;
            body.naturality$impactTicks(flight.age-flight.impactAge);
            if (flight.age-flight.impactAge>=6) finish(entity,level,flight);
            return;
        }
        double center=GhastMotion.bodyCenter(entity);
        Vec3 previousTip=entity.position().add(0,center-support(entity,flight.rotation,Math.max(0,-flight.velocity.y)),0);
        flight.velocity=integrateVelocity(flight.velocity,flight.age);
        rotate(flight); body.naturality$crashRotation(flight.rotation);
        Vec3 next=entity.position().add(flight.velocity);
        double extent=support(entity,flight.rotation,Math.max(0,-flight.velocity.y));
        Vec3 nextTip=next.add(0,center-extent,0);
        HitResult hit=trace(level,entity,previousTip,nextTip);
        if (hit.getType()!=HitResult.Type.MISS) {
            Vec3 landing=hit.getLocation().add(0,.05,0);
            flight.impactAge=flight.age; flight.landing=landing;
            next=landing.add(0,extent-center,0);
        }
        if (next.y<level.getMinY()-64 && flight.impactAge<0) {
            flight.impactAge=flight.age; flight.landing=next;
        }
        entity.setPos(next); entity.setDeltaMovement(flight.velocity); entity.needsSync=true; entity.syncPosition=true;
        if (flight.impactAge>=0) {
            level.getChunkSource().sendToTrackingPlayers(entity,
                net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket.teleport(entity.getId(),
                    net.minecraft.world.entity.PositionMoveRotation.of(entity),java.util.Set.of(),false));
            body.naturality$impactTicks(0);
        }
    }
    private static void finish(LivingEntity entity,ServerLevel level,Flight flight) {
        Vec3 landing=flight.landing;
        for (ItemStack stack : flight.drops) {
            ItemEntity item = new ItemEntity(level, landing.x, landing.y+.08, landing.z, stack.copy());
            item.setDeltaMovement(Vec3.ZERO); item.setDefaultPickUpDelay(); level.addFreshEntity(item);
        }
        ExperienceOrb.award(level, landing.add(0,.1,0), flight.experience);
        level.broadcastEntityEvent(entity,(byte)60);
        flight.drops.clear(); flight.experience = 0;
        entity.remove(Entity.RemovalReason.KILLED);
    }
    public static void save(LivingEntity entity, ValueOutput output) {
        Flight flight = ((GhastCrashBody)entity).naturality$flight();
        if (flight == null) return;
        output.store("NaturalityCrashVelocity",Vec3.CODEC,flight.velocity);
        output.store("NaturalityCrashRotation",Vec3.CODEC,flight.rotation);
        output.store("NaturalityCrashAngularVelocity",Vec3.CODEC,flight.angularVelocity);
        output.store("NaturalityCrashImpulse",Vec3.CODEC,flight.impulse);
        if (flight.landing!=null) output.store("NaturalityCrashLanding",Vec3.CODEC,flight.landing);
        output.putInt("NaturalityCrashImpactAge",flight.impactAge);
        output.store("NaturalityCrashLoot", ItemStack.CODEC.listOf(), flight.drops);
        output.putInt("NaturalityCrashAge", flight.age);
        output.putInt("NaturalityCrashXP", flight.experience);
    }
    public static void load(LivingEntity entity, ValueInput input) {
        if (!GhastMotion.supported(entity)) return;
        // Read old targeted-flight saves too, but discard their destination and steering.
        if (input.getIntOr("NaturalityCrashAge",-1)<0) return;
        Flight flight=new Flight();
        flight.velocity=input.read("NaturalityCrashVelocity",Vec3.CODEC).orElse(entity.getDeltaMovement());
        flight.rotation=input.read("NaturalityCrashRotation",Vec3.CODEC).orElse(Vec3.ZERO);
        flight.angularVelocity=input.read("NaturalityCrashAngularVelocity",Vec3.CODEC).orElse(Vec3.ZERO);
        ((GhastCrashBody)entity).naturality$crashRotation(flight.rotation);
        flight.impulse=input.read("NaturalityCrashImpulse",Vec3.CODEC).orElse(Vec3.ZERO);
        flight.landing=input.read("NaturalityCrashLanding",Vec3.CODEC).orElse(null);
        flight.impactAge=input.getIntOr("NaturalityCrashImpactAge",-1);
        flight.age = input.getIntOr("NaturalityCrashAge",0);
        ((GhastCrashBody)entity).naturality$impactTicks(flight.impactAge<0 ? -1 : flight.age-flight.impactAge);
        flight.drops.addAll(input.read("NaturalityCrashLoot",ItemStack.CODEC.listOf()).orElse(List.of()));
        flight.experience = input.getIntOr("NaturalityCrashXP",0);
        ((GhastCrashBody)entity).naturality$flight(flight);
        ((GhastCrashBody)entity).naturality$crashImpulse(flight.impulse);
    }

    @org.jspecify.annotations.NullMarked
    public interface GhastCrashBody {
        int naturality$impactTicks();
        void naturality$impactTicks(int ticks);
        GhastCrash.@org.jspecify.annotations.Nullable Flight naturality$flight();
        void naturality$flight(GhastCrash.@org.jspecify.annotations.Nullable Flight flight);
        Vec3 naturality$crashRotation();
        void naturality$crashRotation(Vec3 rotation);
        Vec3 naturality$crashImpulse();
        void naturality$crashImpulse(Vec3 impulse);
    }
}
