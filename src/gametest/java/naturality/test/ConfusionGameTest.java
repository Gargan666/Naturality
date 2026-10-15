package naturality.test;
import java.util.Random;
import naturality.client.confusion.ConfusionLayout;
import naturality.client.confusion.ConfusionSlots;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;
public final class ConfusionGameTest implements FabricClientGameTest {
    private static void check(boolean v, String m) { if (!v) throw new AssertionError(m); }
    @Override public void runTest(ClientGameTestContext context) {
        check(ConfusionSlots.interval(0)==40 && ConfusionSlots.interval(1)==20 && ConfusionSlots.interval(255)==5,"Intervals");
        var layout=new ConfusionLayout(9); var random=new Random(1234);
        check(ConfusionLayout.landingAmplitude(0,6)==0,"Stationary landing has no boost");
        check(Math.abs(ConfusionLayout.landingAmplitude(.02F,6)*2-ConfusionLayout.landingAmplitude(.04F,6))<.000001,"Landing jump scales with actual velocity");
        boolean five = false;
        check(ConfusionLayout.baseDuration(0)==30 && ConfusionLayout.baseDuration(1)==21
            && ConfusionLayout.baseDuration(3)==15 && ConfusionLayout.baseDuration(255)==12,"Gentle level scaling and minimum duration");
        for(int amp=1;amp<256;amp++) check(ConfusionLayout.baseDuration(amp)<=ConfusionLayout.baseDuration(amp-1),"Easing speeds up monotonically");
        for(int round=0;round<100;round++) {
            int[] counts = ConfusionSlots.batch(random, 0);
            check(counts[0] + counts[1] + 2 <= 5, "Level I total movement cap");
            five |= counts[0] + counts[1] + 2 == 5;
            float[] before=new float[9];
            int[] cells = new int[9];
            for(int i=0;i<9;i++) cells[i]=layout.cell(i);
            for(int i=0;i<9;i++) before[i]=layout.coordinate(i,0,9,false,20);
            int count = 2 + random.nextInt(4);
            layout.swap(random,count);
            int moved = 0;
            for(int i=0;i<9;i++) {
                if(cells[i]!=layout.cell(i)) moved++;
                check(layout.identity(layout.cell(i))==i,"Permutation inverse");
                check(layout.coordinate(i,0,9,false,20)==before[i],"Continuous start");
            }
            check(moved==count,"Every chosen slot moves exactly once");
            for(int i=0;i<12;i++) layout.tick();
            check(!layout.settled(),"Slower easing still active after original duration");
            for(int i=12;i<33;i++) layout.tick();
            for(int i=0;i<9;i++) check(layout.coordinate(i,0,9,false,20)==layout.cell(i)*20,"Exact destination");
        }
        check(five,"Five-slot total budget including physical offhand pair");
        check(ConfusionLayout.spring(.82F)>0,"Elastic landing continues approach");
        check(ConfusionLayout.spring(.89F)<0,"Elastic landing recoils quickly");
        check(Math.abs(ConfusionLayout.spring(1))<.00001,"Elastic landing settles exactly");
        var varied=new ConfusionLayout(9); varied.swap(new Random(42),5); varied.tick();
        boolean different=false;
        for(int i=1;i<9;i++) different |= varied.progress(i,0)!=varied.progress(0,0);
        check(different,"Individual easing durations vary");
        // Retarget repeatedly at the fastest level without speeding up easing or snapping.
        for(int round=0;round<1000;round++) {
            float[] x=new float[9], y=new float[9], vx=new float[9], vy=new float[9];
            for(int i=0;i<9;i++) {
                vx[i]=(layout.coordinate(i,.01F,9,false,20)-layout.coordinate(i,-.01F,9,false,20))/.02F;
                vy[i]=(layout.coordinate(i,.01F,9,true,20)-layout.coordinate(i,-.01F,9,true,20))/.02F;
            }
            for(int i=0;i<9;i++) { x[i]=layout.coordinate(i,0,9,false,20); y[i]=layout.coordinate(i,0,9,true,20); }
            layout.swap(random,5,255);
            for(int i=0;i<9;i++) {
                check(Math.abs(layout.coordinate(i,0,9,false,20)-x[i])<.0001,"Continuous rapid retarget X");
                check(Math.abs(layout.coordinate(i,0,9,true,20)-y[i])<.0001,"Continuous rapid retarget Y");
                float nextX=(layout.coordinate(i,.01F,9,false,20)-x[i])/.01F;
                float nextY=(layout.coordinate(i,.01F,9,true,20)-y[i])/.01F;
                check(Math.abs(nextX-vx[i])<.03F,"Retarget preserves horizontal velocity");
                check(Math.abs(nextY-vy[i])<.03F,"Retarget preserves vertical velocity");
            }
            for(int i=0;i<ConfusionSlots.interval(255);i++) layout.tick();
            for(int i=0;i<9;i++) check(layout.progress(i,0)>=5F/13 && layout.progress(i,0)<=5F/11,"Highest level keeps gentle varied easing");
            check(!layout.settled(),"Rapid shuffle does not finish easing early");
            for(int i=0;i<9;i++) for(int sample=0;sample<=4;sample++) {
                float xx=layout.coordinate(i,sample/4F,9,false,20), yy=layout.coordinate(i,sample/4F,9,true,20);
                check(xx>=-12.001F && xx<=172.001F,"Rapid motion stays near hotbar width");
                check(yy>=-18.001F && yy<=3.001F,"Rapid motion stays near hotbar row");
            }
        }
        layout.restore(random);
        for(int i=0;i<33;i++) layout.tick();
        for(int i=0;i<9;i++) check(layout.coordinate(i,0,9,false,20)==i*20,"Interrupted animation returns home");
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer(); server.runCommand("gamemode survival @a");
            server.runCommand("item replace entity @a hotbar.0 with diamond");
            server.runCommand("item replace entity @a hotbar.4 with emerald");
            server.runCommand("item replace entity @a inventory.0 with gold_ingot");
            server.runCommand("item replace entity @a weapon.offhand with shield");
            context.waitTicks(5); server.runCommand("effect give @a naturality:confusion infinite 0 true");
            context.waitTicks(70);
            context.runOnClient(c->{
                check(ConfusionSlots.visible(),"Active synced effect");
                check(TestFixtures.player(c).getInventory().getItem(0).is(Items.DIAMOND) || TestFixtures.player(c).getInventory().getItem(0).is(Items.SHIELD) || TestFixtures.player(c).getInventory().getItem(0).isEmpty(),"Only offhand exchanges affect item storage");
                check(ConfusionSlots.visual(TestFixtures.player(c).getInventory().getSelectedSlot())==0,"Fixed selection cell");
                c.gui.setScreen(new InventoryScreen(TestFixtures.player(c)));
            });
            context.waitTicks(5); context.takeScreenshot("confusion-inventory");
            context.runOnClient(c->{
                ConfusionSlots.layout(TestFixtures.player(c).inventoryMenu,1);
                for(var slot:TestFixtures.player(c).inventoryMenu.slots) if(slot.container==TestFixtures.player(c).getInventory() && slot.getContainerSlot()<36) {
                    int i=slot.getContainerSlot(), cell=i<9?ConfusionSlots.hotbar.cell(i):ConfusionSlots.inventory.cell(i-9);
                    check(slot.x==(8+cell%9*18),"Mapped slot position including offhand anchor");
                    try {
                        var type=net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class;
                        var left=type.getDeclaredField("leftPos"); var top=type.getDeclaredField("topPos");
                        left.setAccessible(true); top.setAccessible(true);
                        var hover=type.getDeclaredMethod("getHoveredSlot",double.class,double.class); hover.setAccessible(true);
                        check(hover.invoke(c.gui.screen(),left.getInt(c.gui.screen())+slot.x+8.0,top.getInt(c.gui.screen())+slot.y+8.0)==slot,"Mouse hits mapped storage slot");
                    } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
                }
                c.gui.setScreen(null);
                int expectedSelection=ConfusionSlots.selectable(4,TestFixtures.player(c).getInventory().getSelectedSlot());
                net.minecraft.client.KeyMapping.click(c.options.keyHotbarSlots[4].getDefaultKey());
                try {
                    var keys=net.minecraft.client.Minecraft.class.getDeclaredMethod("handleKeybinds"); keys.setAccessible(true); keys.invoke(c);
                } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
                check(TestFixtures.player(c).getInventory().getSelectedSlot()==expectedSelection,"Number key selects visual cell");
                int expectedCell=(ConfusionSlots.visual(expectedSelection)+1)%9;
                if(ConfusionSlots.storage(expectedCell)==9) expectedCell=(expectedCell+1)%10;
                c.mouseHandler.onScroll(c.getWindow().handle(),0,-1);
                check(ConfusionSlots.visual(TestFixtures.player(c).getInventory().getSelectedSlot())==expectedCell,"Scroll follows selectable visual order");
            });
            context.takeScreenshot("confusion-hotbar");

            server.runCommand("effect clear @a naturality:confusion"); context.waitTicks(35);
            context.runOnClient(c->{
                check(!ConfusionSlots.visible(),"Return finished");
                check(TestFixtures.player(c).getOffhandItem().is(Items.SHIELD),"Original offhand restored by server");
                for(int i=0;i<9;i++) check(ConfusionSlots.visual(i)==i,"Identity restored");
                for(var slot:TestFixtures.player(c).inventoryMenu.slots) if(slot.container==TestFixtures.player(c).getInventory() && slot.getContainerSlot()<36)
                    check(slot.x==8+slot.getContainerSlot()%9*18,"Original coordinates restored");
            });
            context.takeScreenshot("confusion-restored");
            String[] items = {"diamond", "emerald", "gold_ingot", "iron_ingot", "ender_pearl", "apple", "bow", "netherite_sword", "elytra"};
            for(int i=0;i<items.length;i++) server.runCommand("item replace entity @a hotbar."+i+" with "+items[i]);
            server.runCommand("effect give @a naturality:confusion infinite 7 true");
            context.waitTicks(100);
            context.takeScreenshot("confusion-high-level-bounded");
            server.runCommand("effect clear @a naturality:confusion");
            context.waitTicks(35);
            server.runOnServer(s -> {
                var p=s.overworld().players().getFirst();
                var state=((naturality.confusion.ConfusionOffhandAccess)p).naturality$confusionOffhand();
                var inv=p.getInventory();
                for(int i=0;i<36;i++) inv.setItem(i,net.minecraft.world.item.ItemStack.EMPTY);
                inv.setItem(0,new net.minecraft.world.item.ItemStack(Items.DIAMOND));
                inv.setItem(40,new net.minecraft.world.item.ItemStack(Items.SHIELD));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(naturality.NaturalityEffects.CONFUSION,1000));
                state.tick(p); state.exchange(p,0);
                check(p.getOffhandItem().is(Items.DIAMOND) && inv.getItem(0).is(Items.SHIELD),"Actual server offhand exchange");
                var saved=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,s.registryAccess());
                state.save(saved);
                inv.setItem(17,inv.getItem(0).copy()); inv.setItem(0,new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT));
                state.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,s.registryAccess(),saved.buildResult()));
                p.removeEffect(naturality.NaturalityEffects.CONFUSION); state.tick(p);
                check(p.getOffhandItem().is(Items.SHIELD) && inv.getItem(17).is(Items.DIAMOND),"Moved original restored across saved state without losing replacement");
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(naturality.NaturalityEffects.CONFUSION,1000));
                state.tick(p); state.exchange(p,0);
                inv.setItem(0,net.minecraft.world.item.ItemStack.EMPTY);
                p.removeEffect(naturality.NaturalityEffects.CONFUSION); state.tick(p);
                check(p.getOffhandItem().is(Items.GOLD_INGOT),"Lost original is not recreated");
                inv.setItem(40,net.minecraft.world.item.ItemStack.EMPTY); inv.setItem(0,new net.minecraft.world.item.ItemStack(Items.APPLE));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(naturality.NaturalityEffects.CONFUSION,1000));
                state.tick(p); state.exchange(p,0);
                check(p.getOffhandItem().is(Items.APPLE),"Empty offhand can receive an actual item");
                p.removeEffect(naturality.NaturalityEffects.CONFUSION); state.tick(p);
                check(p.getOffhandItem().isEmpty() && inv.getItem(0).is(Items.APPLE),"Original empty offhand restored without deleting its replacement");
            });
        }
    }
}
