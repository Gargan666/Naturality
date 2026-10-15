package naturality.client.sky.overworld.stars;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/** Preserves vanilla star positions and RNG consumption while emitting repeated quad centers. */
@org.jspecify.annotations.NullMarked
public final class StarGeometry {
    private StarGeometry() {}
    public record Stars(GpuBuffer buffer, int indexCount) {}
    public static Stars create() {
        // Preserve vanilla's star positions and random sequence. The shader expands
        // each repeated center into a quad, so its pulse cannot move the star.
        RandomSource random = RandomSource.createThreadLocalInstance(10842L);
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(1500 * 4 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            for (int i = 0; i < 1500; i++) {
                float x = random.nextFloat() * 2.0F - 1.0F;
                float y = random.nextFloat() * 2.0F - 1.0F;
                float z = random.nextFloat() * 2.0F - 1.0F;
                random.nextFloat(); // Vanilla size draw.
                float lengthSquared = x * x + y * y + z * z;
                if (lengthSquared <= 0.010000001F || lengthSquared >= 1.0F) continue;
                random.nextDouble(); // Vanilla rotation draw.
                Vector3f center = new Vector3f(x, y, z).normalize(100.0F);
                for (int corner = 0; corner < 4; corner++) builder.addVertex(center);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                int count = mesh.drawState().indexCount();
                return new Stars(RenderSystem.getDevice().createBuffer(() -> "Naturality stars", 40, mesh.vertexBuffer()), count);
            }
        }
    }
}
