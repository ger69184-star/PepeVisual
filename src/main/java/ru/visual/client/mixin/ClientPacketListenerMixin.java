package ru.visual.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.impl.ServerInfo;
import ru.visual.client.module.impl.TotemPops;

/** TAIL - пакеты уже в главном потоке (на сетевом потоке метод выходит исключением раньше). */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"), require = 0)
    private void pepevisual$tps(ClientboundSetTimePacket packet, CallbackInfo ci) {
        ServerInfo.onTimePacket();
    }

    /** Событие 35 - сработал тотем бессмертия. */
    @Inject(method = "handleEntityEvent", at = @At("TAIL"), require = 0)
    private void pepevisual$totem(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != 35) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = packet.getEntity(mc.level);
        if (e != null) TotemPops.onPop(e);
    }
}
