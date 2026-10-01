package naturality.test;

import naturality.snow.SnowGeometry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;

public final class SnowloggingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("fill -5 99 15 5 99 25 grass_block");
            server.runCommand("fill -5 100 15 5 100 25 snow");
            server.runCommand("setblock 0 100 20 fern");
            server.runCommand("setblock 0 101 20 snow");
            server.runCommand("tp @a 0 103 16 0 45");
            world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();context.waitTicks(10);
            context.takeScreenshot("snow-lighting-comparison");
            context.runOnClient(client -> {
                var lighter=new net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl(true,true,client.getBlockColors());
                for(var sample:new BlockPos[]{new BlockPos(0,101,20),new BlockPos(1,100,20)}) {
                    var sampleState=client.level.getBlockState(sample);
                    var sink=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
                        if(q.lightFace()==Direction.UP && q.y(0)>.01F-(sample.getY()-100)) {
                            for(int vertex=0;vertex<4;vertex++) {
                                check((q.color(vertex)&0xFFFFFF)==0xFFFFFF,"Snow top has no false ambient shadow");
                                check((q.lightmap(vertex)&0xFF0000)==0xF00000,"Displaced and adjacent snow both receive full skylight");
                            }
                        }
                    });
                    lighter.tesselateBlock(sink,0,0,0,client.level,sample,sampleState,client.getModelManager().getBlockStateModelSet().get(sampleState),0);
                }
            });
            server.runCommand("fill -10 99 -4 12 99 8 grass_block");
            String[] supports={"stone_slab[type=bottom]","oak_stairs[facing=east]","oak_fence","anvil",
                "oak_sign[rotation=2]","oak_fence_gate[open=true]","glass_pane","oak_trapdoor[half=bottom]"};
            for(int i=0;i<supports.length;i++) {
                int x=-6+(i%4)*4,z=(i/4)*4;
                server.runCommand("setblock "+x+" 100 "+z+" "+supports[i]);
                server.runCommand("setblock "+x+" 101 "+z+" snow");
            }
            // The owner is in section Y=7 while its ground sheet is in Y=6.
            server.runCommand("setblock 11 109 7 stone");
            server.runCommand("setblock 11 110 7 oak_fence");
            server.runCommand("setblock 11 111 7 oak_fence");
            server.runCommand("setblock 11 112 7 snow");
            server.runOnServer(s -> {
                var level=s.overworld();
                var supportOnly=new BlockPos(10,100,6);
                for(var support:new net.minecraft.world.level.block.state.BlockState[]{
                        Blocks.TORCH.defaultBlockState(),Blocks.WALL_TORCH.defaultBlockState(),
                        Blocks.SOUL_TORCH.defaultBlockState(),Blocks.SOUL_WALL_TORCH.defaultBlockState(),
                        Blocks.REDSTONE_TORCH.defaultBlockState(),Blocks.REDSTONE_WALL_TORCH.defaultBlockState(),
                        Blocks.CAMPFIRE.defaultBlockState(),Blocks.SOUL_CAMPFIRE.defaultBlockState()}) {
                    level.setBlock(supportOnly,support,18);
                    check(naturality.snow.SnowSupportOnly.contains(support),"Configured torch or lit campfire is support-only");
                    var patches=SnowGeometry.surfaces(level,supportOnly.above());
                    check(patches.size()==1 && Math.abs(patches.getFirst().y()+1)<1e-5,
                        "Support-only snow covers the ground without a raised cap");
                }
                var unlitCampfire=Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,false);
                check(!naturality.snow.SnowSupportOnly.contains(unlitCampfire),"Unlit campfire can receive ordinary snow coating");
                level.removeBlock(supportOnly,false);
                var water = new BlockPos(12,100,6);
                level.setBlock(water,Blocks.WATER.defaultBlockState(),3);
                check(naturality.snow.NoSnowBlocks.contains(level.getBlockState(water)),
                    "Water is in the configurable nosnow block tag");
                check(!Blocks.SNOW.defaultBlockState().canSurvive(level,water.above()),
                    "Snow cannot survive above water");
                check(!Blocks.SNOW.defaultBlockState().canSurvive(level,water),
                    "Snow cannot replace a water block");
                var waterSnow = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOW,1);
                var waterClick = new net.minecraft.world.item.context.BlockPlaceContext(level,null,
                    net.minecraft.world.InteractionHand.MAIN_HAND,waterSnow,
                    new net.minecraft.world.phys.BlockHitResult(new Vec3(12.5,101,6.5),Direction.UP,water,false));
                check(!((net.minecraft.world.item.BlockItem)waterSnow.getItem()).place(waterClick).consumesAction(),
                    "Player snow placement rejects water");
                check(level.getBlockState(water.above()).isAir(),"Rejected water snow leaves the surface clear");
                for (int index=0;index<3;index++) {
                    var waterlogged = new BlockPos(14+index*2,100,6);
                    level.setBlock(waterlogged.below(),Blocks.STONE.defaultBlockState(),3);
                    var block = switch(index) {
                        case 0 -> Blocks.OAK_SLAB;
                        case 1 -> Blocks.OAK_FENCE;
                        default -> Blocks.COBBLESTONE_WALL;
                    };
                    var state = block.defaultBlockState().setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true);
                    level.setBlock(waterlogged,state,3);
                    var patches = SnowGeometry.surfaces(level,waterlogged.above());
                    if (index==0) {
                        check(patches.isEmpty(),"Submerged waterlogged slab has no snow surface");
                        check(!Blocks.SNOW.defaultBlockState().canSurvive(level,waterlogged.above()),
                            "Snow cannot be placed on a submerged waterlogged block");
                    } else {
                        check(!patches.isEmpty(),"Waterlogged fence or wall retains its exposed top");
                        check(patches.stream().allMatch(p -> p.y() >= -1e-5),
                            "Waterlogged support has no ground snow beneath the water");
                        check(Blocks.SNOW.defaultBlockState().canSurvive(level,waterlogged.above()),
                            "Snow can cover the exposed top of a waterlogged support");
                    }
                    var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOW,1);
                    var click = new net.minecraft.world.item.context.BlockPlaceContext(level,null,
                        net.minecraft.world.InteractionHand.MAIN_HAND,stack,
                        new net.minecraft.world.phys.BlockHitResult(
                            new Vec3(waterlogged.getX()+.5,waterlogged.getY()+1,waterlogged.getZ()+.5),
                            Direction.UP,waterlogged,false));
                    check(((net.minecraft.world.item.BlockItem)stack.getItem()).place(click).consumesAction()==(index!=0),
                        "Snow item only places on a waterlogged block's exposed top");
                    check(level.getBlockState(waterlogged.above()).is(index==0?Blocks.AIR:Blocks.SNOW),
                        "Waterlogged placement stays above its support");
                }
                var mixed=new BlockPos(11,100,5);
                level.setBlock(mixed,Blocks.FIRE.defaultBlockState(),18);
                level.setBlock(mixed.above(),Blocks.SNOW.defaultBlockState(),18);
                near(SnowGeometry.shape(level,mixed.above(),1).max(Direction.Axis.Y),.125,
                    "Legacy snow above fire uses its vanilla outline");
                check(naturality.fire.FireGeometry.shape(level,mixed,level.getBlockState(mixed))!=null,
                    "Legacy fire beside snow has a bounded shape");
                check(naturality.snow.ShapeRecursionGuard.enterFire(),"Fire shape guard starts");
                try {
                    near(SnowGeometry.shape(level,mixed.above(),3).max(Direction.Axis.Y),.375,
                        "Nested snow shape falls back to vanilla layers");
                    near(SnowGeometry.collisionShape(level,mixed.above(),3).max(Direction.Axis.Y),.25,
                        "Nested snow collision falls back to vanilla layers");
                } finally { naturality.snow.ShapeRecursionGuard.exitFire(); }
                check(naturality.snow.ShapeRecursionGuard.enterSnow(),"Snow shape guard starts");
                try {
                    near(naturality.fire.FireGeometry.shape(level,mixed,level.getBlockState(mixed))
                        .max(Direction.Axis.Y),1.0/16,"Nested fire shape falls back to vanilla height");
                    var sideFire=Blocks.FIRE.defaultBlockState().setValue(FireBlock.NORTH,true);
                    check(naturality.fire.FireGeometry.shape(level,mixed,sideFire)
                        .equals(((naturality.mixin.FireShapeAccessor)Blocks.FIRE)
                            .naturality$vanillaShapes().apply(sideFire)),
                        "Nested wall fire uses its state-specific vanilla shape");
                } finally { naturality.snow.ShapeRecursionGuard.exitSnow(); }
                check(!naturality.snow.ShapeRecursionGuard.active(),"Shape guard clears after both queries");
                level.setBlock(mixed.above(),Blocks.AIR.defaultBlockState(),18);
                var snowItem=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOW,2);
                var fireClick=new net.minecraft.world.item.context.BlockPlaceContext(level,null,
                    net.minecraft.world.InteractionHand.MAIN_HAND,snowItem,
                    new net.minecraft.world.phys.BlockHitResult(new Vec3(11.5,100.5,5.5),Direction.UP,mixed,false));
                check(((net.minecraft.world.item.BlockItem)snowItem.getItem()).place(fireClick).consumesAction(),
                    "Snow item replaces a flame");
                check(level.getBlockState(mixed).is(Blocks.SNOW) && level.getBlockState(mixed.above()).isAir(),
                    "Snow takes the fire cell, not the cell above");
                check(!Blocks.FIRE.defaultBlockState().canSurvive(level,mixed.above()),
                    "Fire cannot survive on snow");
                level.removeBlock(mixed,false);
                var modelSupport=new BlockPos(11,100,7);
                for(var rod:new Block[]{Blocks.END_ROD,net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("lightning_rod"))})for(var facing:Direction.values()) {
                    level.setBlock(modelSupport,rod.defaultBlockState().setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,facing),18);
                    var boxes=naturality.snow.SnowSupportModels.boxes(level,modelSupport);
                    check(boxes.size()==2,"Rod snow uses separate model head and shaft");
                    var surfaces=SnowGeometry.surfaces(level,modelSupport.above());
                    check(!surfaces.isEmpty(),"All rod orientations can hold snow");
                    if(facing.getAxis()!=Direction.Axis.Y) {
                        near(surfaces.stream().mapToDouble(p->p.y()).max().orElseThrow(),rod==Blocks.END_ROD?-7.0/16:-6.0/16,
                            "Horizontal rod snow follows the model, omitting one-pixel end-rod bases");
                        check(surfaces.stream().anyMatch(p->Math.abs(p.y()+7F/16)<1e-5),
                            "Horizontal rod shaft has its own lower snow surface");
                    }
                }
                level.setBlock(modelSupport,Blocks.LANTERN.defaultBlockState(),18);
                near(SnowGeometry.surfaces(level,modelSupport.above()).stream().mapToDouble(p->p.y()).max().orElseThrow(),
                    -7.0/16,"Lantern snow rests on metal cap, not handle hitbox");
                level.removeBlock(modelSupport,false);
                var removal = new BlockPos(11,100,7);
                for(var support:new net.minecraft.world.level.block.state.BlockState[]{
                        Blocks.OAK_FENCE.defaultBlockState(),Blocks.OAK_FENCE_GATE.defaultBlockState(),
                        Blocks.FERN.defaultBlockState()}) {
                    level.setBlockAndUpdate(removal,support);
                    level.setBlockAndUpdate(removal.above(),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3));
                    level.destroyBlock(removal,false);
                    check(level.getBlockState(removal).is(Blocks.SNOW),"Breaking a partial support seats its snow on the ground");
                    check(level.getBlockState(removal).getValue(SnowLayerBlock.LAYERS)==3,"Support removal preserves snow layer count");
                    check(level.getBlockState(removal.above()).isAir(),"Old snow owner is cleared");
                    check(level.getBlockState(removal.below()).getValue(SnowyBlock.SNOWY),"Settled snow keeps ground snowy");
                    level.removeBlock(removal,false);
                }
                level.setBlockAndUpdate(removal,Blocks.OAK_FENCE.defaultBlockState());
                level.setBlockAndUpdate(removal.above(),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,8));
                level.setBlockAndUpdate(removal.above(2),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,2));
                level.destroyBlock(removal,false);
                check(level.getBlockState(removal).getValue(SnowLayerBlock.LAYERS)==8
                        && level.getBlockState(removal.above()).getValue(SnowLayerBlock.LAYERS)==2,
                        "Support removal preserves overflow snow layers");
                check(level.getBlockState(removal.above(2)).isAir(),"Overflow owner moves down too");
                level.removeBlock(removal.above(),false);level.removeBlock(removal,false);
                var compact = new BlockPos(11,100,6);
                level.setBlockAndUpdate(compact,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3));
                var feet = new net.minecraft.world.phys.AABB(11.2,100.25,6.2,11.8,102.05,6.8);
                check(compact.equals(naturality.snow.SnowCompaction.contact(level,feet)),"Foot contact resolves layered snow");
                check(naturality.snow.SnowCompaction.contact(level,feet.move(0,1,0))==null,"Airborne feet do not press snow");
                for(int i=0;i<7;i++)naturality.snow.SnowCompaction.press(level,compact,1);
                check(level.getBlockState(compact).getValue(SnowLayerBlock.LAYERS)==3,"Seven steps do not compact yet");
                naturality.snow.SnowCompaction.press(level,compact,1);
                check(level.getBlockState(compact).getValue(SnowLayerBlock.LAYERS)==2,"Eighth step removes exactly one layer");
                for(int i=0;i<4;i++)naturality.snow.SnowCompaction.press(level,compact,2);
                check(level.getBlockState(compact).getValue(SnowLayerBlock.LAYERS)==1,"Four landings compact one layer");
                for(int i=0;i<20;i++)naturality.snow.SnowCompaction.press(level,compact,2);
                check(level.getBlockState(compact).getValue(SnowLayerBlock.LAYERS)==1,"Foot traffic never removes the final layer");
                var slabSnow = new BlockPos(-6,101,0);
                level.setBlockAndUpdate(slabSnow,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3));
                check(slabSnow.equals(naturality.snow.SnowCompaction.contact(level,
                    new net.minecraft.world.phys.AABB(-5.8,100.75,.2,-5.2,102.55,.8))),"Foot contact follows fitted slab snow geometry");
                level.setBlockAndUpdate(slabSnow,Blocks.SNOW.defaultBlockState());
                // Exercise the real item path, including replacement and item consumption.
                for (var support : new net.minecraft.world.level.block.state.BlockState[]{
                        Blocks.OAK_FENCE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(),
                        Blocks.DANDELION.defaultBlockState(), Blocks.FERN.defaultBlockState(), Blocks.SPRUCE_SAPLING.defaultBlockState()}) {
                    var base = new BlockPos(10,99,0);
                    level.setBlock(base.above(),support,3);
                    var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOW,4);
                    var contextPlace = new net.minecraft.world.item.context.BlockPlaceContext(level,null,
                        net.minecraft.world.InteractionHand.MAIN_HAND,stack,
                        new net.minecraft.world.phys.BlockHitResult(new Vec3(10.9,100,.9),Direction.UP,base,false));
                    var result = ((net.minecraft.world.item.BlockItem)stack.getItem()).place(contextPlace);
                    check(result.consumesAction(),"Snow places through occupied partial cell");
                    check(level.getBlockState(base.above()).equals(support),"Snow preserves foliage and partial supports");
                    check(level.getBlockState(base.above(2)).is(Blocks.SNOW),"Snow resolves to saved cell above support");
                    check(stack.getCount()==3,"Snow placement consumes one item");
                    if (support.getBlock() instanceof VegetationBlock) {
                        var patches = SnowGeometry.surfaces(level,base.above(2));
                        check(patches.size()==1,"Foliage has only one ground snow sheet");
                        near(patches.getFirst().y(),-1,"No snow cap above plant selection box");
                        near(SnowGeometry.shape(level,base.above(2),1).max(Direction.Axis.Y),-.875,
                            "Plant snow selection stays at ground height");
                        level.setBlock(base.above(2),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3),3);
                        near(level.getBlockState(base.above()).getCollisionShape(level,base.above()).max(Direction.Axis.Y),.25,
                            "Layered snow collision stays below foliage");
                    }
                    level.removeBlock(base.above(2),false);
                    level.removeBlock(base.above(),false);
                }
                var caneBase = new BlockPos(10,99,0);
                level.setBlock(caneBase.east(),Blocks.WATER.defaultBlockState(),3);
                for(int i=1;i<=3;i++) level.setBlock(caneBase.above(i),Blocks.SUGAR_CANE.defaultBlockState(),3);
                var caneSnow = caneBase.above(4);
                var caneStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOW,4);
                var caneContext = new net.minecraft.world.item.context.BlockPlaceContext(level,null,
                    net.minecraft.world.InteractionHand.MAIN_HAND,caneStack,
                    new net.minecraft.world.phys.BlockHitResult(new Vec3(10.95,100,.95),Direction.UP,caneBase,false));
                check(((net.minecraft.world.item.BlockItem)caneStack.getItem()).place(caneContext).consumesAction(),
                    "Snow places through three-block sugar cane");
                var canePatches=SnowGeometry.surfaces(level,caneSnow);
                check(canePatches.size()==1,"Sugar cane has one ground sheet");
                near(canePatches.getFirst().y(),-3,"Sugar cane snow reaches its base");
                check(level.getBlockState(caneBase).getValue(SnowyBlock.SNOWY),"Grass below tall cane becomes snowy");
                level.setBlock(caneSnow,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3),3);
                near(level.getBlockState(caneBase.above()).getCollisionShape(level,caneBase.above()).max(Direction.Axis.Y),.25,
                    "Tall cane snow collision reaches ground");
                var caneHit=level.clip(new net.minecraft.world.level.ClipContext(new Vec3(10.95,100.8,.95),new Vec3(10.95,100,.95),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
                check(caneHit.getBlockPos().equals(caneSnow),"Ground snow below tall cane is selectable");
                level.removeBlock(caneSnow,false);
                check(!level.getBlockState(caneBase).getValue(SnowyBlock.SNOWY),"Removing cane snow restores grass");
                for(int i=3;i>=1;i--) level.removeBlock(caneBase.above(i),false);
                level.setBlock(caneBase,Blocks.SAND.defaultBlockState(),3);
                for(int i=1;i<=3;i++)level.setBlock(caneBase.above(i),Blocks.CACTUS.defaultBlockState(),3);
                level.setBlock(caneSnow,Blocks.SNOW.defaultBlockState(),3);
                var cactusPatches=SnowGeometry.surfaces(level,caneSnow);
                check(cactusPatches.size()==2,"Cactus has a top cap and a ground sheet");
                near(cactusPatches.stream().mapToDouble(p->p.y()).max().orElseThrow(),0,
                    "Cactus cap sits on its top face");
                check(cactusPatches.stream().anyMatch(p->Math.abs(p.y())<1e-5
                    && Math.abs(p.x()-.0625F)<1e-5 && Math.abs(p.z()-.0625F)<1e-5
                    && Math.abs(p.ux()-.875F)<1e-5 && Math.abs(p.vz()-.875F)<1e-5),
                    "Cactus cap matches its inset outline");
                near(cactusPatches.stream().mapToDouble(p->p.y()).min().orElseThrow(),-3,
                    "Cactus snow still reaches sand at its base");
                check(SnowGeometry.shape(level,caneSnow,1).toAabbs().stream()
                    .anyMatch(b->Math.abs(b.minX-.0625)<1e-5 && Math.abs(b.maxX-.9375)<1e-5
                        && Math.abs(b.minY)<1e-5),"Cactus snow cap has fitted selection geometry");
                check(level.getBlockState(caneBase.above(3)).is(Blocks.CACTUS),"Snow preserves cactus");
                var fenceColumn=new BlockPos(9,100,6);
                for(int i=0;i<3;i++)level.setBlock(fenceColumn.above(i),Blocks.OAK_FENCE.defaultBlockState(),3);
                level.setBlock(fenceColumn.above(3),Blocks.SNOW.defaultBlockState(),3);
                var columnPatches=SnowGeometry.surfaces(level,fenceColumn.above(3));
                check(columnPatches.stream().anyMatch(p->p.y()>=0),"Top fence keeps its snow cap");
                check(columnPatches.stream().noneMatch(p->p.y()<0 && p.y()>-3),
                    "Lower fence segments do not get internal snow caps");
                check(columnPatches.stream().anyMatch(p->Math.abs(p.y()+3)<1e-5),
                    "Stacked fence still gets ground snow");
                var chainColumn=new BlockPos(10,100,6);
                var chain=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                    net.minecraft.resources.Identifier.withDefaultNamespace("chain"));
                for(int i=0;i<2;i++)level.setBlock(chainColumn.above(i),chain.defaultBlockState(),3);
                level.setBlock(chainColumn.above(2),Blocks.SNOW.defaultBlockState(),3);
                check(SnowGeometry.surfaces(level,chainColumn.above(2)).stream()
                    .noneMatch(p->Math.abs(p.y()+1)<1e-5),
                    "Lower chain segments do not get internal snow caps");
                level.removeBlock(caneSnow,false);
                for(int i=3;i>=1;i--)level.removeBlock(caneBase.above(i),false);
                for(boolean room:new boolean[]{true,false}) {
                    var target=caneBase.above();
                    level.setBlock(target,Blocks.SNOW.defaultBlockState(),3);
                    if(!room)level.setBlock(target.above(),Blocks.STONE.defaultBlockState(),3);
                    var fenceStack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_FENCE,2);
                    var place=new net.minecraft.world.item.context.BlockPlaceContext(level,null,
                        net.minecraft.world.InteractionHand.MAIN_HAND,fenceStack,
                        new net.minecraft.world.phys.BlockHitResult(new Vec3(10.5,100,.5),Direction.UP,caneBase,false));
                    check(((net.minecraft.world.item.BlockItem)fenceStack.getItem()).place(place).consumesAction(),"Fence replaces snow");
                    check(level.getBlockState(target).is(Blocks.OAK_FENCE),"Fence placed into original snow cell");
                    check(level.getBlockState(target.above()).is(room?Blocks.SNOW:Blocks.STONE),"Snow moves upward only into available space");
                    level.removeBlock(target.above(),false);level.removeBlock(target,false);
                }
                var stackSupport=caneBase.above();var stackOwner=stackSupport.above();
                level.setBlock(stackSupport,Blocks.OAK_FENCE.defaultBlockState(),3);
                level.setBlock(stackOwner,Blocks.SNOW.defaultBlockState(),3);
                var moreSnow=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOW,16);
                for(int count=2;count<=10;count++) {
                    // A side hit names the displaced layer owner, but vanilla's
                    // placement context initially points into adjacent air.
                    var sideClick=new net.minecraft.world.item.context.BlockPlaceContext(level,null,
                        net.minecraft.world.InteractionHand.MAIN_HAND,moreSnow,
                        new net.minecraft.world.phys.BlockHitResult(new Vec3(10.5,100.05,0),Direction.NORTH,stackOwner,false));
                    check(((net.minecraft.world.item.BlockItem)moreSnow.getItem()).place(sideClick).consumesAction(),
                        "Repeated lower side clicks add snow instead of placing an orphan");
                    var expected=count<=8?stackOwner:stackOwner.above();
                    check(level.getBlockState(expected).getValue(SnowLayerBlock.LAYERS)==(count<=8?count:count-8),
                        "Layers accumulate on the correct saved owner, including overflow");
                    check(level.getBlockState(stackOwner.north()).isAir(),"No transient adjacent snow block");
                }
                check(moreSnow.getCount()==7,"One snow item consumed per successful layer");

                level.removeBlock(stackOwner.above(),false);level.removeBlock(stackOwner,false);level.removeBlock(stackSupport,false);
                var gatePos=caneBase.above();
                for(var facing:Direction.Plane.HORIZONTAL)for(boolean open:new boolean[]{false,true}) {
                    level.setBlock(gatePos,Blocks.OAK_FENCE_GATE.defaultBlockState()
                        .setValue(FenceGateBlock.FACING,facing).setValue(FenceGateBlock.OPEN,open),3);
                    var patches=SnowGeometry.surfaces(level,gatePos.above());
                    for(var patch:patches) {
                        check(patch.ux()>1F/16 && patch.vz()>1F/16,"No one-pixel snow slices survive");
                        if(patch.y()<-.99)continue;
                        var boxes=naturality.fire.FireGeometry.supportBoxes(level,gatePos);
                        check(boxes.stream().anyMatch(b->Math.abs(b.maxY-1-patch.y())<1e-5
                            && b.minX<=patch.x()+patch.ux()/2 && b.maxX>=patch.x()+patch.ux()/2
                            && b.minZ<=patch.z()+patch.vz()/2 && b.maxZ>=patch.z()+patch.vz()/2),
                            "Gate snow rests on a rendered rail/post top");
                    }
                }
                level.removeBlock(gatePos,false);
                var stackedGround=new BlockPos(10,99,4);
                level.setBlock(stackedGround,Blocks.GRASS_BLOCK.defaultBlockState(),3);
                level.setBlock(stackedGround.above(),Blocks.OAK_FENCE.defaultBlockState(),3);
                level.setBlock(stackedGround.above(2),Blocks.SOUL_LANTERN.defaultBlockState(),3);
                var stackedOwner=stackedGround.above(3);
                level.setBlock(stackedOwner,Blocks.SNOW.defaultBlockState(),3);
                var stackedPatches=SnowGeometry.surfaces(level,stackedOwner);
                check(stackedPatches.stream().anyMatch(p->Math.abs(p.y()+1)<1e-5),"Fence below lantern retains snow surface for its overlay");
                near(stackedPatches.stream().filter(p->Math.abs(p.y()+2)<1e-5).mapToDouble(p->p.ux()*p.vz()).sum(),1,
                    "Lantern and fence column retains a full ground sheet");
                check(level.getBlockState(stackedGround).getValue(SnowyBlock.SNOWY),"Grass below stacked supports becomes snowy");
                level.setBlock(stackedOwner,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3),3);
                var stackedHit=level.clip(new net.minecraft.world.level.ClipContext(new Vec3(10.95,100.8,4.95),new Vec3(10.95,100,4.95),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
                check(stackedHit.getBlockPos().equals(stackedOwner),"Ground snow under stacked supports targets the owner");
                check(level.getBlockState(stackedGround.above()).getCollisionShape(level,stackedGround.above())
                    .toAabbs().stream().anyMatch(b->b.contains(new Vec3(.95,.125,.95))),"Ground collision is included below stacked supports");
                var slab=new BlockPos(-6,101,0);
                check(level.getBlockState(slab).is(Blocks.SNOW),"Snow remains on bottom slab");
                var shape=SnowGeometry.shape(level,slab,1);
                near(shape.min(Direction.Axis.Y),-.5,"Snow lowers to slab top");
                near(shape.max(Direction.Axis.Y),-.375,"One layer stays one eighth block thick");
                var fence=new BlockPos(2,101,0);
                var surfaces=SnowGeometry.surfaces(level,fence);
                check(surfaces.stream().anyMatch(p->p.y() < -.99),"Gaps expose ground under fence");
                near(surfaces.stream().filter(p->p.y() < -.99).mapToDouble(p->p.ux()*p.vz()).sum(),1,
                    "Ground snow is a whole sheet beneath the fence");
                near(SnowGeometry.surfaces(level,new BlockPos(-6,101,4)).stream()
                    .filter(p->p.y() < -.99).mapToDouble(p->p.ux()*p.vz()).sum(),1,
                    "Ground snow is a whole sheet beneath the sign");
                check(surfaces.stream().anyMatch(p->p.y() >= 0),"Fence top also receives snow");
                var upper=surfaces.stream().filter(p->p.y()>=0).findFirst().orElseThrow();
                for(int layers=1;layers<=8;layers++)
                    check(SnowGeometry.sliceLayers(surfaces,upper,layers)==(layers+1)/2,"Raised narrow snow grows every second layer");
                check(SnowGeometry.collisionShape(level,fence,2).max(Direction.Axis.Y)<0,
                    "A one-layer raised cap remains non-colliding after two inventory layers");
                var stairs=new BlockPos(-2,101,0);
                var stairPatches=SnowGeometry.surfaces(level,stairs);
                double mergedTop=Double.NaN;
                for(var p:stairPatches) {
                    double top=p.y()+SnowGeometry.sliceLayers(stairPatches,p,8)/8.0;
                    if(Double.isNaN(mergedTop))mergedTop=top;else near(top,mergedTop,"Eight layers merge stair snow tops");
                }
                check(level.getBlockState(fence.below(2)).getValue(SnowyBlock.SNOWY),"Ground under fence becomes snowy grass");
                check(!level.getBlockState(slab.below(2)).getValue(SnowyBlock.SNOWY),"Ground sheltered by full slab footprint stays green");
                var from=new Vec3(-5.5,100.9,-1);
                var to=new Vec3(-5.5,100.3,1);
                var hit=level.clip(new net.minecraft.world.level.ClipContext(from,to,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
                check(hit.getBlockPos().equals(slab),"Displaced snow targets its saved snow block");
                level.setBlock(slab,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3),3);
                near(SnowGeometry.shape(level,slab,3).max(Direction.Axis.Y),-.125,"Stacked layers stay fitted");
                check(level.getBlockState(slab.below()).getCollisionShape(level,slab.below()).max(Direction.Axis.Y)>.5,
                    "Support cell includes displaced snow collision");
                level.removeBlock(fence,false);
                check(!level.getBlockState(fence.below(2)).getValue(SnowyBlock.SNOWY),"Removing snow restores grass");
                level.setBlock(fence,Blocks.SNOW.defaultBlockState(),3);
                level.removeBlock(slab.below(),false);
                check(level.getBlockState(slab).isAir(),"Removing support removes unsupported snow");
                level.setBlock(slab.below(),Blocks.STONE_SLAB.defaultBlockState(),3);
                level.setBlock(slab,Blocks.SNOW.defaultBlockState(),3);
            });
            server.runCommand("setblock 10 99 0 grass_block");
            server.runCommand("setblock 10 100 0 oak_fence");
            server.runCommand("setblock 10 101 0 snow");
            server.runCommand("tp @a 10.5 100 -2");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();context.waitTicks(10);
            server.runOnServer(s -> {
                var level=s.overworld();var stackOwner=new BlockPos(10,101,0);
                level.removeBlock(stackOwner.above(),false);
                level.setBlock(stackOwner,Blocks.SNOW.defaultBlockState(),3);
                var player=s.getPlayerList().getPlayers().getFirst();
                // The test driver can leave a command teleport awaiting its movement ack.
                try {
                    var type=net.minecraft.server.network.ServerGamePacketListenerImpl.class;
                    var waiting=type.getDeclaredField("awaitingPositionFromClient");waiting.setAccessible(true);
                    if(waiting.get(player.connection)!=null) {
                        var id=type.getDeclaredField("awaitingTeleport");id.setAccessible(true);
                        player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(
                            id.getInt(player.connection),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot()));
                    }
                } catch(ReflectiveOperationException ex) { throw new AssertionError(ex); }
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);

                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOW,16));
                for(int layers=2;layers<=4;layers++) {
                    var lowerHit=new net.minecraft.world.phys.BlockHitResult(new Vec3(10.95,100+(layers-1)/8.0,.95),
                        Direction.UP,stackOwner,false);
                    player.connection.handleUseItemOn(new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
                        net.minecraft.world.InteractionHand.MAIN_HAND,lowerHit,layers));
                    check(level.getBlockState(stackOwner).getValue(SnowLayerBlock.LAYERS)==layers,
                        "Real server packet accepts the displaced ground snow surface");
                }
                player.connection.handleUseItemOn(new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(new Vec3(10.95,98,.95),Direction.UP,stackOwner,false),5));
                check(level.getBlockState(stackOwner).getValue(SnowLayerBlock.LAYERS)==4,
                    "Server still rejects hits outside the fitted snow shape");
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                level.removeBlock(stackOwner,false);level.removeBlock(stackOwner.below(),false);
            });
            server.runCommand("tp @a 0 104 -7 0 22");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(5);
            context.takeScreenshot("snowlogging-surfaces");
            server.runCommand("tp @a -8 102 -3 -55 12");
            world.getConnection().waitForClientboundPackets();context.waitTicks(5);
            context.takeScreenshot("snowlogging-ground-and-tops");
            server.runCommand("setblock -4 100 -2 oak_log");
            server.runCommand("setblock -4 101 -2 snow");
            server.runCommand("setblock -2 100 -2 oak_leaves[persistent=true]");
            server.runCommand("setblock -2 101 -2 snow");
            server.runCommand("setblock 0 100 -2 stone_bricks");
            server.runCommand("setblock 0 101 -2 snow");
            server.runCommand("setblock 2 100 -2 oak_stairs[facing=east]");
            server.runCommand("setblock 2 101 -2 snow[layers=8]");
            server.runCommand("tp @a -1 101 -6 0 5");
            world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();context.waitTicks(10);
            context.takeScreenshot("snow-overlay-leaves-and-accumulation");
            server.runCommand("setblock 4 99 -2 grass_block");
            server.runCommand("setblock 5 99 -2 water");
            server.runCommand("fill 4 100 -2 4 102 -2 sugar_cane");
            server.runCommand("setblock 4 103 -2 snow");
            server.runCommand("setblock 7 98 -2 stone");
            server.runCommand("setblock 7 99 -2 sand");
            server.runCommand("fill 7 100 -2 7 102 -2 cactus");
            server.runCommand("setblock 7 103 -2 snow");
            server.runCommand("setblock 8 100 -2 tall_grass[half=lower]");
            server.runCommand("setblock 8 101 -2 tall_grass[half=upper]");
            server.runCommand("setblock 8 102 -2 snow");
            server.runCommand("tp @a 5 102 -7 0 0");
            world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();context.waitTicks(5);
            context.takeScreenshot("snow-overlay-top-stalk-only");
            server.runCommand("tp @a 11 113 5 0 30");
            world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();context.waitTicks(5);
            context.takeScreenshot("snow-cross-section-visibility");
            context.runOnClient(client -> {
                var highSnow=new BlockPos(11,112,7);
                check(client.level.getBlockState(highSnow).is(Blocks.SNOW),"Cross-section snow fixture exists");
                near(SnowGeometry.shape(client.level,highSnow,1).min(Direction.Axis.Y),-2,
                    "Cross-section snow extends two blocks below its owner");
                int recordedDrop=naturality.client.snow.SnowSectionVisibility.below(
                    net.minecraft.core.SectionPos.of(highSnow).asLong());
                check(recordedDrop>=2,"Compiled owner section records lower snow (drop="+recordedDrop+")");
                var overlaySprite=client.getAtlasManager().get(new net.minecraft.client.resources.model.sprite.SpriteId(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("naturality","block/snow_overlay")));
                int[] fenceOverlays={0,0,0};
                for(int segment=0;segment<3;segment++) {
                    var pos=new BlockPos(9,100+segment,6);
                    var state=client.level.getBlockState(pos);
                    final int index=segment;
                    client.getModelManager().getBlockStateModelSet().get(state).emitQuads(
                        net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q->{
                            if(q.u(0)>=overlaySprite.getU0() && q.u(0)<overlaySprite.getU1()
                                && q.v(0)>=overlaySprite.getV0() && q.v(0)<overlaySprite.getV1())fenceOverlays[index]++;
                        }),client.level,pos,state,net.minecraft.util.RandomSource.create(0),d->false);
                }
                check(fenceOverlays[0]==0 && fenceOverlays[1]==0 && fenceOverlays[2]>0,
                    "Only the top fence segment receives snow overlay quads");
                int[] grassOverlays={0,0};
                for(int half=0;half<2;half++) {
                    var pos=new BlockPos(8,100+half,-2);
                    var state=client.level.getBlockState(pos);
                    check(state.is(Blocks.TALL_GRASS),"Two-block tall grass remains in the snow fixture");
                    final int index=half;
                    client.getModelManager().getBlockStateModelSet().get(state).emitQuads(
                        net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q->{
                            if(q.u(0)>=overlaySprite.getU0() && q.u(0)<overlaySprite.getU1()
                                && q.v(0)>=overlaySprite.getV0() && q.v(0)<overlaySprite.getV1())grassOverlays[index]++;
                        }),client.level,pos,state,net.minecraft.util.RandomSource.create(0),d->false);
                }
                check(grassOverlays[0]==0 && grassOverlays[1]>0,
                    "Only the upper half of tall foliage gets snow drips");
                var cactusPos=new BlockPos(7,102,-2);
                var cactus=client.level.getBlockState(cactusPos);
                int[] topFaces={0};
                var sink=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
                    if(q.lightFace()==Direction.UP)topFaces[0]++;
                    for(int i=0;i<4;i++) {
                        check(q.x(i)>=-.002F && q.x(i)<=1.002F && q.y(i)>=0 && q.y(i)<=1
                            && q.z(i)>=-.002F && q.z(i)<=1.002F,"Snow leaves cactus geometry within its model bounds");
                    }
                });
                var renderer=new net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl(
                    true,true,client.getBlockColors());
                renderer.tesselateBlock(sink,0,0,0,client.level,cactusPos,cactus,
                    client.getModelManager().getBlockStateModelSet().get(cactus),0);
                check(topFaces[0]>0,"Displaced snow must not cull the exposed cactus top");
                check(naturality.client.weather.WindRendering.tag(cactus)==255,"Snowy cactus remains rigid in wind");
            });
            server.runCommand("tp @a 10 101 0 0 0");
            world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();context.waitTicks(5);
            context.takeScreenshot("snow-lantern-fence-ground");
            server.runCommand("setblock 10 100 -3 fern");
            server.runCommand("setblock 10 101 -3 snow");
            server.runCommand("tp @a 10 100 -5 0 25");
            world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();context.waitTicks(5);
            context.takeScreenshot("snow-foliage-texel-alignment");
            context.runOnClient(client -> {
                var fern=Blocks.FERN.defaultBlockState();
                var model=client.getModelManager().getBlockStateModelSet().get(fern);
                var sprite=client.getAtlasManager().get(new net.minecraft.client.resources.model.sprite.SpriteId(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("naturality","block/snow_overlay")));
                int[] count={0};var starts=new java.util.HashSet<Integer>();
                var sink=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
                    if(q.u(0)<sprite.getU0() || q.u(0)>=sprite.getU1() || q.v(0)<sprite.getV0() || q.v(0)>=sprite.getV1())return;
                    count[0]++;
                    for(int i=1;i<4;i++) {
                        near(q.u(i),q.u(0),"Each foliage texel has one overlay U sample");
                        near(q.v(i),q.v(0),"Each foliage texel has one overlay V sample");
                    }
                    near(Math.abs(q.y(0)-q.y(1)),1.0/16,"Overlay raster matches fern pixel height");
                    near(q.y(0)*16,Math.round(q.y(0)*16),"Overlay vertices align to fern texel rows");
                    float v=(q.v(0)-sprite.getV0())/(sprite.getV1()-sprite.getV0());
                    if(v<1F/16)starts.add(Math.round(q.y(0)*16));
                });
                model.emitQuads(sink,client.level,new BlockPos(10,100,-3),fern,
                    net.minecraft.util.RandomSource.create(0),d->false);
                check(count[0]>0,"Fern produces snow overlay texels");
                check(starts.size()>1,"Overlay columns start at independent foliage heights");
            });
            server.runCommand("setblock -6 100 6 end_rod[facing=east]");
            server.runCommand("setblock -6 101 6 snow");
            server.runCommand("setblock -4 100 6 lightning_rod[facing=east]");
            server.runCommand("setblock -4 101 6 snow");
            server.runCommand("setblock -2 100 6 lightning_rod[facing=down]");
            server.runCommand("setblock -2 101 6 snow");
            server.runCommand("setblock 0 100 6 stone_stairs[facing=east]");
            server.runCommand("setblock 0 101 6 snow");
            server.runCommand("tp @a -3 102 2 0 20");
            world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();context.waitTicks(5);
            context.takeScreenshot("snow-model-rods-and-stair-clipping");
            context.runOnClient(client -> {
                var ordinary=new BlockPos(11,100,6);
                check(SnowGeometry.usesVanillaGeometry(client.level,ordinary),"Ground snow uses vanilla geometry");
                check(!SnowGeometry.usesVanillaGeometry(client.level,new BlockPos(-6,101,0)),"Slab snow still uses fitted geometry");
                int[] ordinaryFaces={0};
                var ordinaryState=client.level.getBlockState(ordinary);
                var ordinaryModel=client.getModelManager().getBlockStateModelSet().get(ordinaryState);
                ordinaryModel.emitQuads(net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q->ordinaryFaces[0]++),
                    client.level,ordinary,ordinaryState,net.minecraft.util.RandomSource.create(0),d->true);
                check(ordinaryFaces[0]<=1,"Ordinary snow honors vanilla face culling instead of emitting six custom faces");
                int[] buriedOverlay={0};
                var buried=new BlockPos(0,100,-2);var buriedState=client.level.getBlockState(buried);
                var overlaySprite=client.getAtlasManager().get(new net.minecraft.client.resources.model.sprite.SpriteId(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("naturality","block/snow_overlay")));
                client.getModelManager().getBlockStateModelSet().get(buriedState).emitQuads(
                    net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q->{
                        if(q.u(0)>=overlaySprite.getU0() && q.u(0)<overlaySprite.getU1()
                            && q.v(0)>=overlaySprite.getV0() && q.v(0)<overlaySprite.getV1())buriedOverlay[0]++;
                    }),client.level,buried,buriedState,net.minecraft.util.RandomSource.create(0),d->true);
                check(buriedOverlay[0]==0,"Culled support faces generate no hidden snow overlay quads");
                var state=Blocks.SNOW.defaultBlockState();
                var wrapped=client.getModelManager().getBlockStateModelSet().get(state);
                for(boolean smooth:new boolean[]{false,true}) {
                    int[][] lighting=new int[2][8];
                    for(int pass=0;pass<2;pass++) {
                        final int index=pass,shift=pass==0?0:-3;
                        var model=new net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel(wrapped) {
                            @Override public void emitQuads(net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter e,
                                    net.minecraft.client.renderer.block.BlockAndTintGetter level,BlockPos pos,
                                    net.minecraft.world.level.block.state.BlockState s,net.minecraft.util.RandomSource random,
                                    java.util.function.Predicate<Direction> cull) {
                                e.pos(0,0,.125F+shift,0).pos(1,0,.125F+shift,1)
                                    .pos(2,1,.125F+shift,1).pos(3,1,.125F+shift,0);
                                for(int i=0;i<4;i++)e.color(i,-1);
                                e.tag(0x534E0000|((shift+64)&255)).shadeDirectionOverride(Direction.UP).emit();
                            }
                        };
                        var sink=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
                            for(int i=0;i<4;i++){lighting[index][i]=q.color(i);lighting[index][i+4]=q.lightmap(i);}
                        });
                        var renderer=new net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl(
                            smooth,false,client.getBlockColors());
                        renderer.tesselateBlock(sink,0,0,0,client.level,new BlockPos(10,100-shift,4),state,model,0);
                    }
                    check(java.util.Arrays.equals(lighting[0],lighting[1]),
                        "Displaced snow matches normal snow AO and lightmap at the same physical cell (smooth="+smooth+")");
                }
            });
        }
    }
    private static void near(double actual,double expected,String message) { check(Math.abs(actual-expected)<1e-5,message+": "+actual); }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}



























