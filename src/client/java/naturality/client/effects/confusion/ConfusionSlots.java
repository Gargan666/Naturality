package naturality.client.effects.confusion;

import java.util.WeakHashMap;
import java.util.Random;
import naturality.bootstrap.ContentRegistration.NaturalityEffects;
import naturality.client.effects.confusion.mixins.ConfusionMixins.ConfusionSlotAccess;
import naturality.client.effects.confusion.mixins.ConfusionMixins.ConfusionCreativeSlotAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ConfusionSlots {
    public static ConfusionLayout hotbar = new ConfusionLayout(9);
    public static ConfusionLayout inventory = new ConfusionLayout(27);
    private static final Random RANDOM = new Random();
    private static final WeakHashMap<Slot, int[]> ORIGINAL = new WeakHashMap<>();
    private static Object owner;
    public static int storageSlot(int cell) { return cell==9?40:storage(cell); }
    public static int selectable(int cell, int current) { return cell==9?current:storage(cell); }
    public static ConfusionLayout.View hud(boolean right) { return null; }
    private static boolean active, returning;
    private static int elapsed;
    private ConfusionSlots() {}
    public static int interval(int amplifier) { return Math.max(5, 40 / (Math.max(0, amplifier) + 1)); }
    public static int maxMovingSlots(int amplifier) { return 5 + 2 * Math.min(15, Math.max(0, amplifier)); }
    public static int[] batch(Random random, int amplifier) {
        // Reserve two movers for the independently synced physical offhand exchange.
        int total = 2 + random.nextInt(maxMovingSlots(amplifier) - 3);
        int hotbarCount;
        do { hotbarCount = random.nextInt(Math.min(9, total) + 1); }
        while (hotbarCount == 1 || total - hotbarCount == 1 || total - hotbarCount > 27);
        return new int[]{hotbarCount, total - hotbarCount};
    }
    public static boolean visible() { return active || returning; }
    public static int visual(int identity) { return hotbar.cell(identity); }
    public static int storage(int cell) { return hotbar.identity(cell); }
    private static void restoreCoordinates() {
        ORIGINAL.forEach((slot, xy) -> { var access = (ConfusionSlotAccess)slot; access.naturality$x(xy[0]); access.naturality$y(xy[1]); });
        ORIGINAL.clear();
    }
    public static void tick(Minecraft client) {
        var player = client.player;
        if (player != owner || client.level == null) {
            restoreCoordinates(); owner = player; active = returning = false; elapsed = 0;
            hotbar = new ConfusionLayout(9); inventory = new ConfusionLayout(27);
        }
        if (player == null || client.isPaused()) return;
        hotbar.tick(); inventory.tick();
        var effect = player.getEffect(NaturalityEffects.CONFUSION);
        if (effect != null && player.isAlive()) {
            if (!active) { active = true; returning = false; elapsed = 0; }
            int interval = interval(effect.getAmplifier());
            if (++elapsed >= interval) {
                int selectedCell = visual(player.getInventory().getSelectedSlot());
                int[] counts = batch(RANDOM, effect.getAmplifier());
                hotbar.swap(RANDOM, counts[0], effect.getAmplifier()); inventory.swap(RANDOM, counts[1], effect.getAmplifier()); elapsed = 0;
                player.getInventory().setSelectedSlot(selectable(selectedCell, player.getInventory().getSelectedSlot()));
            }
        } else if (active) {
            int selectedCell = visual(player.getInventory().getSelectedSlot());
            hotbar.restore(RANDOM); inventory.restore(RANDOM); active = false; returning = true; elapsed = 0;
            player.getInventory().setSelectedSlot(selectedCell < 9 ? selectedCell : player.getInventory().getSelectedSlot());
        } else if (returning && hotbar.settled() && inventory.settled()) { returning = false; restoreCoordinates(); }
    }
    public static void layout(AbstractContainerMenu menu, float partial) {
        if (!visible()) return;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null) return;
        Slot[] slots = new Slot[36];
        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory()) continue;
            int index = slot instanceof ConfusionCreativeSlotAccess wrapper
                ? wrapper.naturality$target().getContainerSlot() : slot.getContainerSlot();
            if(index<0 || index>=36) continue;
            slots[index]=slot; ORIGINAL.computeIfAbsent(slot,s->new int[]{s.x,s.y});
        }
        for(int i=0;i<slots.length;i++) {
            Slot slot=slots[i]; if(slot==null) continue;
            boolean hands=i<9;
            int identity=hands?i:i-9;
            Slot first=slots[hands?0:9]; if(first==null) continue;
            int[] origin=ORIGINAL.get(first);
            var access=(ConfusionSlotAccess)slot;
            var layout=hands?hotbar:inventory;
            access.naturality$x(Math.round(origin[0]+layout.coordinate(identity,partial,9,false,18)));
            access.naturality$y(Math.round(origin[1]+layout.coordinate(identity,partial,9,true,18)));
        }
    }

    /** Maps storage identities to visual cells without ever moving an ItemStack. */
    public static final class ConfusionLayout {
        private final int[] cells;
        private final int[] previous;
        private final float[] phases;
        private final java.util.List<java.lang.ref.WeakReference<View>> views = new java.util.ArrayList<>();
        private final View defaultView;
        public final class View {
            private final float[] anchorsX, anchorsY, startX, startY, velocityX, velocityY;
            private final boolean row;
            private final float minX, maxX, minY, maxY;
            private View(float[] x, float[] y, boolean row) {
                this.anchorsX=x.clone(); this.anchorsY=y.clone(); this.row=row;
                startX=new float[x.length]; startY=new float[x.length];
                velocityX=new float[x.length]; velocityY=new float[x.length];
                float lx=x[0], hx=x[0], ly=y[0], hy=y[0];
                for(int i=0;i<x.length;i++) {
                    startX[i]=x[previous[i]]; startY[i]=y[previous[i]];
                    lx=Math.min(lx,x[i]); hx=Math.max(hx,x[i]); ly=Math.min(ly,y[i]); hy=Math.max(hy,y[i]);
                }
                minX=lx-.6F; maxX=hx+.6F; minY=ly-(row?.9F:.6F); maxY=hy+.15F;
            }
            public float coordinate(int identity, float partial, boolean vertical, float spacing) {
                return ConfusionLayout.this.coordinate(this,identity,partial,vertical,spacing);
            }
        }
        public View view(float[] x, float[] y, boolean row) {
            if(x.length!=cells.length || y.length!=cells.length) throw new IllegalArgumentException("Anchor count");
            View view=new View(x,y,row); views.add(new java.lang.ref.WeakReference<>(view)); return view;
        }
        private int age = 100;
        private final int[] durations;
        public ConfusionLayout(int size) {
            cells = new int[size]; previous = new int[size];
            phases = new float[size];
            durations = new int[size]; java.util.Arrays.fill(durations, 30);
            for (int i = 0; i < size; i++) cells[i] = previous[i] = i;
            float[] x=new float[size], y=new float[size];
            for(int i=0;i<size;i++) { x[i]=i%9; y[i]=i/9; }
            if(size==10) { x[9]=-1.45F; y[9]=0; }
            defaultView=view(x,y,size<=10);
        }
        public int cell(int identity) { return cells[identity]; }
        public int identity(int cell) {
            for (int i = 0; i < cells.length; i++) if (cells[i] == cell) return i;
            throw new IllegalArgumentException("Invalid cell " + cell);
        }
        public void tick() { age++; }
        public static int baseDuration(int amplifier) {
            return Math.max(12, (int)Math.round(30 / Math.sqrt(Math.max(0, amplifier) + 1.0)));
        }
        public void swap(Random random, int count) { swap(random, count, 0); }
        public void swap(Random random, int count, int amplifier) { swap(random,count,amplifier,-1); }
        public void swap(Random random, int count, int amplifier, int protectedCell) {
            if (count == 0) return;
            if (count < 2 || count > cells.length) throw new IllegalArgumentException("Invalid shuffle size " + count);
            snapshot();
            System.arraycopy(cells, 0, previous, 0, cells.length);
            int[] identities = new int[cells.length];
            for (int i = 0; i < identities.length; i++) identities[i] = i;
            for (int i = 0; i < count; i++) {
                int j = i + random.nextInt(identities.length - i);
                int v = identities[i]; identities[i] = identities[j]; identities[j] = v;
            }
            if(cells.length==10 && protectedCell>=0 && count<10) {
                boolean offhand=false; int selected=-1;
                for(int i=0;i<count;i++) { offhand |= identities[i]==9; if(cells[identities[i]]==protectedCell) selected=i; }
                if(offhand && selected>=0) { int v=identities[selected]; identities[selected]=identities[count]; identities[count]=v; }
            }
            // One cycle moves every chosen slot, including odd-sized batches.
            int first = cells[identities[0]];
            for (int i = 0; i < count - 1; i++) cells[identities[i]] = cells[identities[i + 1]];
            cells[identities[count - 1]] = first;
            for (int i = 0; i < durations.length; i++) {
                durations[i] = Math.round(baseDuration(amplifier) * (.9F + random.nextFloat() * .2F));
                phases[i] = random.nextFloat() * (float)(Math.PI * 2);
            }
            age = 0;
        }
        public void restore(Random random) {
            int amplifier = 0;
            snapshot();
            System.arraycopy(cells, 0, previous, 0, cells.length);
            for (int i = 0; i < cells.length; i++) cells[i] = i;
            for (int i = 0; i < durations.length; i++) {
                durations[i] = Math.round(baseDuration(amplifier) * (.9F + random.nextFloat() * .2F));
                phases[i] = random.nextFloat() * (float)(Math.PI * 2);
            }
            age = 0;
        }
        public boolean settled() {
            for (int duration : durations) if (age < duration) return false;
            return true;
        }
        private void snapshot() {
            views.removeIf(reference -> reference.get()==null);
            for(var reference:views) {
                View view=reference.get(); if(view==null) continue;
                float[] x=new float[cells.length], y=new float[cells.length], vx=new float[cells.length], vy=new float[cells.length];
                for(int i=0;i<cells.length;i++) {
                    x[i]=view.coordinate(i,0,false,1); y[i]=view.coordinate(i,0,true,1);
                    vx[i]=(view.coordinate(i,.01F,false,1)-view.coordinate(i,-.01F,false,1))/.02F;
                    vy[i]=(view.coordinate(i,.01F,true,1)-view.coordinate(i,-.01F,true,1))/.02F;
                }
                System.arraycopy(x,0,view.startX,0,x.length); System.arraycopy(y,0,view.startY,0,y.length);
                System.arraycopy(vx,0,view.velocityX,0,vx.length); System.arraycopy(vy,0,view.velocityY,0,vy.length);
            }
        }
        public float progress(int identity, float partial) { return Math.max(0, Math.min(1, (age + partial) / durations[identity])); }
        private static float smooth(float t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }
        public static float spring(float t) {
            float u = Math.max(0, Math.min(1, (t - .78F) / .22F));
            float envelope = (float)Math.sin(Math.PI * u);
            return envelope * envelope * envelope * (float)Math.sin(u * Math.PI * 3);
        }
        public static float landingAmplitude(float incomingVelocity, float settlingTicks) {
            return incomingVelocity * settlingTicks / (float)(Math.PI * 3);
        }
        private static float boundedOffset(float base, float offset, float min, float max) {
            float room = offset < 0 ? base - min : max - base;
            if (room <= 0 || offset == 0) return base;
            return base + room * (float)Math.tanh(offset / room);
        }
        private float travelCoordinate(View view, int identity, float t, boolean vertical) {
            float start = vertical ? view.startY[identity] : view.startX[identity];
            int b = cells[identity];
            float end = vertical ? view.anchorsY[b] : view.anchorsX[b];
            float travel = Math.min(1, t / .82F);
            float arc = (float)Math.sin(Math.PI * travel); arc *= arc;
            float bend = vertical && view.row
                ? arc * (-.3F + .7F * (float)Math.sin(t * Math.PI * 2 + phases[identity]))
                : arc * (float)Math.sin(t * Math.PI * 2 + phases[identity]) * (vertical ? 1.3F : .8F);
            float base = start + (end - start) * smooth(travel);
            float min = vertical ? view.minY : view.minX;
            float max = vertical ? view.maxY : view.maxX;
            return boundedOffset(base, bend, min, max);
        }
        public float landingDirection(int identity, boolean vertical) {
            float dx = travelCoordinate(defaultView,identity,.781F,false)-travelCoordinate(defaultView,identity,.779F,false);
            float dy = travelCoordinate(defaultView,identity,.781F,true)-travelCoordinate(defaultView,identity,.779F,true);
            float length = (float)Math.sqrt(dx*dx+dy*dy);
            return length < .000001F ? 0 : (vertical ? dy : dx) / length;
        }
        public float coordinate(int identity, float partial, int columns, boolean vertical, float spacing) {
            return defaultView.coordinate(identity,partial,vertical,spacing);
        }
        private float coordinate(View view, int identity, float partial, boolean vertical, float spacing) {
            float t = progress(identity, partial);
            int a = previous[identity], b = cells[identity];
            float start = vertical ? view.startY[identity] : view.startX[identity];
            float end = vertical ? view.anchorsY[b] : view.anchorsX[b];
            float velocity = vertical ? view.velocityY[identity] : view.velocityX[identity];
            if (t >= 1 || (a == b && start == end && velocity == 0)) return end * spacing;
            float base = travelCoordinate(view,identity,t,vertical);
            float elapsed = Math.max(0, age + partial);
            // Preserve the incoming velocity, then gradually steer into the new curve.
            float carry = velocity * elapsed * (1 - smooth(Math.min(1, elapsed / 6)));
            float approach = (travelCoordinate(view,identity,.781F,vertical)
                - travelCoordinate(view,identity,.779F,vertical)) / .002F;
            // Scale the spring by incoming speed and its short settling window.
            // Dividing by its angular frequency prevents a slow arrival from launching.
            float landing = landingAmplitude(approach / durations[identity], .22F * durations[identity]) * spring(t);
            float min = vertical ? view.minY : view.minX;
            float max = vertical ? view.maxY : view.maxX;
            return boundedOffset(base, carry + landing, min, max) * spacing;
        }
    }

    /** Animates real server exchanges without changing their new storage locations. */
    public static final class OffhandTransfer {
        private static Object owner;
        private static ItemStack previousHand=ItemStack.EMPTY;
        private static final ItemStack[] previous=new ItemStack[9];
        private static ConfusionLayout motion;
        private static ConfusionLayout.View hud;
        private static int target=-1;
        private static float handX, slotX, slotY;
        private static final Random RANDOM=new Random();
        private OffhandTransfer() {}
        public static void tick(Minecraft client) {
            var player = client.player;
            if(player!=owner || client.level==null) {
                owner=player; previousHand=ItemStack.EMPTY; motion=null; target=-1;
                java.util.Arrays.fill(previous,ItemStack.EMPTY);
                if(player==null) return;
                remember(client); return;
            }
            if(player==null || client.isPaused()) return;
            if(motion!=null) { motion.tick(); if(motion.settled()) motion=null; }
            ItemStack hand=player.getOffhandItem();
            if(ConfusionSlots.visible() && !ItemStack.matches(hand,previousHand)) {
                for(int i=0;i<9;i++) {
                    ItemStack stack=player.getInventory().getItem(i);
                    if(ItemStack.matches(stack,previousHand) && ItemStack.matches(hand,previous[i])
                        && !ItemStack.matches(stack,previous[i])) {
                        target=i;
                        handX=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT?9.45F:-1.45F;
                        slotX=ConfusionSlots.hotbar.coordinate(i,0,9,false,1);
                        slotY=ConfusionSlots.hotbar.coordinate(i,0,9,true,1);
                        motion=new ConfusionLayout(2);
                        hud=motion.view(new float[]{handX,slotX},new float[]{0,slotY},true);
                        var effect=player.getEffect(naturality.bootstrap.ContentRegistration.NaturalityEffects.CONFUSION);
                        motion.swap(RANDOM,2,effect==null?0:effect.getAmplifier());
                        break;
                    }
                }
            }
            remember(client);
        }
        private static void remember(Minecraft client) {
            var player = client.player; if (player == null) return;
            previousHand=player.getOffhandItem().copy();
            for(int i=0;i<9;i++) previous[i]=player.getInventory().getItem(i).copy();
        }
        public static float offset(int inventorySlot,float partial,boolean vertical) {
            if(motion==null || (inventorySlot!=40 && inventorySlot!=target)) return 0;
            boolean hand=inventorySlot==40;
            float endpoint=vertical?(hand?0:slotY):(hand?handX:slotX);
            return (hud.coordinate(hand?1:0,partial,vertical,1)-endpoint)*20;
        }
    }
}
