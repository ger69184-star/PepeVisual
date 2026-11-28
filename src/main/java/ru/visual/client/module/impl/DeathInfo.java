package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;

import java.util.Locale;

/** Координаты смерти: сообщение в чат (видно только тебе) и метка "Смерть" на экране. */
public class DeathInfo extends Module implements Config.DefaultOn {
    private final BoolSetting chat = add(new BoolSetting("Сообщение в чат", "Написать координаты и измерение смерти в чат (видно только тебе).", true));
    private final BoolSetting waypoint = add(new BoolSetting("Метка", "Поставить метку \"Смерть\" (нужен включённый Waypoints).", true));
    private final BoolSetting killer = add(new BoolSetting("Кто убил", "Добавить в сообщение, кто или что тебя убило.", true));

    private boolean wasDead;

    public DeathInfo() {
        super("Death Info", "После смерти показывает координаты, измерение и убийцу, ставит метку к месту смерти.", Category.UTILITY);
    }

    @Override
    public boolean tickOutsideWorld() {
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            wasDead = false;
            return;
        }
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) onDeath(mc);
        wasDead = dead;
    }

    private void onDeath(Minecraft mc) {
        Vec3 p = mc.player.position();
        String dim = switch (mc.level.dimension().identifier().getPath()) {
            case "overworld" -> Lang.t("Верхний мир");
            case "the_nether" -> Lang.t("Незер");
            case "the_end" -> Lang.t("Энд");
            default -> mc.level.dimension().identifier().toString();
        };
        if (chat.get()) {
            String msg = String.format(Locale.ROOT, Lang.t("§c[PV] Ты умер: §f%d %d %d §7(%s)"), (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), dim);
            if (killer.get()) {
                var src = mc.player.getLastDamageSource();
                if (src != null) msg += Lang.t("§7, причина: §f") + src.getLocalizedDeathMessage(mc.player).getString();
            }
            mc.gui.getChat().addMessage(Component.literal(msg));
        }
        if (waypoint.get()) Waypoints.put(mc, Lang.t("Смерть"), p, 0xFFFF5555);
    }
}
