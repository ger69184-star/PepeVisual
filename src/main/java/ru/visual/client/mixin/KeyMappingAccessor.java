package ru.visual.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Счётчик нажатий клавиши: помощник "нажимает" ЛКМ/ПКМ так же, как игрок, и ванильный код сам делает удар/клик. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int pepevisual$getClickCount();

    @Accessor("clickCount")
    void pepevisual$setClickCount(int count);
}
