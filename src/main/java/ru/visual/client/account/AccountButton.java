package ru.visual.client.account;

import ru.visual.client.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AccountButton {
    private AccountButton() {}

    public static Button create(Screen parent) {
        Button b = Button.builder(Component.literal(Lang.t("Аккаунты: ") + AccountManager.currentName()),
                        btn -> Minecraft.getInstance().setScreen(new AccountScreen(parent)))
                .bounds(6, 6, 130, 20)
                .build();
        b.setTooltip(Tooltip.create(Component.literal(Lang.t("Меню оффлайн-аккаунтов: сменить ник, добавить или удалить аккаунт."))));
        return b;
    }
}
