package ru.visual.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.account.AccountButton;

/** Кнопка "Аккаунты" на экране списка серверов - удобно сменить ник перед входом. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"), require = 0)
    private void pepevisual$addAccountsButton(CallbackInfo ci) {
        this.addRenderableWidget(AccountButton.create((Screen) (Object) this));
    }
}
