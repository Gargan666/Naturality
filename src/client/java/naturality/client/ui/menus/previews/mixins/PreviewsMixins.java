package naturality.client.ui.menus.previews.mixins;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import naturality.client.ui.menus.previews.MenuPreview;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.ui.menus.previews.MenuPreview.MenuWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.Panorama;
import naturality.client.ui.menus.previews.MenuPreview.MenuPresetSky;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.client.gui.Hud;
import naturality.api.worlds.PreviewEnvironment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import java.util.List;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import naturality.client.ui.menus.previews.MenuPreview.MenuPreviewConnection;
import naturality.client.ui.menus.previews.MenuPreview.MenuPreviewLoader;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import naturality.client.ui.menus.previews.MenuSurface;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class PreviewsMixins {
    private PreviewsMixins() {}

    @Mixin(CreateWorldScreen.class)
    @org.jspecify.annotations.NullMarked
    public interface MenuCreateWorldAccess {
        @Accessor("uiState") WorldCreationUiState naturality$uiState();
    }

    @Mixin(CreateWorldScreen.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuCreateWorldMixin {
        @SuppressWarnings("null") @Shadow private WorldCreationUiState uiState;
        @Unique private boolean naturality$listening;
        @Inject(method = "init", at = @At("TAIL"))
        private void naturality$seedPreview(CallbackInfo ci) {
            var screen = (CreateWorldScreen)(Object)this;
            if (!naturality$listening) {
                naturality$listening = true;
                uiState.addListener(state -> MenuPreview.requestSeed(screen, state));
            }
            MenuPreview.requestSeed(screen, uiState);
        }
    }

    @Mixin(Panorama.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPanoramaMixin {
        @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
        private void naturality$world(GuiGraphicsExtractor graphics, int width, int height, CallbackInfo ci) {
            var client = Minecraft.getInstance();
            if (!(client.gui.screen() instanceof TitleScreen
                    || client.gui.screen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
                    || client.gui.screen() instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)) return;
            if (!MenuPreview.active()) MenuWorld.camera().frame(client.options.panoramaSpeed().get());
            ci.cancel();
        }
    }

    @Mixin(GameRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPresetSkyMixin {
        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
        private void naturality$nativeLoadingSky(CallbackInfo ci) { MenuPresetSky.render(); }
    }

    @Mixin(Camera.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewCameraMixin {
        @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
        private void naturality$noImmersion(CallbackInfoReturnable<net.minecraft.world.level.material.FogType> cir) {
            if (MenuPreview.active()) cir.setReturnValue(net.minecraft.world.level.material.FogType.NONE);
        }
        @Shadow protected abstract void setPosition(Vec3 position);
        @Shadow protected abstract void setRotation(float yaw, float pitch);
        @Shadow public abstract Matrix4f getViewRotationMatrix(Matrix4f matrix);
        @Invoker("prepareCullFrustum") protected abstract void naturality$frustum(Matrix4fc view, Matrix4f projection, Vec3 position);
        @Inject(method = "update", at = @At("RETURN"))
        private void naturality$orbit(DeltaTracker delta, CallbackInfo ci) {
            if (!MenuPreview.active()) return;
            var client = Minecraft.getInstance();
            var orbit = MenuWorld.camera();
            orbit.frame(client.options.panoramaSpeed().get());
            double yaw = Math.toRadians(orbit.yaw()), pitch = Math.toRadians(orbit.pitch());
            var center = MenuPreview.spawn();
            var position = new Vec3(center.getX() - Math.sin(yaw) * Math.cos(pitch) * 180,
                    center.getY() + 30 + Math.sin(pitch) * 180,
                    center.getZ() + Math.cos(yaw) * Math.cos(pitch) * 180);
            setPosition(position); setRotation(180 + orbit.yaw(), orbit.pitch());
            // Include the camera's section cube conservatively; the native perspective
            // frustum-offset loop cannot safely chase an orthographic near plane.
            naturality$frustum(getViewRotationMatrix(new Matrix4f()), MenuPreview.cullingProjection(), position);
        }
        @Inject(method = "extractRenderState", at = @At("RETURN"))
        private void naturality$projection(CameraRenderState state, DeltaTracker delta, CallbackInfo ci) {
            if (!MenuPreview.active()) return;
            state.projectionMatrix.set(MenuPreview.projection());
            state.isFirstPerson = false; state.smartCull = false;
        }
        @Inject(method = "getViewRotationProjectionMatrix", at = @At("HEAD"), cancellable = true)
        private void naturality$viewProjection(Matrix4f matrix, CallbackInfoReturnable<Matrix4f> ci) {
            if (MenuPreview.active()) ci.setReturnValue(MenuPreview.projection().mul(getViewRotationMatrix(new Matrix4f()), matrix));
        }
    }

    @Mixin(LevelExtractor.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewEntityMixin {
        @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
        private void naturality$surfaceEntities(Entity entity, Frustum frustum, double x, double y, double z,
                                               float delta, long fade, CallbackInfoReturnable<Boolean> cir) {
            if (!MenuPreview.active()) return;
            var pos = entity.blockPosition();
            var center = MenuPreview.spawn();
            double dx = entity.getX() - center.getX(), dz = entity.getZ() - center.getZ();
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            if (naturality.client.ui.menus.previews.MenuPreview.MenuWorld.camera().pitch() <= 0
                    || dx * dx + dz * dz >= Math.pow(MenuPreview.radius() * .85, 2)
                    || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    || entity.getBoundingBox().maxY < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 2)
                cir.setReturnValue(false);
        }
        @Inject(method = "extractVisibleBlockEntities", at = @At("HEAD"), cancellable = true)
        private void naturality$hideUndersideBlockEntities(net.minecraft.client.Camera camera, float partial,
                net.minecraft.client.renderer.state.level.LevelRenderState state, CallbackInfo ci) {
            if (MenuPreview.active() && naturality.client.ui.menus.previews.MenuPreview.MenuWorld.camera().pitch() <= 0) ci.cancel();
        }
        @Inject(method = "extractVisibleBlockEntities", at = @At("TAIL"))
        private void naturality$hideBuriedBlockEntities(net.minecraft.client.Camera camera, float partial,
                net.minecraft.client.renderer.state.level.LevelRenderState state, CallbackInfo ci) {
            if (!MenuPreview.active()) return;
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var center = MenuPreview.spawn();
            double radius = MenuPreview.radius() * .85;
            state.blockEntityRenderStates.removeIf(rendered -> {
                var pos = rendered.blockPos;
                double dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
                var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
                return dx * dx + dz * dz >= radius * radius || chunk == null
                        || pos.getY() < naturality.client.ui.menus.previews.MenuSurface.MenuFog.surfaceFloor(chunk, pos.getX(), pos.getZ());
            });
        }
    }

    @Mixin(FogRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewFogMixin {
        @Inject(method = "setupFog", at = @At("RETURN"))
        private void naturality$regionDistance(Camera camera, int distance, DeltaTracker delta, float darken,
                                              ClientLevel level, CallbackInfoReturnable<FogData> ci) {
            if (!MenuPreview.active()) return;
            // Environmental/weather fog stays native; distance fog is centered on the
            // miniature instead of fogging its center from the external camera.
            var fog = ci.getReturnValue();
            fog.renderDistanceStart = 1.0e6F;
            fog.renderDistanceEnd = 1.0e6F + 1;
        }
    }

    @Mixin(WorldGenRegion.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewGenerationMobMixin {
        @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
        private void naturality$noPopulation(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof net.minecraft.world.entity.Mob && ((WorldGenRegion)(Object)this).getServer() instanceof net.minecraft.client.server.IntegratedServer server && MenuPreview.owns(server))
                cir.setReturnValue(false);
        }
    }

    @Mixin(Hud.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewHudMixin {
        @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
        private void naturality$hidePreviewPlayer(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
            if (!MenuPreview.active()) return;
            Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.isHudHidden = true;
            ci.cancel();
        }
    }

    @Mixin(MinecraftServer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewInitialChunksMixin {
        @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
        private void naturality$cancelInitialChunks(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
            var server = (MinecraftServer)(Object)this;
            if (PreviewEnvironment.preview(server) && !server.isRunning()) ci.cancel();
        }
    }

    @Mixin(ServerLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewMobMixin {
        @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
        private void naturality$noMobs(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof net.minecraft.world.entity.Mob && ((ServerLevel)(Object)this).getServer() instanceof net.minecraft.client.server.IntegratedServer server && MenuPreview.owns(server))
                cir.setReturnValue(false);
        }
    }

    @Mixin(Options.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewOptionsMixin {
        @Inject(method = "getEffectiveRenderDistance", at = @At("HEAD"), cancellable = true)
        private void naturality$orbitView(CallbackInfoReturnable<Integer> ci) {
            if (MenuPreview.active()) ci.setReturnValue(16);
        }
    }

    @Mixin(LocalPlayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewPlayerMixin {
        @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
        private void naturality$inertPreview(CallbackInfo ci) { if (MenuPreview.active()) ci.cancel(); }
    }

    @Mixin(Gui.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewScreenMixin {
        @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
        private Screen naturality$keepMenu(Screen screen) { return MenuPreview.loadingScreen(screen); }
        @Inject(method = "isPausing", at = @At("HEAD"), cancellable = true)
        private void naturality$streamBehindLoading(CallbackInfoReturnable<Boolean> cir) {
            if (MenuPreview.preparingOwnWorld()) cir.setReturnValue(false);
        }
    }

    @Mixin(IntegratedServer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewServerMixin {
        @org.spongepowered.asm.mixin.injection.Inject(method = "onServerCrash", at = @At("HEAD"), cancellable = true)
        private void naturality$previewFailure(net.minecraft.CrashReport report,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
            if (MenuPreview.privateServer((IntegratedServer)(Object)this)) {
                naturality.Naturality.LOGGER.error("Disposable menu preview failed: {}", report.getTitle(), report.getException());
                ci.cancel();
            }
        }
        @WrapOperation(method = "halt", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
        private void naturality$noLanCleanup(IntegratedServer server, Runnable task, Operation<Void> original) {
            // Preview servers are never published and have only the local player. Native cleanup continues asynchronously; preview save hooks discard persistence.
            if (!MenuPreview.privateServer(server)) original.call(server, task);
        }
        @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
        private Object naturality$boundedWorld(OptionInstance<?> option, Operation<Object> original) {
            if (MenuPreview.owns((IntegratedServer)(Object)this)) {
                var options = Minecraft.getInstance().options;
                if (option == options.renderDistance()) return 8;
                if (option == options.simulationDistance()) return 2;
            }
            return original.call(option);
        }
    }

    @Mixin(SoundManager.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewSoundMixin {
        private static boolean worldSound(SoundInstance sound) {
            return MenuPreview.active() && sound.getSource() != SoundSource.MASTER
                    && sound.getSource() != SoundSource.MUSIC && sound.getSource() != SoundSource.UI;
        }
        @Inject(method = "play", at = @At("HEAD"), cancellable = true)
        private void naturality$quietWorld(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
            if (worldSound(sound)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
        @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
        private void naturality$quietDelayed(SoundInstance sound, int delay, CallbackInfo ci) { if (worldSound(sound)) ci.cancel(); }
        @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
        private void naturality$quietLoop(net.minecraft.client.resources.sounds.TickableSoundInstance sound, CallbackInfo ci) {
            if (worldSound(sound)) ci.cancel();
        }
    }

    /** An external orbit cannot seed the native visibility walk from an unloaded camera chunk. */
    @Mixin(SectionOcclusionGraph.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuPreviewVisibilityMixin {
        @Shadow private @org.jspecify.annotations.Nullable ViewArea viewArea;
        @Inject(method = "addSectionsInFrustum", at = @At("HEAD"), cancellable = true)
        private void naturality$surfaceRegion(Frustum frustum, List<SectionRenderDispatcher.RenderSection> visible,
                                             List<SectionRenderDispatcher.RenderSection> nearby, CallbackInfo ci) {
            var area = viewArea;
            if (!MenuPreview.active() || area == null) return;
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var spawn = MenuPreview.spawn();
            int cx = spawn.getX() >> 4, cz = spawn.getZ() >> 4;
            for (int z = cz - 7; z <= cz + 7; z++) for (int x = cx - 7; x <= cx + 7; x++) {
                if (!MenuPreview.containsChunk(x, z) || !level.getChunkSource().hasChunk(x, z)) continue;
                var chunk = level.getChunk(x, z);
                for (int i = 0; i < chunk.getSections().length; i++) {
                    if (chunk.getSection(i).hasOnlyAir()) continue;
                    int y = chunk.getSectionYFromSectionIndex(i);
                    var section = area.getRenderSectionAt(new BlockPos(x << 4, y << 4, z << 4));
                    // The fog skirt extends far outside the original section bounds.
                    // Submit this bounded surface footprint and let GPU clipping handle
                    // it, so an offscreen wall top cannot cull the visible wall below.
                    if (section != null) visible.add(section);
                }
            }
            ci.cancel();
        }
    }

    @Mixin(SkyRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuSkyProjectionMixin {
        @Unique private @org.jspecify.annotations.Nullable ProjectionMatrixBuffer naturality$skyProjection;
        @WrapMethod(method = "render")
        private void naturality$sky(GpuBufferSlice fog, SkyRenderState state, Operation<Void> original) {
            if (!MenuPreview.active()) { original.call(fog, state); return; }
            var buffer = naturality$skyProjection;
            if (buffer == null) { buffer = new ProjectionMatrixBuffer("Menu native sky"); naturality$skyProjection = buffer; }
            var projection = MenuPreview.skyProjection();
            RenderSystem.backupProjectionMatrix();
            try {
                RenderSystem.setProjectionMatrix(buffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
                original.call(fog, state);
            } finally { RenderSystem.restoreProjectionMatrix(); }
        }
        @Inject(method = "close", at = @At("TAIL"))
        private void naturality$close(CallbackInfo ci) {
            if (naturality$skyProjection != null) naturality$skyProjection.close();
        }
    }

    @Mixin(Minecraft.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuStartupConnectionMixin implements MenuPreviewConnection {
        @Shadow private @org.jspecify.annotations.Nullable IntegratedServer singleplayerServer;
        @Shadow private @org.jspecify.annotations.Nullable Connection pendingConnection;
        @Shadow private boolean isLocalServer;
        @SuppressWarnings("null") @Shadow @Final private Services services;
        @Unique private @org.jspecify.annotations.Nullable LevelLoadTracker naturality$loadTracker;
        @Unique private boolean naturality$discarding;

        @Override public void naturality$startPreview(MenuPreviewLoader.Prepared prepared) {
            var client = (Minecraft)(Object)this;
            prepared.stem().dataPackResources().updateComponentsAndStaticRegistryTags();
            var tracker = new LevelLoadTracker(0);
            naturality$loadTracker = tracker;
            singleplayerServer = MinecraftServer.spin(thread -> {
                var server = new IntegratedServer(thread, client, prepared.access(), prepared.packs(), prepared.stem(),
                        java.util.Optional.empty(), services, tracker);
                // Registration precedes thread start, including all initialization saves.
                MenuPreview.claimServer(server);
                return server;
            });
            isLocalServer = true;
        }
        @Override public void naturality$connectPreview() {
            var client = (Minecraft)(Object)this;
            var server = singleplayerServer;
            var tracker = naturality$loadTracker;
            if (server == null || tracker == null || !MenuPreview.privateServer(server)) return;
            var address = server.getConnection().startMemoryChannel();
            var connection = Connection.connectToLocalServer(address);
            connection.initiateServerboundPlayConnection(address.toString(), 0,
                    new ClientHandshakePacketListenerImpl(connection, client, null, null, false,
                            java.time.Duration.ZERO, _ -> {}, tracker, null));
            connection.send(new ServerboundHelloPacket(client.getUser().getName(), client.getUser().getProfileId()));
            pendingConnection = connection;
        }
        @Inject(method = "doWorldLoad", at = @At("HEAD"))
        private void naturality$realWorldWins(CallbackInfo ci) { MenuPreview.cancelPreparation(); }

        @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
        private void naturality$cancelPending(Screen screen, boolean packs, boolean sound, CallbackInfo ci) {
            naturality$discarding = MenuPreview.privateServer(singleplayerServer);
            MenuPreview.cancelPreparation();
            var connection = pendingConnection;
            if (connection != null && MenuPreview.privateServer(singleplayerServer)) {
                connection.disconnect(net.minecraft.network.chat.Component.literal("Preview closed"));
                pendingConnection = null;
            }
        }
        @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
        private void naturality$releasePreview(Screen screen, boolean packs, boolean sound, CallbackInfo ci) {
            MenuPreview.disconnected();
            naturality$loadTracker = null;
        }
        @WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/server/IntegratedServer;isShutdown()Z"))
        private boolean naturality$doNotWaitForPreview(IntegratedServer server, Operation<Boolean> original) {
            return MenuPreview.privateServer(server) || original.call(server);
        }
        @WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
        private void naturality$noSavingScreen(Gui gui, Screen screen, Operation<Void> original) {
            if (!naturality$discarding) original.call(gui, screen);
        }
    }

    /** Native post-processing deltas must not repopulate the discarded underground columns. */
    @Mixin(LevelChunk.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuSurfaceChunkMixin {
        @Unique private int @org.jspecify.annotations.Nullable [] naturality$surfaceBottom;
        @Inject(method = "replaceWithPacketData", at = @At("TAIL"))
        private void naturality$rememberShell(int x, int z, ClientboundLevelChunkPacketData data, CallbackInfo ci) {
            if (!MenuPreview.active()) return;
            var chunk = (LevelChunk)(Object)this;
            naturality.client.ui.menus.previews.MenuSurface.MenuFog.invalidate(chunk);
            var bottomByColumn = new int[256];
            naturality$surfaceBottom = bottomByColumn;
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                int bottom = chunk.getMaxY() + 1;
                for (int i = 0; i < chunk.getSections().length && bottom > chunk.getMaxY(); i++) {
                    var section = chunk.getSection(i);
                    if (section.hasOnlyAir()) continue;
                    for (int dy = 0; dy < 16; dy++) if (!section.getBlockState(dx, dy, dz).isAir()) {
                        bottom = (chunk.getSectionYFromSectionIndex(i) << 4) + dy;
                        break;
                    }
                }
                bottomByColumn[dz * 16 + dx] = bottom;
            }
        }
        @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
        private void naturality$rejectBuriedDelta(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
            var bottomByColumn = naturality$surfaceBottom;
            if (bottomByColumn != null && MenuPreview.active()
                    && pos.getY() < bottomByColumn[(pos.getZ() & 15) * 16 + (pos.getX() & 15)])
                cir.setReturnValue(((LevelChunk)(Object)this).getBlockState(pos));
        }
        @Inject(method = "setBlockState", at = @At("RETURN"))
        private void naturality$refreshSurface(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
            var chunk = (LevelChunk)(Object)this;
            if (MenuPreview.active() && chunk.getLevel().isClientSide()) naturality.client.ui.menus.previews.MenuSurface.MenuFog.invalidate(chunk);
        }
    }

    @Mixin(ClientboundLevelChunkPacketData.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuSurfacePacketMixin {
        @ModifyVariable(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("HEAD"), argsOnly = true)
        private static LevelChunk naturality$surfacePayload(LevelChunk chunk) {
            if (chunk.getLevel().getServer() instanceof net.minecraft.client.server.IntegratedServer server && MenuPreview.owns(server)) {
                return MenuSurface.snapshot(chunk);
            }
            return chunk;
        }
    }

    @Mixin(SelectWorldScreen.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MenuWorldSelectionMixin {
        @Inject(method = "updateButtonStatus", at = @At("TAIL"))
        private void naturality$selectedSpawn(LevelSummary summary, CallbackInfo ci) {
            if (summary != null && summary.primaryActionActive())
                MenuPreview.requestSaved((SelectWorldScreen)(Object)this, summary.getLevelId());
        }
    }

    @Mixin(TitleScreen.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class TitleWorldMixin extends Screen {
        @Unique private @org.jspecify.annotations.Nullable MouseButtonEvent naturality$press;
        @Unique private boolean naturality$moved, naturality$replaying, naturality$doubleClick;
        protected TitleWorldMixin(Component title) { super(title); }

        @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
        private void naturality$hold(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> ci) {
            if (event.button() != 1 || naturality$replaying) return;
            naturality$press = event;
            naturality$moved = false;
            naturality$doubleClick = doubleClick;
            MenuWorld.camera().hold();
            ci.setReturnValue(true);
        }

        @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            var press = naturality$press;
            if (event.button() != 1 || press == null) return super.mouseDragged(event, dx, dy);
            if (!naturality$moved && Math.hypot(event.x() - press.x(), event.y() - press.y()) >= 4) {
                naturality$moved = true;
                dx = event.x() - press.x();
                dy = event.y() - press.y();
            }
            if (naturality$moved) MenuWorld.camera().drag(dx, dy, width, height);
            return true;
        }

        @Override public boolean mouseReleased(MouseButtonEvent event) {
            var press = naturality$press;
            if (event.button() != 1 || press == null) return super.mouseReleased(event);
            boolean click = !naturality$moved
                    && Math.hypot(event.x() - press.x(), event.y() - press.y()) < 4;
            naturality$press = null;
            MenuWorld.camera().release();
            if (click) {
                naturality$replaying = true;
                try { this.mouseClicked(event, naturality$doubleClick); }
                finally { naturality$replaying = false; }
                super.mouseReleased(event);
            }
            return true;
        }

        @Inject(method = "removed", at = @At("HEAD"))
        private void naturality$removed(CallbackInfo ci) {
            naturality$press = null;
            MenuWorld.camera().suspend();
        }
    }
}
