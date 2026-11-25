package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class PlayerUtil {
    private PlayerUtil() {}

    /** Пинг до сервера в мс (0 в одиночной игре). */
    public static int ping(Minecraft mc) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.player == null) return 0;
        PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }
}
