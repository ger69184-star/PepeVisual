package ru.visual.client.module.impl;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.ParticleStatus;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;

/**
 * Собственная оптимизация: временно переопределяет настройки графики и возвращает их при выключении.
 * Основной прирост даёт связка вшитых модов (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling...).
 */
public class Optimization extends Module implements Config.DefaultOn {
    private static final class Managed<T> {
        boolean active;
        T original;
    }

    private final BoolSetting clouds = add(new BoolSetting("Без облаков", "Отключает облака - заметно экономит FPS.", true));
    private final BoolSetting particles = add(new BoolSetting("Минимум частиц", "Снижает количество частиц до минимума.", true));
    private final BoolSetting shadows = add(new BoolSetting("Без теней существ", "Убирает круглые тени под существами.", true));
    private final BoolSetting blend = add(new BoolSetting("Без сглаживания биомов", "Радиус смешивания цветов биомов = 0, быстрее прогрузка чанков.", true));
    private final BoolSetting ao = add(new BoolSetting("Без плавного освещения", "Отключает smooth lighting (картинка станет угловатей, но быстрее).", false));
    private final BoolSetting entities = add(new BoolSetting("Дальность существ", "Уменьшает дальность прорисовки существ (см. ползунок ниже).", true));
    private final NumSetting entityScale = add(new NumSetting("Масштаб дальности существ", "0.5 = самая малая дальность прорисовки существ, 1.0 = обычная.", 0.75, 0.5, 1.0, 0.05));
    private final BoolSetting background = add(new BoolSetting("FPS в фоне", "Ограничивает FPS, когда окно игры не в фокусе.", true));
    private final NumSetting backgroundFps = add(new NumSetting("FPS в фоне (лимит)", "Лимит FPS при свёрнутом окне.", 15, 10, 60, 5));

    private final Managed<CloudStatus> mCloud = new Managed<>();
    private final Managed<ParticleStatus> mParticles = new Managed<>();
    private final Managed<Boolean> mShadows = new Managed<>();
    private final Managed<Integer> mBlend = new Managed<>();
    private final Managed<Boolean> mAo = new Managed<>();
    private final Managed<Double> mEntities = new Managed<>();
    private final Managed<Integer> mFps = new Managed<>();

    private int timer;

    public Optimization() {
        super("Optimization", "Повышает FPS: отключает тяжёлые настройки графики и ограничивает FPS в фоне.", Category.OPTIMIZATION);
    }

    @Override
    protected void onEnable() {
        timer = 0;
    }

    @Override
    protected void onDisable() {
        restoreAll();
    }

    @Override
    public void onShutdown() {
        restoreAll();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.options == null) return;
        // Фоновый лимит FPS проверяем каждый тик, остальное - раз в секунду
        manage(mFps, mc.options.framerateLimit(), background.get() && !mc.isWindowActive(), backgroundFps.asInt());
        if (timer++ % 20 != 0) return;
        apply(mc);
    }

    private void apply(Minecraft mc) {
        var o = mc.options;
        manage(mCloud, o.cloudStatus(), clouds.get(), CloudStatus.OFF);
        manage(mParticles, o.particles(), particles.get(), ParticleStatus.MINIMAL);
        manage(mShadows, o.entityShadows(), shadows.get(), false);
        manage(mBlend, o.biomeBlendRadius(), blend.get(), 0);
        manage(mAo, o.ambientOcclusion(), ao.get(), false);
        manage(mEntities, o.entityDistanceScaling(), entities.get(), entityScale.get());
    }

    private void restoreAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        var o = mc.options;
        manage(mCloud, o.cloudStatus(), false, null);
        manage(mParticles, o.particles(), false, null);
        manage(mShadows, o.entityShadows(), false, null);
        manage(mBlend, o.biomeBlendRadius(), false, null);
        manage(mAo, o.ambientOcclusion(), false, null);
        manage(mEntities, o.entityDistanceScaling(), false, null);
        manage(mFps, o.framerateLimit(), false, null);
    }

    private static <T> void manage(Managed<T> m, OptionInstance<T> opt, boolean on, T target) {
        if (on) {
            if (!m.active) {
                m.original = opt.get();
                m.active = true;
            }
            if (!opt.get().equals(target)) opt.set(target);
        } else if (m.active) {
            if (m.original != null) opt.set(m.original);
            m.active = false;
        }
    }
}
