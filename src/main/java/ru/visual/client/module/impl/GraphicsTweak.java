package ru.visual.client.module.impl;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Отдельная кнопка оптимизации в меню (раздел Optimization). Включил - настройка графики временно меняется,
 * выключил - возвращается как была. В options.txt урезанные значения не сохраняются.
 * Если игрок сам поменял эту настройку в настройках игры, кнопка больше её не трогает (до перевключения).
 */
public class GraphicsTweak<T> extends Module {
    private final Function<Options, OptionInstance<T>> option;
    private final Supplier<T> target;
    private final boolean defaultOn;
    /** Дополнительное условие (например, "окно не в фокусе"). */
    private Supplier<Boolean> condition = () -> true;
    /** Пресет "Баланс": включается вместе с "Максимум FPS" или нет. */
    public final boolean balanced;

    private boolean active, userOverride;
    private T original, applied;
    private int timer;

    public GraphicsTweak(String name, String description, boolean defaultOn, boolean balanced,
                         Function<Options, OptionInstance<T>> option, Supplier<T> target) {
        super(name, description, Category.OPTIMIZATION);
        this.option = option;
        this.target = target;
        this.defaultOn = defaultOn;
        this.balanced = balanced;
    }

    public GraphicsTweak<T> when(Supplier<Boolean> c) {
        this.condition = c;
        return this;
    }

    @Override
    public boolean defaultEnabled() {
        return defaultOn;
    }

    @Override
    public boolean tickOutsideWorld() {
        return true;
    }

    @Override
    protected void onEnable() {
        userOverride = false;
        timer = 0;
    }

    @Override
    protected void onDisable() {
        restore();
    }

    @Override
    public void onShutdown() {
        restore(); // options.txt сохраняется один раз в ModuleManager.shutdown
    }

    /** Применить сразу (кнопки пресетов в меню). */
    public void applyNow() {
        timer = 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.options == null) return;
        if (timer++ % 10 != 0) return;
        OptionInstance<T> opt = option.apply(mc.options);
        boolean on = condition.get();
        if (on && !userOverride) {
            if (!active) {
                original = opt.get();
                active = true;
            } else if (applied != null && !opt.get().equals(applied)) {
                // игрок сам изменил настройку - уважаем его выбор
                userOverride = true;
                active = false;
                original = null;
                return;
            }
            T want = target.get();
            if (!opt.get().equals(want)) opt.set(want);
            applied = opt.get();
        } else if (active) {
            if (original != null) opt.set(original);
            active = false;
            applied = null;
        }
    }

    private void restore() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || !active) return;
        if (original != null) option.apply(mc.options).set(original);
        active = false;
        applied = null;
    }

    @Override
    public String info() {
        return userOverride ? "вручную" : null;
    }

    // ---------------------------------------------------------------- набор кнопок

    public static List<Module> createAll() {
        List<Module> out = new ArrayList<>();
        out.add(new GraphicsTweak<>("No Clouds", "Без облаков - один из самых заметных приростов FPS.", true, true,
                Options::cloudStatus, () -> CloudStatus.OFF));

        ModeSetting level = new ModeSetting("Уровень", "Уменьшено - часть частиц, Минимум - почти без частиц.", "Минимум", "Уменьшено", "Минимум");
        GraphicsTweak<ParticleStatus> parts = new GraphicsTweak<>("Less Particles", "Меньше частиц: взрывы, дождь, зелья и блоки не роняют FPS.", true, true,
                Options::particles, () -> level.is("Уменьшено") ? ParticleStatus.DECREASED : ParticleStatus.MINIMAL);
        parts.add(level);
        out.add(parts);

        out.add(new GraphicsTweak<>("No Entity Shadows", "Без круглых теней под мобами и игроками.", true, true,
                Options::entityShadows, () -> false));
        out.add(new GraphicsTweak<>("No Biome Blend", "Без плавного перехода цветов травы и воды между биомами - чанки грузятся быстрее.", true, true,
                Options::biomeBlendRadius, () -> 0));

        NumSetting scale = new NumSetting("Дальность", "1.0 - обычная дальность прорисовки мобов, 0.5 - вдвое меньше.", 0.75, 0.5, 1.0, 0.25);
        GraphicsTweak<Double> dist = new GraphicsTweak<>("Entity Distance", "Мобы и предметы рисуются ближе - сильно помогает на фермах и спавне.", true, true,
                Options::entityDistanceScaling, scale::get);
        dist.add(scale);
        out.add(dist);

        NumSetting fps = new NumSetting("Лимит FPS", "FPS, когда окно игры свёрнуто или не в фокусе.", 20, 10, 60, 10);
        GraphicsTweak<Integer> bg = new GraphicsTweak<>("Background FPS", "Когда сворачиваешь игру, FPS ограничивается - не греет видеокарту и не шумит.", true, true,
                Options::framerateLimit, fps::asInt);
        bg.add(fps);
        bg.when(() -> !Minecraft.getInstance().isWindowActive());
        out.add(bg);

        out.add(new GraphicsTweak<>("Fast Leaves", "Листва без прозрачности (быстрые листья) - большой прирост в лесах.", false, false,
                Options::cutoutLeaves, () -> false));
        out.add(new GraphicsTweak<>("No Smooth Light", "Без плавного освещения: картинка чуть угловатей, но чанки строятся быстрее.", false, false,
                Options::ambientOcclusion, () -> false));
        out.add(new GraphicsTweak<>("Less Weather", "Дождь и снег рисуются только рядом с тобой.", false, true,
                Options::weatherRadius, () -> 3));
        out.add(new GraphicsTweak<>("No Chunk Fade", "Чанки появляются сразу, без плавного проявления.", false, false,
                Options::chunkSectionFadeInTime, () -> 0.0));
        out.add(new GraphicsTweak<>("No Menu Blur", "Без размытия фона в меню и инвентаре (на слабых видеокартах размытие тормозит).", false, false,
                Options::menuBackgroundBlurriness, () -> 0));
        return out;
    }
}
