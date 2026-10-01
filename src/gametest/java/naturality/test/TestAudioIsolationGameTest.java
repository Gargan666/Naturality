package naturality.test;

import com.mojang.blaze3d.audio.Listener;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.openal.AL10;

/** Exercise the real listener hook with the manual-launch and automated-test flags. */
public final class TestAudioIsolationGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        UnderwaterAudioGameTest.run(context);
        context.runOnClient(client -> {
            String original = System.getProperty("naturality.test.muteAudio");
            var listener = new Listener();
            try {
                System.clearProperty("naturality.test.muteAudio");
                // A freshly opened OpenAL device starts with listener gain 1.
                AL10.alListenerf(AL10.AL_GAIN, 1.0F);
                listener.reset();
                if (AL10.alGetListenerf(AL10.AL_GAIN) <= 0.0F)
                    throw new AssertionError("Manual launches must retain listener audio");
                System.setProperty("naturality.test.muteAudio", "true");
                AL10.alListenerf(AL10.AL_GAIN, 1.0F);
                listener.reset();
                if (AL10.alGetListenerf(AL10.AL_GAIN) != 0.0F)
                    throw new AssertionError("Automated test launches must be muted");
            } finally {
                if (original == null) System.clearProperty("naturality.test.muteAudio");
                else System.setProperty("naturality.test.muteAudio", original);
                listener.reset();
            }
        });
    }
}
