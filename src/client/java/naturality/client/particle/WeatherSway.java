package naturality.client.particle;

/** Three exact-frequency oscillators advanced by a fixed-angle rotation each tick. */
public final class WeatherSway {
    private static final double SX=Math.sin(.065),CX=Math.cos(.065);
    private static final double SZ=Math.sin(.065*.81),CZ=Math.cos(.065*.81);
    private static final double SY=Math.sin(.065*.7),CY=Math.cos(.065*.7);
    private double xSin,xCos,zSin,zCos,ySin,yCos;
    public WeatherSway(double phase) {
        xSin=Math.sin(phase);xCos=Math.cos(phase);
        zSin=Math.sin(phase*.81);zCos=Math.cos(phase*.81);
        ySin=Math.sin(phase*.7);yCos=Math.cos(phase*.7);
    }
    public void tick() {
        double next=xSin*CX+xCos*SX;xCos=xCos*CX-xSin*SX;xSin=next;
        next=zSin*CZ+zCos*SZ;zCos=zCos*CZ-zSin*SZ;zSin=next;
        next=ySin*CY+yCos*SY;yCos=yCos*CY-ySin*SY;ySin=next;
    }
    public double x(){return xSin;}
    public double z(){return zCos;}
    public double y(){return ySin;}
}
