package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.HeldTracker;
import ru.visual.client.util.TargetManager;

import java.util.List;

/**
 * Хотбар другого игрока: на кого наведён прицел (хоть за 60 блоков) или текущая цель.
 * Сервер не отдаёт клиенту чужой инвентарь, поэтому слоты собираются из того, что игрок брал в руки
 * ({@link HeldTracker}); то, что у него в руке сейчас, подсвечено. Плюс броня и вторая рука - они видны всегда.
 */
public class HotbarView extends HudModule implements Config.DefaultOn {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final NumSetting range = add(new NumSetting("Дальность прицела", "На каком расстоянии ловить игрока под прицелом (блоки).", 64, 8, 128, 4));
    private final BoolSetting useTarget = add(new BoolSetting("Цель, если никого под прицелом", "Показывать цель (кого бил или на кого смотрел), когда прицел ни на ком.", true));
    private final NumSetting hold = add(new NumSetting("Держать после взгляда", "Сколько секунд панель остаётся после того, как отвёл прицел.", 2, 0, 10, 1));
    private final BoolSetting armor = add(new BoolSetting("Броня", "Показывать броню и вторую руку.", true));

    private Player shown;
    private long shownTime;
    private float anim;

    public HotbarView() {
        super("HotbarView", "Хотбар игрока под прицелом или цели: что у него в руке сейчас и что он уже доставал (жемчуг, зелья, тотемы). Сервер не присылает чужой инвентарь, поэтому слоты заполняются по мере того, как игрок их берёт в руку.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        long now = System.currentTimeMillis();
        Player p = TargetManager.aimedPlayer(mc, range.get());
        if (p == null && useTarget.get() && TargetManager.get() instanceof Player tp) p = tp;
        if (p != null) {
            shown = p;
            shownTime = now;
        } else if (shown != null && (now - shownTime > hold.get() * 1000 || shown.isRemoved() || shown.level() != mc.level)) {
            shown = null;
        }
        boolean editor = mc.screen instanceof ru.visual.client.gui.HudEditorScreen;
        Player t = shown != null ? shown : editor ? mc.player : null;
        anim += ((t != null ? 1f : 0f) - anim) * 0.25f;
        if (t == null || anim < 0.05f) return;

        int slot = 18, pad = 5;
        int pw = pad * 2 + slot * HeldTracker.SLOTS - 2;
        int ph = armor.get() ? 64 : 44;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 - 30 - pw + ox();
        int y = Math.min(h / 2 + 26, Draw.hudTop(mc) - 40 - ph) + oy();
        int a = (int) (255 * anim);

        bounds(x, y, x + pw, y + ph);
        Draw.panel(g, x, y, x + pw, y + ph, Draw.withAlpha(Draw.bg(), (Draw.bg() >>> 24) * a / 255));
        Draw.round(g, x + 2, y + 4, x + 4, y + ph - 4, 1, Draw.accent());

        // голова, ник, здоровье
        if (t instanceof AbstractClientPlayer acp) PlayerFaceRenderer.draw(g, acp.getSkin(), x + 8, y + 5, 12);
        float hp = t.getHealth() + t.getAbsorptionAmount();
        String hpS = Math.round(hp) + " HP";
        String dist = (int) mc.player.distanceTo(t) + Lang.t(" м");
        int right = x + pw - pad;
        Draw.textRight(g, hpS, right, y + 6, t.getAbsorptionAmount() > 0 ? 0xFFFFD700 : Draw.healthColor(t.getHealth(), t.getMaxHealth()));
        int nameMax = pw - 24 - Draw.width(hpS) - Draw.width(dist) - 14;
        Draw.text(g, Draw.trim(t.getName().getString(), nameMax), x + 24, y + 6, Draw.WHITE);
        Draw.textRight(g, dist, right - Draw.width(hpS) - 6, y + 6, Draw.GRAY);

        // хотбар
        int hy = y + 22;
        ItemStack hand = t.getMainHandItem();
        List<HeldTracker.Seen> seen = HeldTracker.seen(t);
        int sx = x + pad;
        for (int i = 0; i < HeldTracker.SLOTS; i++) {
            int cx = sx + i * slot;
            Draw.round(g, cx - 1, hy - 1, cx + 17, hy + 17, 2, 0x50000000);
            if (i < seen.size()) {
                HeldTracker.Seen s = seen.get(i);
                boolean inHand = !hand.isEmpty() && ItemStack.isSameItem(s.stack, hand);
                if (inHand) {
                    Draw.round(g, cx - 1, hy - 1, cx + 17, hy + 17, 2, Draw.withAlpha(Draw.accent(), 110));
                    Draw.roundOutline(g, cx - 1, hy - 1, cx + 17, hy + 17, 2, Draw.accent());
                }
                Draw.item(g, inHand ? hand : s.stack, cx, hy);
            }
        }
        if (seen.isEmpty()) Draw.textCentered(g, hand.isEmpty() ? Lang.t("Пустая рука") : Lang.t("Ждём, пока сменит слот…"), x + pw / 2, hy + 4, Draw.GRAY);

        if (armor.get()) {
            int ay = hy + 21;
            int ax = sx;
            for (EquipmentSlot s : ARMOR) {
                ItemStack st = t.getItemBySlot(s);
                if (st.isEmpty()) Draw.outline(g, ax, ay, ax + 16, ay + 16, 0x30FFFFFF);
                else Draw.item(g, st, ax, ay);
                ax += slot;
            }
            ItemStack off = t.getOffhandItem();
            ax += 6;
            if (off.isEmpty()) Draw.outline(g, ax, ay, ax + 16, ay + 16, 0x30FFFFFF);
            else Draw.item(g, off, ax, ay);
            String dur = armorSummary(t);
            if (dur != null) Draw.textRight(g, dur, right, ay + 4, Draw.GRAY);
        }
    }

    /** Общая прочность брони: одно число, если всё одинаково, иначе минимальная. */
    static String armorSummary(LivingEntity t) {
        int min = 101, max = -1;
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = t.getItemBySlot(s);
            if (st.isEmpty() || !st.isDamageableItem() || st.getMaxDamage() <= 0) continue;
            int p = (int) Math.floor(100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        if (max < 0) return null;
        return min == max ? min + "%" : Lang.t("мин ") + min + "%";
    }
}
