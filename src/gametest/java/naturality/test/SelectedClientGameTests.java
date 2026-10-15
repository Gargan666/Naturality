package naturality.test;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.jspecify.annotations.NullMarked;

/** One catalog shared with Gradle. Expand groups first; instantiate only selected checks, once each. */
@NullMarked
public final class SelectedClientGameTests implements FabricClientGameTest {
    private record Check(String id, String implementation, String invocation, String method, String fixture) {
        void run(ClientGameTestContext context) throws ReflectiveOperationException {
            Class<?> type = Class.forName("naturality.test." + implementation);
            switch (invocation) {
                case "test" -> type.asSubclass(FabricClientGameTest.class).getDeclaredConstructor().newInstance().runTest(context);
                case "static" -> invoke(type);
                case "client" -> context.runOnClient(_ -> invoke(type));
                case "context" -> invoke(type, ClientGameTestContext.class, context);
                case "world" -> {
                    try (var world = TestWorlds.disposable(context).create()) {
                        world.getConnection().waitForClientboundPackets();
                        context.runOnClient(client -> invoke(type, net.minecraft.client.Minecraft.class, client));
                    }
                }
                default -> throw new IllegalArgumentException("Unknown invocation mode: " + invocation);
            }
        }
        private void invoke(Class<?> type) { invoke(type, new Class<?>[0], new Object[0]); }
        private void invoke(Class<?> type, Class<?> parameter, Object argument) {
            invoke(type, new Class<?>[]{parameter}, new Object[]{argument});
        }
        private void invoke(Class<?> type, Class<?>[] parameters, Object[] arguments) {
            try {
                var target = type.getDeclaredMethod(method, parameters);
                target.setAccessible(true);
                target.invoke(null, arguments);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Error error) throw error;
                if (e.getCause() instanceof RuntimeException error) throw error;
                throw new AssertionError("Check failed: " + id, e.getCause());
            } catch (ReflectiveOperationException e) { throw new AssertionError("Invalid test catalog entry: " + id, e); }
        }
    }

    @Override public void runTest(ClientGameTestContext context) {
        var checks = new LinkedHashMap<String, Check>();
        var groups = new LinkedHashMap<String, List<String>>();
        try (var input = TestFixtures.require(SelectedClientGameTests.class.getResourceAsStream("/naturality-tests.json"), "test catalog");
                var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var catalog = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : catalog.getAsJsonArray("checks")) {
                var item = entry.getAsJsonObject();
                String id = item.get("id").getAsString();
                if (id.isBlank() || id.equals("all")) throw new IllegalArgumentException("Reserved or empty check ID: " + id);
                var check = new Check(id, item.get("class").getAsString(), item.has("invoke") ? item.get("invoke").getAsString() : "test",
                    item.has("method") ? item.get("method").getAsString() : "", item.get("fixture").getAsString());
                if (checks.putIfAbsent(id, check) != null) throw new IllegalArgumentException("Duplicate check: " + id);
            }
            for (var entry : catalog.getAsJsonObject("groups").entrySet()) {
                if (entry.getKey().isBlank() || entry.getKey().equals("all") || checks.containsKey(entry.getKey()))
                    throw new IllegalArgumentException("Group conflicts with a check/reserved ID: " + entry.getKey());
                var members = new ArrayList<String>();
                for (var member : entry.getValue().getAsJsonArray()) {
                    String id = member.getAsString();
                    if (!checks.containsKey(id)) throw new IllegalArgumentException("Unknown group member: " + id);
                    members.add(id);
                }
                groups.put(entry.getKey(), members);
            }
        } catch (java.io.IOException e) { throw new AssertionError("Cannot read test catalog", e); }

        var selected = new LinkedHashSet<String>();
        for (String value : System.getProperty("naturality.test.suites", "").split(",", -1)) {
            String id = value.trim();
            if (id.equals("all")) selected.addAll(checks.keySet());
            else if (groups.containsKey(id)) selected.addAll(TestFixtures.require(groups.get(id), "group"));
            else if (checks.containsKey(id)) selected.add(id);
            else throw new IllegalArgumentException("Unknown client check: " + id + "; run gradlew listClientTests");
        }
        for (String id : selected) {
            var check = TestFixtures.require(checks.get(id), "selected check");
            long start = System.nanoTime();
            System.out.println("Naturality client check: " + id + " [" + check.fixture + "]");
            System.setProperty("naturality.test.activeCheck", id);
            try { check.run(context); }
            catch (ReflectiveOperationException e) { throw new AssertionError("Cannot load selected check: " + id, e); }
            finally { System.clearProperty("naturality.test.activeCheck"); }
            System.out.printf("Naturality check passed: %s (%.2f s)%n", id, (System.nanoTime() - start) / 1e9);
        }
    }
}
