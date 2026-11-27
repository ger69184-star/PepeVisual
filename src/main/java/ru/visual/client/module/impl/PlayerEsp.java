package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.R3D;
import ru.visual.client.util.TargetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ESP игроков без каши на экране:
 * <ul>
 *     <li>подписи не налезают друг на друга - ближние важнее, дальние сдвигаются вверх или прячутся;</li>
 *     <li>дальние игроки рисуются мельче и прозрачнее;</li>
 *     <li>предметы и броня - только у ближних и у цели;</li>
 *     <li>ничего не рисуется поверх хотбара и полосок здоровья.</li>
 * </ul>
 */
public class PlayerEsp extends Module implements Config.DefaultOn {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final ModeSetting style = add(new ModeSetting("Рамка", "Углы - аккуратные уголки. Рамка - 2D-прямоугольник. 3D - коробка в мире.", "Углы", "Углы", "Рамка", "3D", "Нет"));
    private final BoolSetting healthBar = add(new BoolSetting("Полоса HP", "Тонкая полоса здоровья слева от рамки.", true));
    private final BoolSetting nametag = add(new BoolSetting("Подписи", "Ник, здоровье и дистанция над игроком. Не налезают друг на друга.", true));
    private final BoolSetting distance = add(new BoolSetting("Дистанция в подписи", "Добавлять метры к подписи.", true));
    private final NumSetting maxTags = add(new NumSetting("Макс. подписей", "Сколько подписей показывать одновременно (ближние важнее).", 8, 1, 30, 1));
    private final BoolSetting heldItem = add(new BoolSetting("Предмет в руке", "Что игрок держит (только у ближних и у цели).", true));
    private final BoolSetting armor = add(new BoolSetting("Броня", "Иконки брони (только у ближних и у цели).", true));
    private final NumSetting detail = add(new NumSetting("Детали до", "До какой дистанции показывать предметы и броню (блоки).", 16, 4, 64, 2));
    private final BoolSetting mobs = add(new BoolSetting("Мобы", "Показывать и мобов (жёлтая рамка, без предметов).", false));
    private final ColorSetting color = add(new ColorSetting("Цвет рамки", "Цвет рамки игроков. Цель выделяется красным.", 0xFFFFFF));
    private final NumSetting range = add(new NumSetting("Дистанция", "Максимальная дистанция, на которой рисуются игроки (блоки).", 96, 16, 256, 8));

    private record Entry(LivingEntity e, int x1, int y1, int x2, int y2, double dist, boolean player, boolean target) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<int[]> taken = new ArrayList<>();

    public PlayerEsp() {
        super("PlayerESP", "Показывает игроков сквозь стены: рамка, ник, HP, предмет в руке и броня. Подписи не налезают друг на друга.", Category.VISUAL);
    }

    private boolean wanted(Minecraft mc, Entity e) {
        if (!(e instanceof LivingEntity p) || p == mc.player || p.isRemoved() || !p.isAlive()) return false;
        if (p instanceof Player pl) return !pl.isSpectator();
        return mobs.get() && p.getType() != EntityType.ARMOR_STAND;
    }

    private int baseColor(boolean player, boolean target) {
        return target ? 0xFFFF4545 : (player ? color.argb() : 0xFFFFD84A);
    }

