package ru.visual.client.mixin;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.visual.client.module.impl.ChatEsp;
import ru.visual.client.module.impl.ChatTimestamps;

/** ChatTimestamps: время перед каждым новым сообщением чата. ChatESP: подсветка важных сообщений. */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component pepevisual$timestamp(Component message) {
        return ChatTimestamps.decorate(ChatEsp.decorate(message));
    }
}
