package naturality.weather;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import naturality.NaturalityTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

public final class EndGravity {
    private EndGravity() {}
    public static boolean affected(Entity entity) {
        return (entity.level().dimension().equals(Level.END) || entity instanceof LivingEntity living
                && (living.hasEffect(naturality.NaturalityEffects.DISTORTION) || ((DistortionState)living).naturality$distortion(false)>0))
            && !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(NaturalityTags.NO_END_GRAVITY)
            && !entity.isNoGravity() && !entity.isPassenger()
            && !(entity instanceof Player p && (p.isSpectator() || p.getAbilities().flying));
    }
    public static float factor(Entity entity) {
        return affected(entity) ? EndWeatherSystem.state(entity.level()).strength()*(float)Math.cos(Math.PI*inversion(entity,false)) : 1;
    }
    public static float inversion(Entity entity, boolean render) {
        return affected(entity) && entity instanceof DistortionState state ? state.naturality$distortion(render) : 0;
    }
    public static boolean inverted(Entity entity) { return inversion(entity,false) > .5F; }
    public static float eyeHeight(Entity entity, float height, boolean render) {
        float half = entity.getBbHeight()/2;
        return half + (height-half)*(float)Math.cos(Math.PI*inversion(entity,render));
    }
    /** A soft ceiling for upward falls, 16 blocks below the dimension's build limit. */
    public static double upwardLimit(Entity entity) { return entity.level().getMaxY()-16; }
    public static double softenUpwardMotion(Entity entity, double velocity) {
        if (!(entity instanceof Player) || !inverted(entity) || velocity<=0) return velocity;
        double remaining=upwardLimit(entity)-entity.getY();
        if(remaining>=48)return velocity;
        if(remaining<=0)return 0;
        double t=Math.clamp(remaining/48,0,1);
        double fade=t*t*(3-2*t);
        return Math.min(velocity*fade,remaining*.25);
    }
}
