package naturality.client.fluid;

/** Coherent rising molten lobes and dark seams, distinct from water's streak field. */
public final class LavaEffects {
    public static float heat(double x,double y,double age) {
        double bend=Math.sin(y*.23-age*.09)*2.3;
        double lobes=Math.sin((x+bend)*.55)+Math.cos(y*.34-age*.16)+.5*Math.sin(x*.31-y*.21);
        return (float)Math.clamp(.48+lobes*.17,0,1);
    }
    public static int brightColor() {
        return naturality.client.fire.FirePalette.color(1F);
    }
    private static int mix(int a,int b,float amount) {
        int r=Math.round(net.minecraft.util.ARGB.red(a)*(1-amount)+net.minecraft.util.ARGB.red(b)*amount);
        int g=Math.round(net.minecraft.util.ARGB.green(a)*(1-amount)+net.minecraft.util.ARGB.green(b)*amount);
        int blue=Math.round(net.minecraft.util.ARGB.blue(a)*(1-amount)+net.minecraft.util.ARGB.blue(b)*amount);
        return 0xff000000 | r<<16 | g<<8 | blue;
    }
    public static int color(float heat,float tip) {
        int base=ProceduralFluids.splashLavaColor(Math.clamp(.08F+.68F*heat,0,.94F));
        return mix(base,brightColor(),Math.clamp(tip,0,1));
    }
}

