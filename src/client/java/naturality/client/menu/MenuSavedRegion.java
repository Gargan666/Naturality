package naturality.client.menu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.server.level.ServerPlayer;

/** Read-only source access: all server locks, migration, simulation and writes target a disposable copy. */
public final class MenuSavedRegion {
    public static void copy(Path original, Path destination) throws IOException {
        copy(original, destination, () -> false);
    }
    public static void copy(Path original, Path destination, java.util.function.BooleanSupplier cancelled) throws IOException {
        original = original.toAbsolutePath().normalize();
        var root = NbtIo.readCompressed(original.resolve("level.dat"), NbtAccounter.unlimitedHeap());
        var data = root.getCompoundOrEmpty("Data");
        var player = data.getCompoundOrEmpty("Player");
        var playerFile = original.resolve("playerdata").resolve(Minecraft.getInstance().getUser().getProfileId() + ".dat");
        if (Files.isRegularFile(playerFile)) player = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
        var respawn = ServerPlayer.RespawnConfig.CODEC.parse(NbtOps.INSTANCE, player.getCompoundOrEmpty("respawn"))
                .result().map(ServerPlayer.RespawnConfig::respawnData)
                .orElseGet(() -> LevelData.RespawnData.CODEC.parse(NbtOps.INSTANCE, data.getCompoundOrEmpty("spawn"))
                        .result().orElse(LevelData.RespawnData.DEFAULT));
        data.put("spawn", LevelData.RespawnData.CODEC.encodeStart(NbtOps.INSTANCE, respawn).getOrThrow());
        data.remove("Player");
        data.putInt("GameType", 3);
        data.putBoolean("allowCommands", true);
        Files.createDirectories(destination);
        NbtIo.writeCompressed(root, destination.resolve("level.dat"));
        int rx = respawn.pos().getX() >> 9, rz = respawn.pos().getZ() >> 9;
        final Path source = original;
        try (var paths = Files.walk(source)) {
            for (var iterator = paths.filter(Files::isRegularFile).iterator(); iterator.hasNext();) {
                if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                var path = iterator.next();
                if (Files.isSymbolicLink(path)) continue;
                var relative = source.relativize(path);
                String name = path.getFileName().toString();
                // Vanilla weather and mod event clocks/overrides live in saved data in 26.3.
                boolean copy = relative.startsWith("datapacks");
                for (Path part : relative) if (part.toString().equals("data")) copy = true;
                if (name.matches("r\\.-?\\d+\\.-?\\d+\\.mca") && path.getParent().getFileName().toString().equals("region")) {
                    var pieces = name.split("\\.");
                    copy = Math.abs(Integer.parseInt(pieces[1]) - rx) <= 1 && Math.abs(Integer.parseInt(pieces[2]) - rz) <= 1;
                }
                if (copy) {
                    var target = destination.resolve(relative).normalize();
                    if (!target.startsWith(destination.normalize())) throw new IOException("Preview copy outside temporary directory");
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }
    private MenuSavedRegion() { }
}
