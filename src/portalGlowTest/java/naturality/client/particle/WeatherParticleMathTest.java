package naturality.client.particle;

import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

final class WeatherParticleMathTest {
    static void run() {
        var random=new Random(82741);
        var basis=new Matrix3f();
        var rotation=new Quaternionf();
        for(int sample=0;sample<10_000;sample++) {
            var velocity=sample%7==0?Vec3.ZERO:new Vec3(random.nextDouble()*2-1,-random.nextDouble(),random.nextDouble()*2-1);
            var camera=sample%11==0?velocity:sample%13==0?Vec3.ZERO
                :new Vec3(random.nextDouble()*72-36,random.nextDouble()*32-16,random.nextDouble()*72-36);
            var original=PortalMoteGeometry.orientation(velocity,camera).rotateZ(-(float)Math.PI/2);
            WeatherCardGeometry.orientation(velocity.x,velocity.y,velocity.z,camera.x,camera.y,camera.z,basis,rotation);
            if(!Float.isFinite(rotation.w) || Math.abs(original.dot(rotation))<.99998F)
                throw new AssertionError("Weather orientation changed at sample "+sample);
        }
        for(int sample=0;sample<100;sample++) {
            double phase=random.nextDouble()*Math.PI*2;
            var sway=new WeatherSway(phase);
            for(int age=0;age<=240;age++) {
                double time=phase+age*.065;
                if(Math.abs(sway.x()-Math.sin(time))>1E-12 || Math.abs(sway.z()-Math.cos(time*.81))>1E-12
                        || Math.abs(sway.y()-Math.sin(time*.7))>1E-12)
                    throw new AssertionError("Snow sway changed at age "+age);
                sway.tick();
            }
        }
        System.out.println("Weather orientation and full-lifetime snow sway match the previous formulas");
    }
}
