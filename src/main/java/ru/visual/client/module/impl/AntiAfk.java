package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;

import java.util.Random;

/**
 * Анти-АФК: если ты ничего не нажимаешь дольше заданного времени, иногда подпрыгивает, крутит головой
 * и машет рукой, чтобы сервер не кикнул за бездействие (удобно на АФК-фермах и спавнерах).
 * С места не сходит. Как только нажмёшь любую клавишу - отключается до следующего простоя.
 */
public class AntiAfk extends Module {
    private final NumSetting idle = add(new NumSetting("Простой (сек)", "Через сколько секунд без нажатий начинать двигаться.", 30, 10, 300, 5));
    private final NumSetting interval = add(new NumSetting("Интервал (сек)", "Как часто делать действие, пока ты АФК.", 15, 3, 120, 1));
    private final BoolSetting jump = add(new BoolSetting("Прыжок", "Подпрыгнуть на месте.", true));
    private final BoolSetting look = add(new BoolSetting("Поворот головы", "Немного повернуть камеру и вернуть обратно.", true));
    private final BoolSetting swing = add(new BoolSetting("Взмах рукой", "Махнуть рукой (не ломает и не бьёт).", true));
    private final BoolSetting sneak = add(new BoolSetting("Присесть", "Коротко присесть.", false));

    private final Random random = new Random();
    private long lastInput = System.currentTimeMillis(), lastAction;
    private float baseYaw, lastYaw, lastPitch;
    private int lookBack, sneakTicks;

    public AntiAfk() {
        super("AntiAFK", "Не даёт серверу кикнуть за АФК: после простоя сам иногда прыгает, крутит головой и машет рукой, не сходя с места.", Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        lastInput = System.currentTimeMillis();
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = System.currentTimeMillis();
        var o = mc.options;
        boolean input = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()
                || o.keyAttack.isDown() || o.keyUse.isDown() || (o.keyJump.isDown() && sneakTicks == 0 && !p.onGround())
                || (lookBack == 0 && (Math.abs(p.getYRot() - lastYaw) > 0.01f || Math.abs(p.getXRot() - lastPitch) > 0.01f))
                || mc.screen != null;
        if (input) {
            lastInput = now;
            lookBack = 0;
        }
        if (sneakTicks > 0 && --sneakTicks == 0) o.keyShift.setDown(false);
        if (lookBack > 0 && --lookBack == 0) p.setYRot(baseYaw);
        lastYaw = p.getYRot();
        lastPitch = p.getXRot();

        if (now - lastInput < idle.get() * 1000 || now - lastAction < interval.get() * 1000) return;
        lastAction = now;
        if (jump.get() && p.onGround()) p.jumpFromGround();
        if (swing.get()) p.swing(InteractionHand.MAIN_HAND);
        if (look.get()) {
            baseYaw = p.getYRot();
            p.setYRot(baseYaw + (random.nextBoolean() ? 1 : -1) * (10 + random.nextInt(25)));
            lookBack = 10;
            lastYaw = p.getYRot();
        }
        if (sneak.get()) {
            o.keyShift.setDown(true);
            sneakTicks = 6;
        }
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (sneakTicks > 0 && mc.options != null) mc.options.keyShift.setDown(false);
        sneakTicks = 0;
        lookBack = 0;
    }

    @Override
    public String info() {
        long idleMs = System.currentTimeMillis() - lastInput;
        return idleMs >= idle.get() * 1000 ? Lang.t("АФК") : null;
    }
}
