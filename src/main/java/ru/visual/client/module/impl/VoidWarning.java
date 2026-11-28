package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;

/** Предупреждение о падении в вакуум: красные края, надпись и звук, когда Y ниже порога. */
public class VoidWarning extends Module {
    private final NumSetting threshold = add(new NumSetting("Порог Y", "При каком Y и ниже предупреждать о вакууме.", -64, -256, 0, 1));
    private final BoolSetting vignette = add(new BoolSetting("Красные края", "Пульсирующая красная рамка по краям экрана.", true));
    private final BoolSetting text = add(new BoolSetting("Надпись", "Надпись «Вакуум» по центру экрана.", true));
    private final BoolSetting sound = add(new BoolSetting("Звук", "Короткий звук, когда опускаешься ниже порога.", true));

    private boolean warn;

    public VoidWarning() {
        super("VoidWarning", "Предупреждение о вакууме: когда Y падает ниже порога - красные края, надпись и звук.", Category.HUD);
    }

    private boolean inVoid(Minecraft mc) {
        return mc.player != null && mc.player.isAlive() && !mc.player.isCreative() && !mc.player.isSpectator()
                && mc.player.getY() < threshold.get();
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean now = inVoid(mc);
        if (now && !warn && sound.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.9f, 0.5f);
        }
        warn = now;
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (!warn || mc.options.hideGui) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0);
        if (vignette.get()) {
            int a = (int) (0x50 + 0x50 * pulse);
            int edge = Math.max(18, Math.min(w, h) / 8);
            int c = (a << 24) | 0xFF0000;
            g.fillGradient(0, 0, w, edge, c, 0x00FF0000);
            g.fillGradient(0, h - edge, w, h, 0x00FF0000, c);
            int steps = 12;
            for (int i = 0; i < steps; i++) {
                int ca = (int) (a * (1.0 - i / (double) steps));
                int x1 = edge * i / steps, x2 = edge * (i + 1) / steps;
                g.fill(x1, 0, x2, h, (ca << 24) | 0xFF0000);
                g.fill(w - x2, 0, w - x1, h, (ca << 24) | 0xFF0000);
            }
        }
        if (text.get()) {
            String s = Lang.t("ВАКУУМ");
            int col = Draw.withAlpha(0xFF5050, (int) (180 + 75 * pulse));
            Draw.pill(g, s, w / 2, h / 2 - 30, col);
        }
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        warn = false;
    }
}
