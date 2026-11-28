package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;

/** Сам берёт в руку лучший инструмент из хотбара, когда ты начинаешь ломать блок. */
public class AutoTool extends Module {
    private final ru.visual.client.util.Human.Tune tune = humanTune(false, false);
    private final BoolSetting restore = add(new BoolSetting("Вернуть слот", "Когда перестаёшь копать - вернуть предмет, который был в руке.", true));
    private final NumSetting saveTool = add(new NumSetting("Беречь инструмент", "Не брать инструмент, если прочности осталось меньше (0 - не беречь).", 10, 0, 100, 1));
    private final BoolSetting onlyFaster = add(new BoolSetting("Только если быстрее", "Не менять предмет, если текущий ломает не хуже.", true));

    private int previous = -1;
    private int idle, idleLimit = 8;
    /** Слот, на который собираемся переключиться, и сколько ещё "думаем" (человек меняет предмет не в тот же миг). */
    private int pendingSlot = -1, pendingWait;

    public AutoTool() {
        super("AutoTool", "Когда ломаешь блок, сам берёт лучший инструмент из хотбара и потом возвращает прежний предмет.", Category.HELPERS);
    }

    @Override
    protected void onDisable() {
        previous = -1;
    }

    private boolean usable(ItemStack s) {
        if (!s.isDamageableItem() || saveTool.asInt() <= 0) return true;
        return s.getMaxDamage() - s.getDamageValue() > saveTool.asInt();
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || p.getAbilities().instabuild) return;
        Inventory inv = p.getInventory();
        boolean mining = mc.screen == null && mc.options.keyAttack.isDown()
                && mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            pendingSlot = -1;
            if (previous >= 0 && ++idle > idleLimit) {
                if (restore.get() && previous != inv.getSelectedSlot()) inv.setSelectedSlot(previous);
                previous = -1;
            }
            return;
        }
        if (idle > 0) idleLimit = tune.advanced() ? tune.react(6, 16) : 4;
        idle = 0;
        BlockState st = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
        if (st.isAir() || st.getDestroySpeed(mc.level, ((BlockHitResult) mc.hitResult).getBlockPos()) < 0) return;
        int cur = inv.getSelectedSlot();
        ItemStack held = inv.getItem(cur);
        float curSpeed = usable(held) ? held.getDestroySpeed(st) : 0f;
        boolean curCorrect = !st.requiresCorrectToolForDrops() || held.isCorrectToolForDrops(st);
        int best = cur;
        float bestSpeed = curSpeed;
        boolean bestCorrect = curCorrect;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !usable(s)) continue;
            float sp = s.getDestroySpeed(st);
            boolean correct = !st.requiresCorrectToolForDrops() || s.isCorrectToolForDrops(st);
            // главное - чтобы блок выпал (кирка для камня), потом - скорость
            if ((correct && !bestCorrect) || (correct == bestCorrect && sp > bestSpeed + 0.01f)) {
                best = i;
                bestSpeed = sp;
                bestCorrect = correct;
            }
        }
        if (best == cur) {
            pendingSlot = -1;
            return;
        }
        if (onlyFaster.get() && bestSpeed <= curSpeed && bestCorrect == curCorrect) return;
        if (tune.advanced()) { // продвинутый: меняем предмет не в тот же миг, как человек
            if (pendingSlot != best) {
                pendingSlot = best;
                pendingWait = tune.react(1, 4);
                return;
            }
            if (pendingWait-- > 0) return;
        }
        pendingSlot = -1;
        if (previous < 0) previous = cur;
        inv.setSelectedSlot(best);
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        previous = -1;
        pendingSlot = -1;
        idle = 0;
    }
}
