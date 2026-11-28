package ru.visual.client.account;

import ru.visual.client.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Меню оффлайн-аккаунтов. */
public class AccountScreen extends Screen {
    private final Screen parent;
    private EditBox input;
    private String status = "";
    private int scroll;

    public AccountScreen(Screen parent) {
        super(Component.literal("Оффлайн-аккаунты"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        input = new EditBox(this.font, cx - 100, 40, 130, 20, Component.literal(Lang.t("Ник")));
        input.setMaxLength(16);
        input.setHint(Component.literal(Lang.t("Новый ник (3-16: A-Z, 0-9, _)")));
        input.setTooltip(Tooltip.create(Component.literal(Lang.t("Введи ник: латиница, цифры и _, длина 3-16 символов."))));
        this.addRenderableWidget(input);

        Button add = Button.builder(Component.literal(Lang.t("Добавить")), b -> {
            String n = input.getValue().trim();
            if (AccountManager.add(n)) {
                status = Lang.t("Добавлен: ") + n;
                input.setValue("");
                this.rebuildWidgets();
            } else {
                status = Lang.t("Неверный ник или он уже есть в списке");
            }
        }).bounds(cx + 36, 40, 64, 20).build();
        add.setTooltip(Tooltip.create(Component.literal(Lang.t("Сохранить ник в список аккаунтов."))));
        this.addRenderableWidget(add);

        Button rnd = Button.builder(Component.literal(Lang.t("Случайный")), b -> {
            input.setValue(AccountManager.random());
        }).bounds(cx - 100, 64, 95, 20).build();
        rnd.setTooltip(Tooltip.create(Component.literal(Lang.t("Сгенерировать случайный ник."))));
        this.addRenderableWidget(rnd);

        Button useNow = Button.builder(Component.literal(Lang.t("Войти этим ником")), b -> {
            String n = input.getValue().trim();
            if (AccountManager.login(n)) {
                AccountManager.add(n);
                status = Lang.t("Теперь ты: ") + n;
                this.rebuildWidgets();
            } else {
                status = Lang.t("Неверный ник");
            }
        }).bounds(cx - 2, 64, 102, 20).build();
        useNow.setTooltip(Tooltip.create(Component.literal(Lang.t("Сразу сменить ник на введённый (и сохранить в список)."))));
        this.addRenderableWidget(useNow);

        // Список сохранённых аккаунтов
        List<String> names = new ArrayList<>(AccountManager.names());
        int maxRows = Math.max(1, (this.height - 150) / 24);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, names.size() - maxRows)));
        int y = 100;
        for (int i = scroll; i < Math.min(names.size(), scroll + maxRows); i++) {
            String n = names.get(i);
            boolean active = n.equals(AccountManager.currentName());
            Button use = Button.builder(Component.literal((active ? "> " : "") + n), b -> {
                if (AccountManager.login(n)) status = Lang.t("Теперь ты: ") + n;
                else status = Lang.t("Не удалось сменить аккаунт");
                this.rebuildWidgets();
            }).bounds(cx - 100, y, 170, 20).build();
            use.setTooltip(Tooltip.create(Component.literal(Lang.t("Играть под ником ") + n + ".")));
            this.addRenderableWidget(use);

            Button del = Button.builder(Component.literal("X"), b -> {
                AccountManager.remove(n);
                this.rebuildWidgets();
            }).bounds(cx + 74, y, 26, 20).build();
            del.setTooltip(Tooltip.create(Component.literal(Lang.t("Удалить ") + n + Lang.t(" из списка."))));
            this.addRenderableWidget(del);
            y += 24;
        }

        Button back = Button.builder(Component.literal(Lang.t("Назад")), b -> this.onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build();
        back.setTooltip(Tooltip.create(Component.literal(Lang.t("Вернуться на предыдущий экран."))));
        this.addRenderableWidget(back);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, Lang.t("Оффлайн-аккаунты"), this.width / 2 - this.font.width(Lang.t("Оффлайн-аккаунты")) / 2, 14, 0xFFFFFFFF);
        g.drawString(this.font, Lang.t("Сейчас: ") + AccountManager.currentName(), this.width / 2 - 100, 28, 0xFF8A63FF);
        if (!status.isEmpty()) {
            g.drawString(this.font, status, this.width / 2 - this.font.width(status) / 2, 88, 0xFFFFE070);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy));
        this.rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
