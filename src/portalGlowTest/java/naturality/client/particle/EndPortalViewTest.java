package naturality.client.particle;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Checks the projective-eye construction used by the portal vertex shader. */
public final class EndPortalViewTest {
    public static void run() {
        for (float fov : new float[]{45, 70, 110}) {
            for (float sway : new float[]{0, -0.1f, 0.1f}) {
                Matrix4f view = new Matrix4f().rotateX(0.6f).rotateY(-0.9f);
                Matrix4f bob = new Matrix4f().translate(sway, -Math.abs(sway), 0)
                    .rotateZ(sway * 0.3f).rotateX(sway * 0.5f);
                Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov),
                    16f / 9f, 0.05f, 1024f);
                Matrix4f full = new Matrix4f(projection).mul(bob).mul(view);
                Vector4f eye = new Matrix4f(full).invert().transform(new Vector4f(0, 0, 1, 0));
                eye.div(eye.w);
                Vector3f expected = new Matrix4f(bob).mul(view).invert().transformPosition(new Vector3f());
                if (expected.distance(new Vector3f(eye.x, eye.y, eye.z)) > 0.00001f)
                    throw new AssertionError("Portal viewpoint must include projection-space camera sway");
                // Two points on the same actual viewing ray must map to the
                // same pixel, regardless of depth or the chosen field of view.
                Vector3f ray = new Vector3f(0.2f, -0.5f, -1).normalize();
                Vector3f near = new Vector3f(ray).mul(2).add(expected);
                Vector3f far = new Vector3f(ray).mul(20).add(expected);
                Vector3f a = full.transformProject(near, new Vector3f());
                Vector3f b = full.transformProject(far, new Vector3f());
                if (Math.abs(a.x - b.x) > 0.00001f || Math.abs(a.y - b.y) > 0.00001f)
                    throw new AssertionError("Infinite backdrop rays must be independent of distance");
            }
        }
        System.out.println("End portal view checks passed: camera sway, FOV and infinite-distance rays.");
    }
}
