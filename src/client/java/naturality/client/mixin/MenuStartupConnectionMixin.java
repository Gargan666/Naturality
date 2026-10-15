package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.menu.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MenuStartupConnectionMixin implements MenuPreviewConnection {
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
