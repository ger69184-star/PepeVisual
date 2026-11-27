package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.mixin.AbstractContainerScreenAccessor;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.ContainerMemory;
import ru.visual.client.util.Draw;

import java.util.List;
import java.util.Optional;

/**
 * Превью шалкеров: наведи прицел на шалкер - увидишь, что внутри.
 * <ul>
 *     <li>Шалкер-предмет на земле или в руке другого игрока - содержимое известно сразу.</li>
 *     <li>Поставленный шалкер (и сундук/бочка) - сервер не присылает содержимое, поэтому показываем то,
 *     что было внутри, когда ты открывал его в последний раз.</li>
 *     <li>В инвентаре - сетка 9x3 при наведении мыши на шалкер.</li>
 * </ul>
 */
public class ShulkerPreview extends Module implements Config.DefaultOn {
    private static ShulkerPreview instance;

    private final BoolSetting world = add(new BoolSetting("В мире", "Показывать содержимое шалкера под прицелом.", true));
    private final BoolSetting storages = add(new BoolSetting("Сундуки и бочки", "Показывать по прицелу и запомненное содержимое сундуков и бочек.", true));
    private final BoolSetting inventory = add(new BoolSetting("В инвентаре", "Сетка содержимого при наведении мыши на шалкер в инвентаре.", true));
    private final BoolSetting items = add(new BoolSetting("Предметы на земле", "Содержимое шалкера, который лежит на земле или в руке игрока под прицелом.", true));
    private final NumSetting scale = add(new NumSetting("Размер", "Масштаб окна превью.", 1.0, 0.6, 1.5, 0.05));

    public ShulkerPreview() {
        super("ShulkerPreview", "Наведи прицел на шалкер - покажет, что в нём. Работает и в инвентаре, и для шалкеров на земле.", Category.VISUAL);
        instance = this;
    }

    private record Preview(String title, List<ItemStack> items, int tint) {}

    private static List<ItemStack> contents(ItemStack st) {
        ItemContainerContents c = st.get(DataComponents.CONTAINER);
        if (c == null) return null;
        NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
        c.copyInto(list);
        return list;
    }

    private static boolean isShulker(ItemStack st) {
        return st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static int tintOf(ItemStack st) {
        if (st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock sb) return tintOf(sb.getColor());
        return 0xFF8A63FF;
    }

    private static int tintOf(DyeColor c) {
        return c == null ? 0xFF9A6FA8 : 0xFF000000 | c.getTextureDiffuseColor();
    }

    private Preview fromStack(ItemStack st, String suffix) {
        if (!isShulker(st)) return null;
        List<ItemStack> list = contents(st);
        if (list == null) list = NonNullList.withSize(27, ItemStack.EMPTY);
        return new Preview(st.getHoverName().getString() + suffix, list, tintOf(st));
    }

    /** Что показывать в мире по прицелу. */
    private Preview worldPreview(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && world.get()) {
            BlockPos pos = bh.getBlockPos();
            BlockEntity be = mc.level.getBlockEntity(pos);
            boolean shulker = be instanceof ShulkerBoxBlockEntity;
            boolean storage = be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
            if (shulker || (storage && storages.get())) {
                String name = mc.level.getBlockState(pos).getBlock().getName().getString();
                int tint = shulker ? tintOf(((ShulkerBoxBlockEntity) be).getColor()) : 0xFF8B6A3E;
                ContainerMemory.Entry e = ContainerMemory.get(pos);
                if (e == null) return new Preview(name + Lang.t(": открой один раз, чтобы запомнить"), null, tint);
                long sec = (System.currentTimeMillis() - e.time()) / 1000;
                String ago = sec < 5 ? Lang.t("сейчас") : sec < 60 ? sec + Lang.t(" сек назад") : (sec / 60) + Lang.t(" мин назад");
                return new Preview(name + " (" + ago + ")", e.items(), tint);
            }
        }
        if (!items.get()) return null;
        Entity ce = mc.crosshairPickEntity;
        if (ce instanceof Player pl) {
            Preview p = fromStack(pl.getMainHandItem(), Lang.t(" - в руке ") + pl.getName().getString());
            if (p != null) return p;
        }
        ItemEntity ie = lookedItem(mc);
        if (ie != null) return fromStack(ie.getItem(), Lang.t(" (на земле)"));
        return null;
    }

