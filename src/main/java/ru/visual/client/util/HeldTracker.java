package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Хотбар" других игроков. Сервер не присылает клиенту чужой инвентарь - только то, что в руках и надето.
 * Поэтому запоминаем всё, что игрок брал в руку: так за пару переключений слотов видно почти весь его хотбар
 * (мечи, жемчуг, зелья, тотемы, блоки).
 */
public final class HeldTracker {
    public static final int SLOTS = 9;

    public static final class Seen {
        public ItemStack stack;
        public long time;

        Seen(ItemStack stack, long time) {
            this.stack = stack;
            this.time = time;
        }
    }

    private static final Map<UUID, List<Seen>> SEEN = new HashMap<>();
    private static ClientLevel level;
    private static int tick;

    private HeldTracker() {}

    public static void tick(Minecraft mc) {
        if (mc.level != level) {
            level = mc.level;
            SEEN.clear();
        }
        if (mc.level == null || mc.player == null) return;
        if (++tick % 2 != 0) return;
        long now = System.currentTimeMillis();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isRemoved()) continue;
            remember(p.getUUID(), p.getMainHandItem(), now);
        }
        // забываем тех, кого давно не видно (вышли/далеко) - не копим память
        if (tick % 1200 == 0) {
            Iterator<Map.Entry<UUID, List<Seen>>> it = SEEN.entrySet().iterator();
            while (it.hasNext()) {
                List<Seen> l = it.next().getValue();
                if (l.isEmpty() || now - l.get(0).time > 10 * 60_000) it.remove();
            }
        }
    }

    private static void remember(UUID id, ItemStack st, long now) {
        if (st.isEmpty()) return;
        List<Seen> list = SEEN.computeIfAbsent(id, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            Seen s = list.get(i);
            if (ItemStack.isSameItem(s.stack, st)) {
                // обновили (прочность/количество) и подняли наверх
                s.stack = st.copy();
                s.time = now;
                if (i != 0) {
                    list.remove(i);
                    list.add(0, s);
                }
                return;
            }
        }
        list.add(0, new Seen(st.copy(), now));
        while (list.size() > SLOTS) list.remove(list.size() - 1);
    }

    /** Предметы, которые игрок держал в руке, от последнего к старому (до 9). */
    public static List<Seen> seen(Player p) {
        List<Seen> l = SEEN.get(p.getUUID());
        return l == null ? List.of() : l;
    }
}
