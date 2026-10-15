package naturality.entities.fish;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
public final class FishMoveControl extends MoveControl<AbstractFish> {
    public static double swimSpeed(double modifier,double attribute,boolean panicking) {
        return Math.min(.54,modifier*attribute*.42)*(panicking?2:1);
    }
    private boolean idle;
    private double idleHeight;
    private int burstTicks,lookTicks,cooldown;
    private float lookYaw,lookPitch;
    private Vec3 idleMovement() {
        if(!idle) {
            idle=true;idleHeight=mob.getY();lookYaw=mob.getYRot();lookPitch=mob.getXRot();lookTicks=0;
        }
        if(cooldown>0) cooldown--;
        if(burstTicks==0 && cooldown==0 && (idleHeight-mob.getY()>.45 || mob.verticalCollision)) {
            Vec3 above=mob.position().add(0,.6,0);
            if(mob.level().getFluidState(net.minecraft.core.BlockPos.containing(above)).is(net.minecraft.tags.FluidTags.WATER)) burstTicks=12;
        }
        if(burstTicks>0) {
            burstTicks--;
            if(mob.getY()>idleHeight-.05 || burstTicks==0) {burstTicks=0;cooldown=25;}
            double yaw=Math.toRadians(mob.getYRot());
            Vec3 forward=new Vec3(-Math.sin(yaw)*.075,.12,Math.cos(yaw)*.075);
            if(!mob.level().noCollision(mob,mob.getBoundingBox().move(forward.scale(4)))) forward=new Vec3(0,.12,0);
            return forward;
        }
        if(--lookTicks<=0) {
            lookTicks=45+mob.getRandom().nextInt(55);
            float turn=12+mob.getRandom().nextFloat()*33;
            lookYaw=mob.getYRot()+(mob.getRandom().nextBoolean()?turn:-turn);
            lookPitch=-8+mob.getRandom().nextFloat()*16;
        }
        mob.setYRot(mob.getYRot()+net.minecraft.util.Mth.wrapDegrees(lookYaw-mob.getYRot())*.035F);
        mob.setXRot(mob.getXRot()+(lookPitch-mob.getXRot())*.035F);
        mob.yBodyRot=mob.getYRot();mob.yHeadRot=mob.getYRot();
        return new Vec3(0,-.004,0);
    }
    public FishMoveControl(AbstractFish fish) { super(fish); }
    public void tick() {
        if(!mob.isInWater()) { super.tick(); return; }
        Vec3 desired=Vec3.ZERO;
        boolean swimming=false;
        if(operation==Operation.MOVE_TO && !mob.getNavigation().isDone()) {
            Vec3 offset=new Vec3(wantedX-mob.getX(),wantedY-mob.getY(),wantedZ-mob.getZ());
            if(offset.lengthSqr()>.01) {
                swimming=true;desired=offset.normalize().scale(swimSpeed(speedModifier,mob.getAttributeValue(Attributes.MOVEMENT_SPEED),mob.isPanicking()));
            } else operation=Operation.WAIT;
        }
        if(swimming || mob.isPanicking()) {idle=false;burstTicks=0;cooldown=0;}
        else desired=idleMovement();
        Vec3 velocity=mob.getDeltaMovement().lerp(desired,.15);
        mob.setDeltaMovement(velocity); mob.setSpeed(0); mob.xxa=0; mob.yya=0; mob.zza=0;
        if((swimming || desired.y>0) && velocity.lengthSqr()>.000009) {
            if(velocity.horizontalDistance()>.001) mob.setYRot(rotlerp(mob.getYRot(),(float)Math.toDegrees(Math.atan2(-velocity.x,velocity.z)),10));
            mob.setXRot(rotlerp(mob.getXRot(),(float)-Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance())),8));
            mob.yBodyRot=mob.getYRot(); mob.yHeadRot=mob.getYRot();
        }
    }
}


