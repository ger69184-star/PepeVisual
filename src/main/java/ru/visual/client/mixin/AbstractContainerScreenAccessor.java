package ru.visual.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к положению окна контейнера и слоту под мышкой (кнопки сортировки, превью шалкеров). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int pepevisual$leftPos();

    @Accessor("topPos")
    int pepevisual$topPos();

    @Accessor("imageWidth")
    int pepevisual$imageWidth();

    @Accessor("imageHeight")
    int pepevisual$imageHeight();

    @Accessor("hoveredSlot")
    Slot pepevisual$hoveredSlot();
}
