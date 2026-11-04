package naturality.items.golden.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import naturality.items.golden.GoldenDye;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Structural hooks are CONTENT-gated before enum and entity tables initialize. */
public final class GoldenDyeMixins {
    private GoldenDyeMixins() {}

    @Mixin(DyeColor.class)
    public interface Constructor {
        @Invoker("<init>")
        static DyeColor naturality$newColor(String enumName, int ordinal, int id, String name,
                int diffuse, MapColor map, MapColor terracotta, int firework, int text) {
            throw new AssertionError("Dye constructor invoker missing");
        }
    }

    @Mixin(DyeColor.class)
    public static abstract class Colors {
        @Shadow @Final @Mutable private static DyeColor[] $VALUES;

        @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/DyeColor;values()[Lnet/minecraft/world/item/DyeColor;"))
        private static DyeColor[] naturality$append(DyeColor[] original) {
            DyeColor[] expanded = Arrays.copyOf(original, original.length + 1);
            expanded[original.length] = Constructor.naturality$newColor("GOLDEN", original.length, 16, "golden",
                GoldenDye.RGB, MapColor.GOLD, MapColor.GOLD, GoldenDye.RGB, GoldenDye.RGB);
            $VALUES = expanded;
            return expanded;
        }
    }

    @Mixin(ColorCollection.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class Collections<T> implements GoldenDye.Extension<T> {
        @Unique private @Nullable T naturality$golden;
        @Override public @Nullable T naturality$golden() { return naturality$golden; }
        @Override public void naturality$golden(T value) { naturality$golden = value; }

        @Inject(method = "<clinit>", at = @At("TAIL"))
        private static void naturality$colorNames(CallbackInfo ci) {
            GoldenDye.extension(ColorCollection.VALUES).naturality$golden(GoldenDye.color());
            GoldenDye.extension(ColorCollection.NAMES).naturality$golden("golden");
        }
        @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
        private void naturality$pick(DyeColor color, CallbackInfoReturnable<T> cir) {
            if (GoldenDye.isGolden(color)) {
                @Nullable T value = naturality$golden;
                if (value == null) throw new IllegalStateException("Golden entry missing from color collection");
                cir.setReturnValue(value);
            }
        }
        @Inject(method = "forEach", at = @At("TAIL"))
        private void naturality$each(Consumer<T> consumer, CallbackInfo ci) {
            @Nullable T value = naturality$golden; if (value != null) consumer.accept(value);
        }
        @Inject(method = "map", at = @At("RETURN"))
        private <U> void naturality$map(Function<T, U> mapper, CallbackInfoReturnable<ColorCollection<U>> cir) {
            @Nullable T value = naturality$golden;
            if (value != null) GoldenDye.extension(cir.getReturnValue()).naturality$golden(mapper.apply(value));
        }
        @Inject(method = "zipApply", at = @At("TAIL"))
        private static <T, U> void naturality$zip(ColorCollection<T> first, ColorCollection<U> second, BiConsumer<T, U> consumer, CallbackInfo ci) {
            @Nullable T a = GoldenDye.extension(first).naturality$golden(); @Nullable U b = GoldenDye.extension(second).naturality$golden();
            if (a != null && b != null) consumer.accept(a, b);
        }
        @Inject(method = "zipMap", at = @At("RETURN"))
        private static <T, U, R> void naturality$zipMap(ColorCollection<T> first, ColorCollection<U> second,
                BiFunction<T, U, R> operation, CallbackInfoReturnable<ColorCollection<R>> cir) {
            @Nullable T a = GoldenDye.extension(first).naturality$golden(); @Nullable U b = GoldenDye.extension(second).naturality$golden();
            if (a != null && b != null) GoldenDye.extension(cir.getReturnValue()).naturality$golden(operation.apply(a, b));
        }
    }

    @Mixin(Sheep.class)
    public static abstract class SheepColor {
        @Shadow @Final private static EntityDataAccessor<Byte> DATA_WOOL_ID;
        @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
        private void naturality$read(CallbackInfoReturnable<DyeColor> cir) {
            byte packed = ((Sheep)(Object)this).getEntityData().get(DATA_WOOL_ID);
            if ((packed & 32) != 0) cir.setReturnValue(GoldenDye.color());
        }
        @Inject(method = "setColor", at = @At("HEAD"), cancellable = true)
        private void naturality$write(DyeColor color, CallbackInfo ci) {
            var data = ((Sheep)(Object)this).getEntityData();
            // Keep vanilla's sheared bit (16), using the next spare bit for golden wool.
            int packed = (data.get(DATA_WOOL_ID) & 0xD0) | (GoldenDye.isGolden(color) ? 32 : color.getId());
            data.set(DATA_WOOL_ID, (byte)packed); ci.cancel();
        }
    }

    @Mixin(Shulker.class)
    public static abstract class ShulkerColor {
        @Shadow @Final private static EntityDataAccessor<Byte> DATA_COLOR_ID;
        @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
        private void naturality$read(CallbackInfoReturnable<DyeColor> cir) {
            if (((Shulker)(Object)this).getEntityData().get(DATA_COLOR_ID) == 17) cir.setReturnValue(GoldenDye.color());
        }
        @Inject(method = "setVariant", at = @At("HEAD"), cancellable = true)
        private void naturality$write(java.util.Optional<DyeColor> color, CallbackInfo ci) {
            if (color.isPresent() && GoldenDye.isGolden(color.get())) {
                // Sixteen already means undyed in vanilla saves; do not reinterpret it.
                ((Shulker)(Object)this).getEntityData().set(DATA_COLOR_ID, (byte)17); ci.cancel();
            }
        }
    }

    @Mixin(net.minecraft.world.level.block.entity.BlockEntityType.class)
    public static abstract class BlockEntities {
        @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
        private void naturality$support(net.minecraft.world.level.block.state.BlockState state, CallbackInfoReturnable<Boolean> cir) {
            var block = state.getBlock();
            if ((Object)this == net.minecraft.world.level.block.entity.BlockEntityTypes.BANNER
                    && (block == GoldenDye.BLOCKS.get("golden_banner") || block == GoldenDye.BLOCKS.get("golden_wall_banner"))
                    || (Object)this == net.minecraft.world.level.block.entity.BlockEntityTypes.SHULKER_BOX
                    && block == GoldenDye.BLOCKS.get("golden_shulker_box")) cir.setReturnValue(true);
        }
    }

    @Mixin(net.minecraft.world.item.CreativeModeTabs.class)
    public static abstract class CreativeTabs {
        @Inject(method = "registerColoredItems", at = @At("TAIL"))
        private static void naturality$golden(net.minecraft.world.item.CreativeModeTab.Output output, List<DyeColor> order,
                ColorCollection<net.minecraft.world.item.Item> items, CallbackInfo ci) {
            var item = GoldenDye.extension(items).naturality$golden();
            if (item != null && !order.contains(GoldenDye.color())) output.accept(item);
        }
    }

    @Mixin(DyedItemColor.class)
    public static abstract class EquipmentColor {
        @Inject(method = "applyDyes(Lnet/minecraft/world/item/component/DyedItemColor;Ljava/util/List;)Lnet/minecraft/world/item/component/DyedItemColor;", at = @At("HEAD"), cancellable = true)
        private static void naturality$golden(@Nullable DyedItemColor current, List<DyeColor> dyes, CallbackInfoReturnable<DyedItemColor> cir) {
            if (dyes.stream().anyMatch(GoldenDye::isGolden)) cir.setReturnValue(new DyedItemColor(GoldenDye.RGB));
        }
    }

    @Mixin(net.minecraft.world.level.saveddata.maps.MapBanner.class)
    public static abstract class BannerMap {
        @Inject(method = "getDecoration", at = @At("HEAD"), cancellable = true)
        private void naturality$map(CallbackInfoReturnable<net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType>> cir) {
            if (GoldenDye.isGolden(((net.minecraft.world.level.saveddata.maps.MapBanner)(Object)this).color()))
                cir.setReturnValue(GoldenDye.bannerMarker());
        }
    }
}
