package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Предупреждение, когда броня или предмет в руке вот-вот сломается. */
public class DurabilityWarning extends HudModule implements Config.DefaultOn {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final String[] NAMES = {"Шлем", "Нагрудник", "Поножи", "Ботинки", "Рука", "Вторая рука"};

    private final NumSetting threshold = add(new NumSetting("Порог (%)", "Предупреждать, когда прочность ниже этого процента.", 10, 1, 50, 1));
    private final BoolSetting sound = add(new BoolSetting("Звук", "Короткий звуковой сигнал при первом предупреждении.", true));
    private final BoolSetting hands = add(new BoolSetting("Предметы в руках", "Следить и за инструментами/оружием в руках, не только за бронёй.", true));

    private final Set<String> warned = new HashSet<>();
    private final List<String> lines = new ArrayList<>();

    public DurabilityWarning() {
        super("Durability Warning", "Мигающее предупреждение и звук, когда броня или инструмент почти сломались.");
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        lines.clear();
        Set<String> now = new HashSet<>();
        for (int i = 0; i < SLOTS.length; i++) {
            if (i >= 4 && !hands.get()) break;
            ItemStack st = mc.player.getItemBySlot(SLOTS[i]);
            if (st.isEmpty() || !st.isDamageableItem() || st.getMaxDamage() <= 0) continue;
            int left = st.getMaxDamage() - st.getDamageValue();
            double pct = 100.0 * left / st.getMaxDamage();
            if (pct > threshold.get()) continue;
            String key = SLOTS[i].getName() + ":" + st.getItem();
            now.add(key);
            lines.add(NAMES[i] + ": " + st.getHoverName().getString() + " - " + left + " (" + (int) Math.ceil(pct) + "%)");
            if (!warned.contains(key) && sound.get()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.6f));
            }
        }
        warned.clear();
        warned.addAll(now);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (lines.isEmpty()) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        int y = h / 2 - 50 - lines.size() * 11 + oy();
        String title = "! Прочность на исходе !";
        Draw.textCentered(g, title, w / 2 + ox(), y, blink ? 0xFFFF4040 : 0xFFFFB0B0);
        y += 11;
        for (String s : lines) {
            Draw.textCentered(g, s, w / 2 + ox(), y, 0xFFFFD0D0);
            y += 11;
        }
    }
}