    /** Предмет-сущность на линии взгляда (обычный прицел предметы не "видит"). */
    private static ItemEntity lookedItem(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f);
        Vec3 end = eye.add(look.scale(6));
        double best = Double.MAX_VALUE;
        ItemEntity found = null;
        double blockDist = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? mc.hitResult.getLocation().distanceTo(eye) : 99;
        for (ItemEntity ie : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(6))) {
            if (!isShulker(ie.getItem())) continue;
            AABB box = ie.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(eye);
            if (d < best && d <= blockDist + 0.5) {
                best = d;
                found = ie;
            }
        }
        return found;
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (mc.screen != null) return;
        Preview p = worldPreview(mc);
        if (p == null) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        drawPreview(g, p, sw / 2 + 12, sh / 2 + 8, sw, sh);
    }

    /** Вызывается из ScreenEvents.afterRender для экранов инвентаря. */
    public static void onScreenRender(Screen screen, GuiGraphics g, int mouseX, int mouseY) {
        ShulkerPreview m = instance;
        if (m == null || !m.isEnabled() || !m.inventory.get()) return;
        if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
        Slot s = ((AbstractContainerScreenAccessor) cs).pepevisual$hoveredSlot();
        if (s == null || !s.hasItem()) return;
        Preview p = m.fromStack(s.getItem(), "");
        if (p == null) return;
        boolean any = false;
        for (ItemStack st : p.items) if (!st.isEmpty()) { any = true; break; }
        if (!any) return;
        int h = (int) ((3 * 18 + 20) * m.scale.get());
        // над курсором, чтобы не закрывать ванильную подсказку
        m.drawPreview(g, p, mouseX + 8, mouseY - h - 8, screen.width, screen.height);
    }

    private void drawPreview(GuiGraphics g, Preview p, int x, int y, int sw, int sh) {
        float sc = scale.asFloat();
        int cols = 9;
        int rows = p.items == null ? 0 : Math.max(1, (p.items.size() + 8) / 9);
        int titleW = Draw.width(p.title) + 10;
        int w = Math.max(cols * 18 + 8, titleW);
        int h = 14 + rows * 18 + (rows > 0 ? 6 : 0);
        // не вылезаем за экран (в координатах с учётом масштаба)
        int realW = (int) (w * sc), realH = (int) (h * sc);
        if (x + realW > sw - 2) x = sw - 2 - realW;
        if (y + realH > sh - 2) y = sh - 2 - realH;
        if (x < 2) x = 2;
        if (y < 2) y = 2;

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(sc, sc);
        int tint = p.tint;
        Draw.shadow(g, 0, 0, w, h, 6, 0x90000000);
        Draw.round(g, -1, -1, w + 1, h + 1, 6, Draw.withAlpha(tint, 200));
        Draw.round(g, 0, 0, w, h, 5, 0xF0101018);
        Draw.roundGradient(g, 0, 0, w, 13, 5, Draw.withAlpha(Draw.shade(tint, 0.7f), 235), Draw.withAlpha(Draw.shade(tint, 0.45f), 235));
        Draw.text(g, p.title, 5, 2, Draw.WHITE);
        if (p.items != null) {
            int total = 0;
            for (int i = 0; i < p.items.size(); i++) {
                int cx = 4 + (i % cols) * 18, cy = 16 + (i / cols) * 18;
                Draw.round(g, cx, cy, cx + 17, cy + 17, 2, 0x30FFFFFF);
                ItemStack st = p.items.get(i);
                if (!st.isEmpty()) {
                    Draw.item(g, st, cx + 1, cy + 1);
                    total++;
                }
            }
            if (total == 0) Draw.textCentered(g, Lang.t("пусто"), w / 2, 16 + rows * 9 - 4, Draw.GRAY);
        }
        pose.popMatrix();
    }
}
