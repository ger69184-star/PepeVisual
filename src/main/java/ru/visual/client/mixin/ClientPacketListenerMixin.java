package ru.visual.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.impl.ServerInfo;

/** Server Info: TPS по интервалу пакетов времени (TAIL - уже в главном потоке). */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"), require = 0)
    private void pepevisual$tps(ClientboundSetTimePacket packet, CallbackInfo ci) {
        ServerInfo.onTimePacket();
    }
}