    /** Прозрачность по дистанции: ближние - полностью, дальние - до 40%. */
    private float fade(double d) {
        double r = range.get();
        double f = 1.0 - (d - r * 0.5) / (r * 0.5);
        return (float) Math.max(0.4, Math.min(1.0, f));
    }

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        if (!style.is("3D")) return;
        double r2 = range.get() * range.get();
        int count = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!wanted(mc, e) || e.distanceToSqr(mc.player) > r2) continue;
            if (++count > 60) break;
            LivingEntity p = (LivingEntity) e;
            Vec3 pos = p.getPosition(pt);
            double w = p.getBbWidth() / 2.0 + 0.05, h = p.getBbHeight() + 0.05;
            AABB box = new AABB(pos.x - w, pos.y, pos.z - w, pos.x + w, pos.y + h, pos.z + w);
            boolean target = TargetManager.isTarget(p);
            int col = baseColor(p instanceof Player, target);
            int a = (int) (255 * fade(Math.sqrt(e.distanceToSqr(mc.player))));
            R3D.boxFilled(box, Draw.withAlpha(col, a / 7));
            R3D.boxOutline(box, Draw.withAlpha(col, a), target ? 2.5f : 1.5f);
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int sw = mc.getWindow().getGuiScaledWidth();
        int bottom = Draw.hudTop(mc) - 2;
        entries.clear();
        double r2 = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!wanted(mc, e) || e.distanceToSqr(mc.player) > r2) continue;
            LivingEntity p = (LivingEntity) e;
            Vec3 pos = p.getPosition(pt);
            double w = p.getBbWidth() / 2.0, h = p.getBbHeight();
            double[] r = Projection.boxRect(new AABB(pos.x - w, pos.y, pos.z - w, pos.x + w, pos.y + h, pos.z + w));
            if (r == null) continue;
            entries.add(new Entry(p, (int) r[0], (int) r[1], (int) r[2], (int) r[3], Math.sqrt(e.distanceToSqr(mc.player)),
                    p instanceof Player, TargetManager.isTarget(p)));
            if (entries.size() >= 60) break;
        }
        // ближние первыми: их подписи важнее, дальние уступают место
        entries.sort((a, b) -> a.target != b.target ? (a.target ? -1 : 1) : Double.compare(a.dist, b.dist));

        // 1) рамки и полосы здоровья - дальние снизу, ближние поверх
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry en = entries.get(i);
            float f = fade(en.dist);
            int col = Draw.withAlpha(baseColor(en.player, en.target), (int) (255 * f));
            int shadow = Draw.withAlpha(0, (int) (200 * f));
            if (style.is("Углы")) corners(g, en.x1, en.y1, en.x2, en.y2, col, shadow);
            else if (style.is("Рамка")) {
                Draw.outline(g, en.x1 - 1, en.y1 - 1, en.x2 + 1, en.y2 + 1, shadow);
                Draw.outline(g, en.x1, en.y1, en.x2, en.y2, col);
            }
            if (healthBar.get() && en.y2 - en.y1 > 4) {
                LivingEntity p = en.e;
                int bx = en.x1 - 4;
                Draw.rect(g, bx - 1, en.y1 - 1, bx + 2, en.y2 + 1, shadow);
                float hf = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
                int top = en.y2 - Math.round((en.y2 - en.y1) * hf);
                Draw.rect(g, bx, top, bx + 1, en.y2, Draw.withAlpha(Draw.healthColor(p.getHealth(), p.getMaxHealth()), (int) (255 * f)));
                if (p.getAbsorptionAmount() > 0) {
                    float af = Math.min(1f, p.getAbsorptionAmount() / Math.max(1f, p.getMaxHealth()));
                    Draw.rect(g, bx, en.y2 - Math.round((en.y2 - en.y1) * af), bx + 1, en.y2, Draw.withAlpha(0xFFD700, (int) (255 * f)));
                }
            }
        }

        // 2) подписи без наложения
        taken.clear();
        int tags = 0, details = 0;
        for (Entry en : entries) {
            boolean near = en.target || en.dist <= detail.get();
            int labelTop = en.y1;
            if (nametag.get() && tags < maxTags.asInt()) {
                int placed = label(g, mc, en, sw, bottom);
                if (placed != Integer.MIN_VALUE) {
                    tags++;
                    labelTop = placed;
                }
            }
            if (!en.player || !near || details >= 4) continue;
            int below = en.y2 + 3;
            if (below + 16 > bottom) continue;
            details++;
            int cx = (en.x1 + en.x2) / 2;
            if (heldItem.get()) {
                below = drawHeld(g, en.e.getMainHandItem(), cx, below, bottom);
                below = drawHeld(g, en.e.getOffhandItem(), cx, below, bottom);
            }
            if (armor.get() && below + 16 <= bottom) {
                int n = 0;
                for (EquipmentSlot s : ARMOR) if (!en.e.getItemBySlot(s).isEmpty()) n++;
                if (n > 0) {
                    int ax = cx - n * 8;
                    Draw.round(g, ax - 2, below - 1, ax + n * 16 + 2, below + 17, 3, 0x70000000);
                    for (EquipmentSlot s : ARMOR) {
                        ItemStack st = en.e.getItemBySlot(s);
                        if (st.isEmpty()) continue;
                        Draw.item(g, st, ax, below);
                        ax += 16;
                    }
                    taken.add(new int[]{cx - n * 8 - 2, below - 1, cx + n * 8 + 2, below + 17});
                }
            }
        }
    }

    /** Рисует подпись; возвращает её верх или MIN_VALUE, если места не нашлось. */
    private int label(GuiGraphics g, Minecraft mc, Entry en, int sw, int bottom) {
        LivingEntity p = en.e;
        float hp = p.getHealth() + p.getAbsorptionAmount();
        String name = Draw.trim(p.getName().getString(), 110);
        String hpS = String.format(Locale.ROOT, hp >= 10 ? "%.0f" : "%.1f", hp);
        String dS = distance.get() ? (int) en.dist + Lang.t("м") : "";
        int nw = Draw.width(name), hw = Draw.width(hpS), dw = dS.isEmpty() ? 0 : Draw.width(dS);
        int tw = nw + 5 + hw + (dw > 0 ? 5 + dw : 0);
        float s = (float) Math.max(0.7, Math.min(1.0, 1.0 - (en.dist - 12) / 120.0));
        if (en.target) s = 1f;
        int lw = Math.round((tw + 8) * s), lh = Math.round(12 * s);
        int cx = (en.x1 + en.x2) / 2;
        int x1 = cx - lw / 2;
        if (x1 < 2) x1 = 2;
        if (x1 + lw > sw - 2) x1 = sw - 2 - lw;
        int yb = en.y1 - 3;
        // ищем свободное место: прямо над рамкой, потом выше, ещё выше
        for (int tries = 0; tries < 4; tries++) {
            int y1 = yb - lh;
            if (y1 < 2) return Integer.MIN_VALUE;
            if (yb > bottom) return Integer.MIN_VALUE;
            int[] r = {x1, y1, x1 + lw, yb};
            int[] hit = overlap(r);
            if (hit == null) {
                taken.add(r);
                draw(g, en, r, s, name, hpS, dS, nw, hw);
                return y1;
            }
            if (!en.target && tries >= 1 && en.dist > 40) return Integer.MIN_VALUE; // дальних не громоздим
            yb = hit[1] - 1;
        }
        return Integer.MIN_VALUE;
    }

    private int[] overlap(int[] r) {
        for (int[] t : taken) if (r[0] < t[2] && r[2] > t[0] && r[1] < t[3] && r[3] > t[1]) return t;
        return null;
    }

    private void draw(GuiGraphics g, Entry en, int[] r, float s, String name, String hpS, String dS, int nw, int hw) {
        float f = en.target ? 1f : fade(en.dist);
        int a = (int) (255 * f);
        int bg = Draw.withAlpha(0x0E0E14, (int) (0xB0 * f));
        int hpCol = Draw.withAlpha(Draw.healthColor(en.e.getHealth(), en.e.getMaxHealth()), a);
        int nameCol = Draw.withAlpha(en.target ? 0xFF8080 : 0xFFFFFF, a);
        int distCol = Draw.withAlpha(0xA0A0A8, a);
        float ax = r[0], ay = r[1];
        Draw.scaled(g, s, ax, ay, () -> {
            int x = r[0], y = r[1];
            int w = Math.round((r[2] - r[0]) / s);
            Draw.round(g, x, y, x + w, y + 12, 3, bg);
            if (en.target) Draw.rect(g, x + 3, y + 11, x + w - 3, y + 12, Draw.withAlpha(0xFF4545, a));
            else if (en.player) Draw.rect(g, x + 3, y + 11, x + w - 3, y + 12, Draw.withAlpha(Draw.accent(), a / 2));
            int tx = x + 4;
            Draw.text(g, name, tx, y + 2, nameCol);
            tx += nw + 5;
            Draw.text(g, hpS, tx, y + 2, hpCol);
            tx += hw + 5;
            if (!dS.isEmpty()) Draw.text(g, dS, tx, y + 2, distCol);
        });
    }

    private static void corners(GuiGraphics g, int x1, int y1, int x2, int y2, int col, int shadow) {
        int lx = Math.max(2, (x2 - x1) / 3), ly = Math.max(2, (y2 - y1) / 4);
        int[][] seg = {
                {x1, y1, x1 + lx, y1 + 1}, {x1, y1, x1 + 1, y1 + ly},
                {x2 - lx, y1, x2, y1 + 1}, {x2 - 1, y1, x2, y1 + ly},
                {x1, y2 - 1, x1 + lx, y2}, {x1, y2 - ly, x1 + 1, y2},
                {x2 - lx, y2 - 1, x2, y2}, {x2 - 1, y2 - ly, x2, y2}};
        for (int[] s : seg) g.fill(s[0] - 1, s[1] - 1, s[2] + 1, s[3] + 1, shadow);
        for (int[] s : seg) g.fill(s[0], s[1], s[2], s[3], col);
    }

    private int drawHeld(GuiGraphics g, ItemStack st, int cx, int y, int bottom) {
        if (st.isEmpty() || y + 16 > bottom) return y;
        String name = Draw.trim(st.getHoverName().getString(), 100) + (st.getCount() > 1 ? " x" + st.getCount() : "");
        int tw = Draw.width(name);
        int total = 18 + tw;
        int x = cx - total / 2;
        int[] r = {x - 2, y - 1, x + total + 3, y + 17};
        if (overlap(r) != null) return y;
        taken.add(r);
        Draw.round(g, r[0], r[1], r[2], r[3], 3, 0x90000000);
        g.renderItem(st, x, y);
        Draw.text(g, name, x + 18, y + 4, 0xFFFFE070);
        return y + 19;
    }
}
