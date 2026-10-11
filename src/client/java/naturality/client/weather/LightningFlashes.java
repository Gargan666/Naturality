package naturality.client.weather;

import naturality.config.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/** One impact pulse, with an independently smooth, low-contrast accessibility alternative. */
public final class LightningFlashes {
    private static long start;
    private static double simulationStart;
    private static Object world;
    private static double hold=.4;
    private static double decay=1;
    private LightningFlashes() {}
    public static void initialize() {
        naturality.client.sleep.SleepVisuals.register(client->{
            if(world!=client.level || start!=0 && elapsed(reduced())>Math.max(1,.1+hold+decay)){world=null;start=0;}
        });
    }
    public static void strike() {
        var random=java.util.concurrent.ThreadLocalRandom.current();
        hold=.4+random.nextDouble(-.2,.2);
        decay=1+random.nextDouble(-.2,.2);
        world=Minecraft.getInstance().level;start=System.nanoTime();
        simulationStart=naturality.client.sleep.SleepVisuals.seconds();
    }
    private static boolean reduced() {
        return NaturalityConfig.get().epilepsyMode || Minecraft.getInstance().options.hideLightningFlash().get();
    }
    private static double elapsed(boolean reduced) {
        return reduced ? (System.nanoTime()-start)/1_000_000_000.0
            : naturality.client.sleep.SleepVisuals.seconds()-simulationStart;
    }
    public static float opacity(double seconds,boolean reduced) {
        return opacity(seconds,reduced,hold,decay);
    }
    public static float opacity(double seconds,boolean reduced,double holdSeconds,double decaySeconds) {
        if(seconds<0)return 0;
        if(reduced) {
            float in=(float)Math.clamp(seconds/.2,0,1),out=(float)Math.clamp((1.0-seconds)/.8,0,1);
            return .075F*in*in*(3-2*in)*out*out*(3-2*out);
        }
        if(seconds<.1)return .9F*smooth(seconds/.1);
        if(seconds<=.1+holdSeconds)return .9F;
        return .9F*(1-smooth((seconds-.1-holdSeconds)/decaySeconds));
    }
    private static float smooth(double value) {
        float t=(float)Math.clamp(value,0,1);
        return t*t*(3-2*t);
    }
    public static void extract(GuiGraphicsExtractor graphics) {
        var client=Minecraft.getInstance();if(start==0 || world!=client.level || client.level==null)return;
        boolean reduced=reduced();
        float alpha=opacity(elapsed(reduced),reduced);
        if(alpha<=0)return;
        int color=reduced?ARGB.colorFromFloat(alpha,.18F,.36F,1):ARGB.colorFromFloat(alpha,.86F,.93F,1);
        graphics.fill(0,0,graphics.guiWidth(),graphics.guiHeight(),color);
    }
}
