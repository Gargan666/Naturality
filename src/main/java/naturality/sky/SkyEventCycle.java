package naturality.sky;

import naturality.weather.SavedRandom;

/** Random start roll and weather-like active clock, with dimension-specific eligibility. */
public final class SkyEventCycle {
    public static final double START_CHANCE_PER_TICK = 1 - Math.pow(.9, 1.0 / 1200);
    public static final double END_FLASH_CHANCE_PER_TICK = 1 - Math.pow(.8, 1.0 / 1200);
    public static final double METEOR_BOOST_CHANCE = .05;
    private final SavedRandom random;
    private final double startChance;
    private final int minDuration, maxDuration;
    private final boolean meteor;
    private int remaining, retarget;
    private int boostClock, boostRemaining;
    private boolean active;
    private float strength, target;
    public SkyEventCycle(long seed) { this(seed, START_CHANCE_PER_TICK, 12000, 24000, true); }
    public static SkyEventCycle endFlashes(long seed) {
        return new SkyEventCycle(seed, END_FLASH_CHANCE_PER_TICK, 1200, 2400, false);
    }
    private SkyEventCycle(long seed, double startChance, int minDuration, int maxDuration, boolean meteor) {
        random = new SavedRandom(seed);
        this.startChance = startChance;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.meteor = meteor;
    }
    public java.util.Map<String,Long> snapshot() {
        return java.util.Map.of("random",random.state(),"remaining",(long)remaining,"retarget",(long)retarget,"active",active?1L:0L,"strength",naturality.weather.EnvironmentWorldData.bits(strength),"target",naturality.weather.EnvironmentWorldData.bits(target),"boostClock",(long)boostClock,"boostRemaining",(long)boostRemaining);
    }
    public void restore(java.util.Map<String,Long> data) {
        if(data.isEmpty())return;
        random.restore(data.getOrDefault("random",random.state()));
        remaining=data.getOrDefault("remaining",0L).intValue();
        retarget=data.getOrDefault("retarget",0L).intValue();
        active=data.getOrDefault("active",0L)!=0;
        strength=naturality.weather.EnvironmentWorldData.number(data,"strength");
        target=naturality.weather.EnvironmentWorldData.number(data,"target");
        boostClock=Math.clamp(data.getOrDefault("boostClock",0L).intValue(),0,1199);
        boostRemaining=Math.clamp(data.getOrDefault("boostRemaining",0L).intValue(),0,1200);
        if (meteor && active && boostRemaining == 0) target = Math.clamp(target, 5, 10);
    }
    public float strength() { return strength; }
    public boolean active() { return active; }
    public int remaining() { return remaining; }
    public void tick(boolean advance) {
        tick(advance, true);
    }
    /** A quiet event only rolls to start when its dimension permits starts. */
    public void tick(boolean advance, boolean canStart) {
        tick(advance, canStart, 1);
    }
    public void tick(boolean advance, boolean canStart, int startMultiplier) {
        if (!advance) return;
        if (active && --remaining <= 0) {
            active = false;
            remaining = 0;
            retarget = 0;
            boostClock = boostRemaining = 0;
        } else if (!active && canStart && random.nextDouble() < naturality.weather.PreviewEnvironment.chance(startChance, startMultiplier)) {
            active = true;
            remaining = minDuration + random.nextInt(maxDuration - minDuration + 1);
            retarget = 0;
        }
        if (meteor && active) {
            if (boostRemaining > 0 && --boostRemaining == 0) retarget = 0;
            if (canStart && ++boostClock >= 1200) {
                boostClock = 0;
                if (random.nextDouble() < METEOR_BOOST_CHANCE) { boostRemaining = 1200; retarget = 0; }
            }
        }
        if (active && --retarget <= 0) {
            float choice = random.nextFloat();
            target = !meteor ? 1 + choice * 19 : boostRemaining > 0 ? 10 + choice * choice * 10 : 5 + choice * 5;
            retarget = 100 + random.nextInt(501);
        }
        if (!active) target = 0;
        // Exponential easing, snapped only when indistinguishably close.
        strength += (target - strength) * .005F;
        if (Math.abs(target - strength) < .001F) strength = target;
    }
}
