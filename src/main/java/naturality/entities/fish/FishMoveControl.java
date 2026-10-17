package naturality.entities.fish;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import naturality.entities.fish.mixins.FishMixins.SchoolLeaderAccess;
import org.jspecify.annotations.Nullable;
public final class FishMoveControl extends MoveControl<AbstractFish> {
    private @Nullable Vec3 schoolMovement;
    private Vec3 swimMovement=Vec3.ZERO;

    public static void registerGoals(AbstractFish fish,GoalSelector goals) {
        goals.removeAllGoals(goal -> goal instanceof RandomSwimmingGoal);
        goals.addGoal(4,new SchoolSwimmingGoal(fish));
    }

    /** Vanilla owns membership; followers share steering instead of repeatedly pathfinding to the leader. */
    private static final class SchoolSwimmingGoal extends Goal {
        private final AbstractFish fish;
        private int swimTicks,idleTicks,repathTicks;

        private SchoolSwimmingGoal(AbstractFish fish) {
            this.fish=fish;
            setFlags(EnumSet.of(Flag.MOVE));
            swimTicks=600+fish.getRandom().nextInt(600);
        }

        @Override public boolean canUse() { return fish.isInWater() && !fish.hasControllingPassenger(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() {
            fish.getNavigation().stop();
            if(fish.getMoveControl() instanceof FishMoveControl control) control.schoolMovement=null;
        }

        @Override public void tick() {
            if(!(fish.getMoveControl() instanceof FishMoveControl control)) return;
            control.schoolMovement=null;
            if(repathTicks>0) repathTicks--;
            if(fish instanceof AbstractSchoolingFish schooling) {
                AbstractSchoolingFish leader=((SchoolLeaderAccess)schooling).naturality$leader();
                if(leader!=null && leader.isAlive() && leader.isInWater() && leader.level()==fish.level()
                        && fish.distanceToSqr(leader)<=121 && leader.getMoveControl() instanceof FishMoveControl leaderControl) {
                    // Stable slots keep followers together without making them converge on one point.
                    double angle=fish.getId()*2.399963229728653;
                    Vec3 slot=new Vec3(Math.cos(angle)*1.2,((fish.getId()%3)-1)*.3,Math.sin(angle)*1.2);
                    Vec3 offset=leader.position().add(slot).subtract(fish.position());
                    Vec3 movement=leaderControl.swimMovement.add(offset.scale(.035));
                    double limit=swimSpeed(1.25,fish.getAttributeValue(Attributes.MOVEMENT_SPEED),false);
                    if(movement.length()>limit) movement=movement.normalize().scale(limit);
                    boolean clear=fish.level().noCollision(fish,fish.getBoundingBox().expandTowards(movement.scale(8)))
                            && fish.level().getFluidState(net.minecraft.core.BlockPos.containing(fish.position().add(movement.scale(8))))
                                    .is(net.minecraft.tags.FluidTags.WATER);
                    if(offset.lengthSqr()<25 && clear) {
                        fish.getNavigation().stop();
                        control.schoolMovement=movement;
                    } else if(repathTicks==0) {
                        fish.getNavigation().moveTo(leader,1.1);
                        repathTicks=20;
                    }
                    return;
                }
            }
            if(idleTicks>0) {
                idleTicks--;
                fish.getNavigation().stop();
                return;
            }
            if(--swimTicks<=0) {
                idleTicks=20+fish.getRandom().nextInt(40);
                swimTicks=600+fish.getRandom().nextInt(600);
                fish.getNavigation().stop();
                return;
            }
            if(repathTicks==0 && (fish.getNavigation().isDone() || fish.horizontalCollision)) {
                Vec3 destination=BehaviorUtils.getRandomSwimmablePos(fish,10,7);
                if(destination!=null) fish.getNavigation().moveTo(destination.x,destination.y,destination.z,1);
                repathTicks=10;
            }
        }
    }
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
        Vec3 shared=schoolMovement;
        if(shared!=null) { desired=shared;swimming=shared.lengthSqr()>.000009; }
        if(swimming || mob.isPanicking()) {idle=false;burstTicks=0;cooldown=0;}
        else desired=idleMovement();
        swimMovement=swimming?desired:Vec3.ZERO;
        Vec3 velocity=mob.getDeltaMovement().lerp(desired,.15);
        mob.setDeltaMovement(velocity); mob.setSpeed(0); mob.xxa=0; mob.yya=0; mob.zza=0;
        if((swimming || desired.y>0) && velocity.lengthSqr()>.000009) {
            if(velocity.horizontalDistance()>.001) mob.setYRot(rotlerp(mob.getYRot(),(float)Math.toDegrees(Math.atan2(-velocity.x,velocity.z)),10));
            mob.setXRot(rotlerp(mob.getXRot(),(float)-Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance())),8));
            mob.yBodyRot=mob.getYRot(); mob.yHeadRot=mob.getYRot();
        }
    }
}


