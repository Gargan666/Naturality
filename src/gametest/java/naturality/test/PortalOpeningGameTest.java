package naturality.test;

import naturality.config.NaturalityConfig;
import naturality.config.NaturalityServerConfig;
import naturality.portal.PortalOpeningManager;
import naturality.client.portal.PortalOpeningClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.InsideBlockEffectApplier;

public final class PortalOpeningGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var buffers = new net.minecraft.client.sounds.SoundBufferLibrary(client.getResourceManager());
            var original = buffers.getCompleteBuffer(net.minecraft.resources.Identifier.parse(
                "minecraft:sounds/block/end_portal/endportal.ogg")).join();
            var positional = buffers.getCompleteBuffer(naturality.Naturality.id(
                "positional_sound/minecraft/sounds/block/end_portal/endportal.ogg")).join();
            check(original.format().getChannels() == 2, "Vanilla End portal audio must remain stereo");
            check(positional.format().getChannels() == 1, "Opening audio must be mono for distance attenuation");
            check(positional.size() * 2 == original.size(), "Mono conversion must preserve duration");
            buffers.clear();
        });
        boolean configured = NaturalityConfig.get().portalChanges.portalBlockChanges;
        boolean introConfigured = NaturalityServerConfig.get().portalIntroAnimation;
        NaturalityConfig.get().portalChanges.portalBlockChanges = true;
        NaturalityServerConfig.get().portalIntroAnimation = true;
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(PortalOpeningGameTest::checkSoundLifecycle);
            var server = world.getServer();
            BlockPos origin = server.computeOnServer(s -> world.getConnection().getServerPlayer().blockPosition().above(3));
            server.runOnServer(s -> frame(s.overworld(), origin, Direction.Axis.X));
            server.runCommand("tp @a " + (origin.getX() + 1.5) + " " + (origin.getY() + 1) + " " + (origin.getZ() - 4) + " 0 0");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            BlockPos seed = origin.offset(1, 1, 0);
            long start = server.computeOnServer(s -> {
                var level = s.overworld();
                level.setBlockAndUpdate(seed, Blocks.FIRE.defaultBlockState());
                check(level.getBlockState(seed).is(Blocks.FIRE), "Ignition must retain fire");
                check(PortalOpeningManager.isOpening(level, seed), "Opening must start on ignition");
                return level.getGameTime();
            });
            context.waitFor(client -> PortalOpeningClient.isOpening(seed));
            context.takeScreenshot("portal-opening-fire");
            server.waitFor(s -> s.overworld().getGameTime() - start >= 12);
            server.runOnServer(s -> {
                check(s.overworld().getBlockState(seed).is(Blocks.NETHER_PORTAL), "Fire must become portal after fade");
                check(PortalOpeningManager.isOpening(s.overworld(), seed), "Portal must remain locked during reveal");
                var player = world.getConnection().getServerPlayer();
                player.portalProcess = null;
                s.overworld().getBlockState(seed).entityInside(s.overworld(), seed, player, InsideBlockEffectApplier.NOOP, true);
                check(player.portalProcess == null, "Collision during opening must not initiate teleportation");
            });
            server.waitFor(s -> s.overworld().getGameTime() - start >= 26);
            context.takeScreenshot("portal-opening-reveal");
            server.waitFor(s -> s.overworld().getGameTime() - start >= 30);
            server.runOnServer(s -> {
                check(!PortalOpeningManager.isOpening(s.overworld(), seed), "Portal must unlock at completion");
                var player = world.getConnection().getServerPlayer();
                s.overworld().getBlockState(seed).entityInside(s.overworld(), seed, player, InsideBlockEffectApplier.NOOP, true);
                check(player.portalProcess != null, "Ready portal collision must initiate normal travel");
                player.portalProcess = null;
            });
            context.takeScreenshot("portal-opening-flash");
            context.waitTicks(2);
            context.takeScreenshot("portal-opening-glow-peak");
            context.waitTicks(42);
            context.takeScreenshot("portal-opening-ready");
            server.runOnServer(s -> {
                checkCrossingGeometry(s.overworld(), seed, null);
                var player=world.getConnection().getServerPlayer();
                var position=player.position();
                var mode=player.gameMode.getGameModeForPlayer();
                try {
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    checkCrossingGeometry(s.overworld(), seed, player);
                } finally {
                    naturality.portal.PortalCrossing.set(player,null);
                    player.portalProcess=null;
                    player.setPos(position);
                    player.setGameMode(mode);
                }
            });
            java.util.UUID pigUuid = server.computeOnServer(s -> {
                var level=s.overworld();
                var pig=net.minecraft.world.entity.EntityTypes.PIG.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                pig.setNoAi(true); pig.setNoGravity(true);
                pig.setPos(seed.getX()+0.5,seed.getY(),seed.getZ()-0.1);
                pig.xo=pig.getX(); pig.zo=pig.getZ();
                level.addFreshEntity(pig);
                naturality.portal.PortalCrossing.enter(pig,seed,Direction.Axis.X);
                pig.setPos(pig.getX(),pig.getY(),seed.getZ()+0.375);
                return pig.getUUID();
            });
            context.waitTicks(12);
            world.getConnection().waitForClientboundPackets();
            context.takeScreenshot("portal-entity-entry-halfway");
            java.util.UUID boatUuid=server.computeOnServer(s -> {
                var boat=net.minecraft.world.entity.EntityTypes.OAK_BOAT.create(s.overworld(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boat.setNoGravity(true);boat.setYRot(45);
                boat.setPos(seed.getX()+1,seed.getY(),seed.getZ()+0.375);
                s.overworld().addFreshEntity(boat);
                naturality.portal.PortalCrossing.set(boat,new naturality.portal.PortalCrossing(boat,seed,Direction.Axis.X,-1));
                naturality.portal.PortalCrossing.syncToObserver(boat,world.getConnection().getServerPlayer());
                return boat.getUUID();
            });
            server.runCommand("tp @a " + (origin.getX()+4) + " " + (origin.getY()+2) + " " + (origin.getZ()-2) + " 45 25");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
            context.takeScreenshot("portal-entity-rays-oblique");
            context.takeScreenshot("portal-large-boat-clipped-oblique");
            context.runOnClient(client -> {
                net.minecraft.world.entity.Entity subject=null;
                for(var entity:client.level.entitiesForRendering()) if(entity.getUUID().equals(pigUuid)) subject=entity;
                check(subject!=null,"Entering entity must be tracked by observers");
                var crossing=naturality.portal.PortalCrossing.get(subject);
                check(crossing!=null,"Entry side must be synchronized to observers");
                var outline=naturality.client.portal.PortalModelCapture.capture(subject,crossing,client.gameRenderer.mainCamera(),1);
                check(outline!=null && !outline.isEmpty(),"Pig must use its actual posed model intersection");
                double area=outline.stream().mapToDouble(r->(r.right()-r.left())*(r.top()-r.bottom())).sum();
                check(area<subject.getBoundingBox().getXsize()*subject.getBoundingBox().getYsize(),
                    "Pig outline must exclude empty space inside the hitbox");
                var renderState=client.getEntityRenderDispatcher().extractEntity(subject,1);
                var front=new net.minecraft.world.phys.Vec3(seed.getX()+0.5,seed.getY()+1,seed.getZ()-2);
                var back=new net.minecraft.world.phys.Vec3(seed.getX()+0.5,seed.getY()+1,seed.getZ()+2);
                check(!naturality.client.portal.PortalCrossingClient.hidden(renderState,front),"Entry-side observer must see the entity");
                check(naturality.client.portal.PortalCrossingClient.hidden(renderState,back),"Exit-side observer must not see the entity");
            });
            server.runOnServer(s -> {
                var boat=s.overworld().getEntity(boatUuid);
                check(boat!=null,"Large boat must remain in the source dimension while half entered");
                boat.discard();
            });
            server.runOnServer(s -> {
                var pig=s.overworld().getEntity(pigUuid);
                check(pig!=null,"Half-entered entity must linger instead of teleporting on a timer");
                check(Math.abs(naturality.portal.PortalCrossing.get(pig).progress(pig.getBoundingBox())-0.5)<0.001,"Glow must peak at half entry");
            });
            server.runCommand("tp @a " + (origin.getX()+1.5) + " " + (origin.getY()+1) + " " + (origin.getZ()+5) + " 180 0");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
            context.takeScreenshot("portal-entity-exit-hidden");
            server.runOnServer(s -> {
                var pig=s.overworld().getEntity(pigUuid);
                pig.setPos(pig.getX(),pig.getY(),seed.getZ()+0.625+pig.getBbWidth()/2+naturality.portal.PortalCrossing.HIDDEN_CLEARANCE+0.02);
            });
            server.waitFor(s -> s.getLevel(net.minecraft.world.level.Level.NETHER).getEntity(pigUuid)!=null);
            server.runCommand("tp @a " + (origin.getX()+1.5) + " " + (origin.getY()+1) + " " + (origin.getZ()-2) + " 0 0");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
            server.runCommand("tp @a " + (origin.getX()+1.5) + " " + (origin.getY()+1) + " " + (origin.getZ()+0.375) + " 0 0");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(8);
            context.runOnClient(client -> {
                check(client.player.portalEffectIntensity==1,"Overlay must be opaque before the camera crosses the surface");
                check(naturality.client.portal.PortalOverlay.cameraOpacity(0)==1,"Near-plane guard must cover the portal surface");
            });
            context.runOnClient(client -> {
                var crossing=naturality.portal.PortalCrossing.get(client.player);
                var outline=naturality.client.portal.PortalModelCapture.capture(client.player,crossing,client.gameRenderer.mainCamera(),1);
                check(outline!=null && outline.size()>1,"Player outline must follow body parts rather than a single box");
            });
            context.takeScreenshot("portal-overlay-half-entry");
            server.runCommand("tp @a " + (origin.getX()+1.5) + " " + (origin.getY()+1) + " " + (origin.getZ()-2) + " 0 0");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(12);
            context.runOnClient(client -> check(client.player.portalEffectIntensity==0,"Overlay must fully fade after retreat"));
            context.takeScreenshot("portal-overlay-retreated");
            server.runCommand("tp @a " + (origin.getX() + 2.5) + " " + (origin.getY() + 1) + " " + (origin.getZ() - 1) + " 15 -25");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(3);
            context.takeScreenshot("portal-glow-close-frame");

            // Oblique views exercise decal depth bias against a foreground stone pillar.
            server.runOnServer(s -> {
                for (int y = 0; y < 5; y++)
                    s.overworld().setBlockAndUpdate(origin.offset(1, y, -1), Blocks.STONE.defaultBlockState());
            });
            server.runCommand("tp @a " + (origin.getX() + 5.5) + " " + (origin.getY() + 1) + " " + (origin.getZ() - 4) + " 45 0");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(3);
            context.takeScreenshot("portal-glow-foreground-occlusion");
            server.runCommand("tp @a " + (origin.getX() + 11.5) + " " + (origin.getY() + 1) + " " + (origin.getZ() - 2) + " 78 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(3);
            context.takeScreenshot("portal-glow-grazing-occlusion");

            // A second portal on the other axis, interrupted before any portal blocks are placed.
            BlockPos second = origin.offset(6, 0, 0);
            BlockPos secondSeed = second.offset(0, 1, 1);
            NaturalityConfig.get().portalChanges.portalBlockChanges = false;
            server.runOnServer(s -> {
                frame(s.overworld(), second, Direction.Axis.Z);
                s.overworld().setBlockAndUpdate(secondSeed, Blocks.FIRE.defaultBlockState());
                check(PortalOpeningManager.isOpening(s.overworld(), secondSeed), "Server intro must remain enabled independently of visual block toggle");
                s.overworld().setBlockAndUpdate(second.offset(0, 2, 0), Blocks.AIR.defaultBlockState());
            });
            context.waitTicks(15);
            server.runOnServer(s -> {
                check(!s.overworld().getBlockState(secondSeed).is(Blocks.NETHER_PORTAL), "Broken frame must cancel placement");
                check(!PortalOpeningManager.isOpening(s.overworld(), secondSeed), "Canceled opening must release tracking");
            });

            // Server toggle restores gameplay even when every client visual option stays enabled.
            NaturalityConfig.get().portalChanges.portalBlockChanges = true;
            NaturalityServerConfig.get().portalIntroAnimation = false;
            server.runOnServer(s -> {
                frame(s.overworld(), second, Direction.Axis.Z);
                s.overworld().setBlockAndUpdate(secondSeed, Blocks.FIRE.defaultBlockState());
                check(s.overworld().getBlockState(secondSeed).is(Blocks.NETHER_PORTAL), "Disabled config must retain vanilla ignition");
                check(!PortalOpeningManager.isOpening(s.overworld(), secondSeed), "Disabled server intro must not lock travel");
                var player = world.getConnection().getServerPlayer();
                player.portalProcess = null;
                s.overworld().getBlockState(secondSeed).entityInside(s.overworld(), secondSeed, player, InsideBlockEffectApplier.NOOP, true);
                check(player.portalProcess != null, "Disabled server intro must allow immediate normal portal contact");
                player.portalProcess = null;
            });
            // Fully crossing players use the same spatial rule, with no standing-in-portal timer.
            server.runOnServer(s -> {
                var player=world.getConnection().getServerPlayer();
                naturality.portal.PortalCrossing.set(player,null);
                player.setPos(seed.getX()+0.5,seed.getY(),seed.getZ()+0.1);
                player.xo=player.getX();player.zo=player.getZ();
                naturality.portal.PortalCrossing.enter(player,seed,Direction.Axis.X);
                player.setPos(player.getX(),player.getY(),seed.getZ()+0.625+player.getBbWidth()/2+naturality.portal.PortalCrossing.HIDDEN_CLEARANCE+0.02);
            });
            server.waitFor(s -> world.getConnection().getServerPlayer().level().dimension()==net.minecraft.world.level.Level.NETHER);
            context.waitFor(client -> client.level!=null && client.level.dimension()==net.minecraft.world.level.Level.NETHER);
            context.waitTicks(20);
            server.runOnServer(s -> {
                var player=world.getConnection().getServerPlayer();
                var crossing=naturality.portal.PortalCrossing.get(player);
                check(player.level().dimension()==net.minecraft.world.level.Level.NETHER,"Arrival must not bounce back while stationary");
                check(crossing!=null && !player.isOnPortalCooldown(),"Arrival must retain an active reversible crossing");
                check(!crossing.fullyCrossed(player.getBoundingBox()),"Hidden arrival must not immediately return");
                double normal=crossing.plane-crossing.side*(player.getBbWidth()/2+0.25+naturality.portal.PortalCrossing.HIDDEN_CLEARANCE+0.02);
                player.setPos(crossing.axis==Direction.Axis.Z?normal:player.getX(),player.getY(),crossing.axis==Direction.Axis.X?normal:player.getZ());
            });
            server.waitFor(s -> world.getConnection().getServerPlayer().level().dimension()==net.minecraft.world.level.Level.OVERWORLD);
            context.waitFor(client -> client.level!=null && client.level.dimension()==net.minecraft.world.level.Level.OVERWORLD);
            context.waitTicks(20);
            String leaveCommand=server.computeOnServer(s -> {
                var player=world.getConnection().getServerPlayer();
                var crossing=naturality.portal.PortalCrossing.get(player);
                check(crossing!=null,"Return arrival must also retain its crossing");
                // Clear the foreground obstruction installed by the earlier occlusion test.
                double normal=crossing.plane+crossing.side*2;
                return "tp @a "+(crossing.axis==Direction.Axis.Z?normal:player.getX())+" "+player.getY()+" "+(crossing.axis==Direction.Axis.X?normal:player.getZ());
            });
            server.runCommand(leaveCommand);
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            context.runOnClient(client -> {
                var c=naturality.portal.PortalCrossing.get(client.player);
                check(client.player.portalEffectIntensity==0,"Overlay must fade fully after leaving the destination portal: alpha="+client.player.portalEffectIntensity+", pos="+client.player.position()+", crossing="+(c==null?"none":c.anchor+" side="+c.side+" signed="+c.signed(client.player.getX(),client.player.getZ())));
            });
        } finally {
            NaturalityConfig.get().portalChanges.portalBlockChanges = configured;
            NaturalityServerConfig.get().portalIntroAnimation = introConfigured;
        }
    }

    private static void checkCrossingGeometry(ServerLevel level,BlockPos pos,net.minecraft.world.entity.Entity subject) {
        var previous=level.getBlockState(pos);
        try {
            for(var axis:new Direction.Axis[]{Direction.Axis.X,Direction.Axis.Z}) for(int side:new int[]{-1,1}) {
                level.setBlock(pos,Blocks.NETHER_PORTAL.defaultBlockState().setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS,axis),2);
                var pig=subject!=null?subject:net.minecraft.world.entity.EntityTypes.PIG.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                double plane=(axis==Direction.Axis.X?pos.getZ():pos.getX())+0.5+side*0.125;
                double outside=plane+side*(pig.getBbWidth()/2+0.1);
                pig.setPos(axis==Direction.Axis.X?pos.getX()+0.5:outside,pos.getY(),axis==Direction.Axis.X?outside:pos.getZ()+0.5);
                naturality.portal.PortalCrossing.set(pig,null);
                pig.portalProcess=null;
                level.getBlockState(pos).entityInside(level,pos,pig,InsideBlockEffectApplier.NOOP,true);
                check(naturality.portal.PortalCrossing.get(pig)==null,"Empty voxel contact must not start physical entry");
                check(pig.portalProcess!=null,"Regression must exercise vanilla block-contact processing");
                check(!pig.portalProcess.processPortalTeleportation(level,pig,true),"Mobs and creative players must not teleport before reaching the slab");
                double normal=plane+side*pig.getBbWidth()*0.49;
                pig.setPos(axis==Direction.Axis.X?pos.getX()+0.5:normal,pos.getY(),axis==Direction.Axis.X?normal:pos.getZ()+0.5);
                pig.xo=pig.getX();pig.zo=pig.getZ();
                naturality.portal.PortalCrossing.enter(pig,pos,axis);
                var c=naturality.portal.PortalCrossing.get(pig);
                check(c!=null && c.side==side,"Entry side must work on both portal axes");
                boolean xAxis=axis==Direction.Axis.X;
                double hiddenNormal=plane-side*0.4, visibleNormal=plane+side*0.4;
                var hiddenBox=new net.minecraft.world.phys.AABB(
                    xAxis?pos.getX()+0.2:hiddenNormal-0.05,pos.getY()+0.2,xAxis?hiddenNormal-0.05:pos.getZ()+0.2,
                    xAxis?pos.getX()+0.8:hiddenNormal+0.05,pos.getY()+0.8,xAxis?hiddenNormal+0.05:pos.getZ()+0.8);
                var visibleBox=hiddenBox.move(xAxis?0:visibleNormal-hiddenNormal,0,xAxis?visibleNormal-hiddenNormal:0);
                check(c.visibleCollision(net.minecraft.world.phys.shapes.Shapes.create(hiddenBox)).isEmpty(),"Hidden opening collisions must be removed for mobs and players");
                check(!c.visibleCollision(net.minecraft.world.phys.shapes.Shapes.create(visibleBox)).isEmpty(),"Visible-side collisions must remain solid");
                var frameBox=hiddenBox.move(0,-1,0);
                check(!c.visibleCollision(net.minecraft.world.phys.shapes.Shapes.create(frameBox)).isEmpty(),"Portal frame collisions must remain solid");
                var hiddenBlock=pos.offset(xAxis?0:-side,0,xAxis?-side:0);
                var visibleBlock=pos.offset(xAxis?0:side,0,xAxis?side:0);
                var oldHidden=level.getBlockState(hiddenBlock);var oldVisible=level.getBlockState(visibleBlock);
                try {
                    level.setBlock(hiddenBlock,Blocks.STONE.defaultBlockState(),18);
                    level.setBlock(visibleBlock,Blocks.STONE.defaultBlockState(),18);
                    var hiddenCollisions=java.util.stream.StreamSupport.stream(level.getBlockCollisions(pig,new net.minecraft.world.phys.AABB(hiddenBlock)).spliterator(),false).toList();
                    check(hiddenCollisions.isEmpty(),"Actual hidden block collision scan must ignore obstacles through the opening: axis="+axis+", side="+side+", plane="+c.plane+", opening="+c.min+".."+c.max+", hidden="+hiddenBlock+", shapes="+hiddenCollisions);
                    check(level.getBlockCollisions(pig,new net.minecraft.world.phys.AABB(visibleBlock)).iterator().hasNext(),"Actual visible block collision scan must retain obstacles");
                } finally {
                    level.setBlock(hiddenBlock,oldHidden,18);level.setBlock(visibleBlock,oldVisible,18);
                }
                check(c.progress(pig.getBoundingBox())<0.02,"Leading edge must start at zero immersion");
                naturality.portal.PortalCrossing.tick(pig);
                check(!pig.portalProcess.processPortalTeleportation(level,pig,true),"First contact must not teleport entities");
                pig.setPos(axis==Direction.Axis.X?pig.getX():plane,pig.getY(),axis==Direction.Axis.X?plane:pig.getZ());
                check(Math.abs(c.progress(pig.getBoundingBox())-0.5)<0.001,"Halfway is the glow peak");
                check(!pig.portalProcess.processPortalTeleportation(level,pig,true),"Half entry must not teleport mobs or creative players");
                var destination=new net.minecraft.world.level.portal.TeleportTransition(level,
                    net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.world.phys.Vec3.ZERO,
                    0,0,false,false,java.util.Set.of(),arriving -> { });
                var arrival=naturality.portal.PortalArrival.prepare(destination,c,pig);
                var exit=new naturality.portal.PortalCrossing(pig,pos,axis,-side);
                var arrivalBox=pig.getBoundingBox().move(arrival.position().subtract(pig.position()));
                check(exit.progress(arrivalBox)==1,"Destination body must start fully behind the exit face");
                check(!exit.fullyCrossed(arrivalBox),"Hidden destination must leave room before return travel");
                normal=plane-side*(pig.getBbWidth()/2+0.001);
                pig.setPos(axis==Direction.Axis.X?pig.getX():normal,pig.getY(),axis==Direction.Axis.X?normal:pig.getZ());
                check(c.progress(pig.getBoundingBox())==1,"Full crossing uses the trailing hitbox edge");
                check(!pig.portalProcess.processPortalTeleportation(level,pig,true),"Clearing the entry face must still wait for the far face");
                normal=plane-side*(pig.getBbWidth()/2+0.25+naturality.portal.PortalCrossing.HIDDEN_CLEARANCE+0.001);
                pig.setPos(axis==Direction.Axis.X?pig.getX():normal,pig.getY(),axis==Direction.Axis.X?normal:pig.getZ());
                check(pig.portalProcess.processPortalTeleportation(level,pig,true),"Clearing the far face must permit teleport immediately");
                normal=plane+side*(pig.getBbWidth()/2+0.2);
                pig.setPos(axis==Direction.Axis.X?pig.getX():normal,pig.getY(),axis==Direction.Axis.X?normal:pig.getZ());
                naturality.portal.PortalCrossing.tick(pig);
                check(naturality.portal.PortalCrossing.get(pig)==null && pig.portalProcess==null,"Retreat must cancel crossing");
            }
        } finally {level.setBlock(pos,previous,2);}
    }

    private static void checkSoundLifecycle(net.minecraft.client.Minecraft client) {
        var settings = NaturalityConfig.get().portalChanges;
        double opening = settings.openingVolume, ambient = settings.ambientVolume, travel = settings.travelVolume;
        var player = client.player;
        var position = player.position();
        BlockPos pos = player.blockPosition().above(10);
        var oldState = client.level.getBlockState(pos);
        try {
            client.level.setBlock(pos, Blocks.NETHER_PORTAL.defaultBlockState(), 2);
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            var ambience = managedSound(client, net.minecraft.sounds.SoundEvents.PORTAL_AMBIENT, pos);
            ambience.tick();
            check(!ambience.isStopped(), "Ambient must continue while its portal exists");
            settings.ambientVolume = 0.4;
            check(Math.abs(ambience.getVolume() - 0.4F) < 0.001, "Ambient volume must update live");
            var entering = managedSound(client, net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER, pos);
            entering.tick();
            check(!entering.isStopped(), "Travel sound must continue during portal contact");
            settings.travelVolume = 0.5;
            check(Math.abs(entering.getVolume() - 0.5F) < 0.001, "Travel volume must be independent");
            var ignition = managedSound(client, naturality.NaturalitySounds.PORTAL_OPEN, pos);
            settings.openingVolume = 0.2;
            check(Math.abs(ignition.getVolume() - 0.2F) < 0.001, "Opening volume must be independent");
            player.setPos(position);
            entering.tick();
            check(!entering.isStopped() && entering.getVolume() < 0.5F && entering.getVolume() > 0,
                "Aborted travel must fade before stopping");
            for (int i = 0; i < 4; i++) entering.tick();
            check(entering.isStopped() && entering.getVolume() == 0, "Aborted travel must stop after five ticks");
            client.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            ambience.tick();
            check(ambience.isStopped(), "Destroyed portal must stop ambient immediately");
        } finally {
            client.level.setBlock(pos, oldState, 2);
            player.setPos(position);
            settings.openingVolume = opening; settings.ambientVolume = ambient; settings.travelVolume = travel;
        }
    }

    private static naturality.client.sound.PortalSoundInstance managedSound(net.minecraft.client.Minecraft client,
            net.minecraft.sounds.SoundEvent event, BlockPos pos) {
        var sound = new net.minecraft.client.resources.sounds.SimpleSoundInstance(event,
            net.minecraft.sounds.SoundSource.BLOCKS, 1, 1, net.minecraft.util.RandomSource.create(), pos);
        var managed = (naturality.client.sound.PortalSoundInstance) naturality.client.sound.PortalSoundInstance.wrap(sound);
        managed.getOrResolve(client.getSoundManager());
        return managed;
    }

    private static void frame(ServerLevel level, BlockPos origin, Direction.Axis axis) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-1, 0, -5), origin.offset(5, 6, 5)))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (int x = -1; x <= 5; x++) for (int z = -5; z <= 5; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
        for (int w = 0; w < 4; w++) for (int y = 0; y < 5; y++) {
            if (w == 0 || w == 3 || y == 0 || y == 4)
                level.setBlock(origin.offset(axis == Direction.Axis.X ? w : 0, y, axis == Direction.Axis.Z ? w : 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
