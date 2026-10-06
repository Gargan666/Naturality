package naturality.weather;

import java.util.HashMap;
import java.util.Map;

/** Saved End weather clocks. Legacy distortion fields are intentionally ignored. */
public final class EndWeatherCycle {
    private final SavedRandom random;
    private int gravityOverride = -1, starfallOverride = -1;
    private int starfallRemaining, starfallRetarget;
    private float starfall, starfallTarget;
    private int riseOverride=-1,riseRemaining,riseRetarget;
    private float rise,riseTarget;
    private int quiet = 6000, gravityRemaining, retarget;
    private float gravity, gravityTarget;
    public EndWeatherCycle(long seed) { random = new SavedRandom(seed); }
    public int override(String channel) {
        return switch(channel) { case "gravity" -> gravityOverride; case "starfall" -> starfallOverride; case "rise" -> riseOverride; default -> throw new IllegalArgumentException(channel); };
    }
    public void set(String channel, int value) {
        switch(channel) {
            case "gravity" -> gravityOverride = value;
            case "starfall" -> starfallOverride = value;
            case "rise" -> riseOverride = value;
            default -> throw new IllegalArgumentException(channel);
        }
    }
    public EndWeatherState state() { return new EndWeatherState(gravity, Math.round(starfall),rise); }
    public void tick(boolean advance) {
        if (advance) {
            if (quiet > 0) quiet--;
            if(riseRemaining>0) {
                riseRemaining--;
                if(--riseRetarget<=0) {riseTarget=20+random.nextFloat()*80;riseRetarget=400+random.nextInt(800);}
            } else {
                riseTarget=0;
                if(quiet==0 && random.nextInt(18000)==0) {riseRemaining=2400+random.nextInt(3600);riseRetarget=0;}
            }
            if (gravityRemaining > 0) {
                gravityRemaining--;
                if (--retarget <= 0) { gravityTarget = 20 + random.nextFloat()*80; retarget = 400 + random.nextInt(800); }
            } else {
                gravityTarget = 0;
                if (quiet == 0 && random.nextInt(12000) == 0) { gravityRemaining = 2400 + random.nextInt(3600); retarget = 0; }
            }
        }
        // Natural starfall is temporarily disabled; auto always returns it to calm.
        starfallRemaining=starfallRetarget=0;starfallTarget=0;
        if(advance || starfallOverride>=0 || starfall>0) starfall+=Math.clamp((starfallOverride>=0?starfallOverride:0)-starfall,-.5F,.5F);
        if(advance || riseOverride>=0)rise+=Math.clamp((riseOverride>=0?riseOverride:riseTarget)-rise,-.5F,.5F);
        float target = gravityOverride >= 0 ? gravityOverride : gravityTarget;
        if (advance || gravityOverride >= 0) gravity += Math.clamp(target - gravity, -.25F, .25F);
    }
    public Map<String,Long> snapshot() {
        var data = new HashMap<String,Long>();
        data.put("random", random.state()); data.put("gravityOverride", (long)gravityOverride);
        data.put("starfall",EnvironmentWorldData.bits(starfall));
        data.put("starfallVersion",1L);data.put("starfallOverride",(long)starfallOverride);
        data.put("starfallRemaining",(long)starfallRemaining);data.put("starfallRetarget",(long)starfallRetarget);
        data.put("starfallTarget",EnvironmentWorldData.bits(starfallTarget)); data.put("quiet", (long)quiet);
        data.put("gravityRemaining", (long)gravityRemaining); data.put("retarget", (long)retarget);
        data.put("gravity", EnvironmentWorldData.bits(gravity)); data.put("gravityTarget", EnvironmentWorldData.bits(gravityTarget));
        data.put("rise",EnvironmentWorldData.bits(rise));data.put("riseTarget",EnvironmentWorldData.bits(riseTarget));
        data.put("riseOverride",(long)riseOverride);data.put("riseRemaining",(long)riseRemaining);data.put("riseRetarget",(long)riseRetarget);
        return data;
    }
    public void restore(Map<String,Long> data) {
        if (data.isEmpty()) return;
        random.restore(data.getOrDefault("random", random.state()));
        gravityOverride = data.getOrDefault("gravityOverride", -1L).intValue();
        if(data.containsKey("starfallVersion")) {
            starfall=EnvironmentWorldData.number(data,"starfall");starfallOverride=data.getOrDefault("starfallOverride",-1L).intValue();
            starfallRemaining=data.getOrDefault("starfallRemaining",0L).intValue();starfallRetarget=data.getOrDefault("starfallRetarget",0L).intValue();
            starfallTarget=EnvironmentWorldData.number(data,"starfallTarget");
        } else {starfall=data.getOrDefault("starfall",0L).intValue();starfallOverride=starfall>0?(int)starfall:-1;} quiet = data.getOrDefault("quiet",6000L).intValue();
        gravityRemaining = data.getOrDefault("gravityRemaining",0L).intValue(); retarget = data.getOrDefault("retarget",0L).intValue();
        if(starfallOverride<0)starfall=0;starfallRemaining=starfallRetarget=0;starfallTarget=0;
        rise=EnvironmentWorldData.number(data,"rise");riseTarget=EnvironmentWorldData.number(data,"riseTarget");
        riseOverride=data.getOrDefault("riseOverride",-1L).intValue();riseRemaining=data.getOrDefault("riseRemaining",0L).intValue();riseRetarget=data.getOrDefault("riseRetarget",0L).intValue();
        gravity = EnvironmentWorldData.number(data,"gravity"); gravityTarget = EnvironmentWorldData.number(data,"gravityTarget");
    }
}