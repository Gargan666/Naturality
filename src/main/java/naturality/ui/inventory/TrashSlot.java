package naturality.ui.inventory;

import naturality.Naturality;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One recoverable stack; only an explicit incoming placement replaces its contents. */
@NullMarked
public final class TrashSlot extends Slot {
    public static final int PANEL_SIZE = 32;
    public static final int PANEL_INSET = 8;
    private static final Identifier EMPTY_ICON = Naturality.id("container/slot/trash");
    private static final String SAVE_KEY = "NaturalityTrashSlot";
    private final Player owner;
    private final boolean playerMenu;

    private TrashSlot(Player owner, Container storage, int x, int y, boolean playerMenu) {
        super(storage, 0, x, y);
        this.owner = owner;
        this.playerMenu = playerMenu;
    }

    public interface MenuAccess {
        void naturality$ensureTrashSlot();
    }

    public static void attach(AbstractContainerMenu menu) {
        ((MenuAccess) menu).naturality$ensureTrashSlot();
    }

    public static @Nullable TrashSlot find(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot instanceof TrashSlot trash) return trash;
        }
        return null;
    }

    /** Called only after menu construction, preserving every native slot index. */
    public static @Nullable TrashSlot create(AbstractContainerMenu menu) {
        if (find(menu) != null) return null;
        for (Slot anchor : menu.slots) {
            if (!(anchor.container instanceof Inventory inventory) || anchor.getContainerSlot() != 8) continue;
            boolean[] present = new boolean[Inventory.INVENTORY_SIZE];
            for (Slot slot : menu.slots) {
                int index = slot.getContainerSlot();
                if (slot.container == inventory && index >= 0 && index < present.length) present[index] = true;
            }
            // Creative item catalogs contain only a hotbar, not the full player inventory.
            for (boolean included : present) {
                if (!included) return null;
            }
            boolean playerMenu = menu instanceof InventoryMenu;
            Container storage = playerMenu ? new SimpleContainer(1) : of(inventory.player).container;
            return new TrashSlot(inventory.player, storage, anchor.x + 36, anchor.y, playerMenu);
        }
        return null;
    }

    @Override
    public @Nullable Identifier getNoItemIcon() {
        return EMPTY_ICON;
    }

    @Override
    public boolean isActive() {
        // Creative's inventory tab wraps every survival slot; it already has its own delete slot.
        return !playerMenu || !owner.hasInfiniteMaterials();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // Explicit placement is handled by click; automatic distribution must never insert here.
        return false;
    }

    public static boolean click(AbstractContainerMenu menu, int slotIndex, int button,
            ContainerInput input, Player player) {
        if (slotIndex < 0 || slotIndex >= menu.slots.size()
                || !(menu.getSlot(slotIndex) instanceof TrashSlot trash)) return false;
        if (!trash.isActive()) return true;
        if (input == ContainerInput.QUICK_MOVE) {
            trash.recover(menu, player);
            return true;
        }
        ItemStack carried = menu.getCarried();
        if (input == ContainerInput.PICKUP && (button == 0 || button == 1) && !carried.isEmpty()) {
            int count = button == 0 ? carried.getCount() : 1;
            trash.setByPlayer(carried.split(Math.min(count, trash.getMaxStackSize(carried))));
            if (carried.isEmpty()) menu.setCarried(ItemStack.EMPTY);
            return true;
        }
        if (input == ContainerInput.SWAP && (button >= 0 && button < 9 || button == 40)) {
            ItemStack source = player.getInventory().getItem(button);
            if (!source.isEmpty()) {
                trash.setByPlayer(source.split(Math.min(source.getCount(), trash.getMaxStackSize(source))));
                if (source.isEmpty()) player.getInventory().setItem(button, ItemStack.EMPTY);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    private void recover(AbstractContainerMenu menu, Player player) {
        ItemStack stack = getItem();
        // Merge first, then fill empty player slots. A full inventory leaves the remainder here,
        // including in creative where Inventory.add would discard an uninsertable remainder.
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (Slot destination : menu.slots) {
                if (destination.container == player.getInventory() && destination.getContainerSlot() < Inventory.INVENTORY_SIZE
                        && destination.hasItem() == (pass == 0)) {
                    destination.safeInsert(stack);
                    if (stack.isEmpty()) break;
                }
            }
        }
        if (stack.isEmpty()) setByPlayer(ItemStack.EMPTY);
        else setChanged();
    }

    private static TrashSlot of(Player player) {
        TrashSlot trash = find(player.inventoryMenu);
        if (trash != null) return trash;
        throw new IllegalStateException("Player inventory has no Naturality trash slot");
    }

    public static void save(Player player, ValueOutput output) {
        ItemStack stack = of(player).getItem();
        if (!stack.isEmpty()) output.store(SAVE_KEY, ItemStack.CODEC, stack);
    }

    public static void load(Player player, ValueInput input) {
        of(player).set(input.read(SAVE_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY));
    }

    public static void transfer(Player previous, Player replacement) {
        of(replacement).set(of(previous).getItem().copy());
        of(previous).set(ItemStack.EMPTY);
    }

    public static void dropOnDeath(Player player, ServerLevel level) {
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) return;
        TrashSlot trash = of(player);
        ItemStack stack = trash.getItem();
        trash.set(ItemStack.EMPTY);
        if (!stack.isEmpty() && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
            var drop = player.createItemStackToDrop(stack, true, false);
            if (drop != null) level.addFreshEntity(drop);
        }
    }
}
