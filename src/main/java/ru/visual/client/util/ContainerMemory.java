package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Запоминает содержимое сундуков/бочек/шалкеров, которые ты открывал (сервер не присылает клиенту
 * содержимое закрытых хранилищ). Нужна для превью шалкеров и поиска предметов по сундукам.
 */
public final class ContainerMemory {
    public record Entry(List<ItemStack> items, long time) {
        public int count(ItemStack like) {
            int n = 0;
            for (ItemStack s : items) if (!s.isEmpty() && s.is(like.getItem())) n += s.getCount();
            return n;
        }

        public int filled() {
            int n = 0;
            for (ItemStack s : items) if (!s.isEmpty()) n++;
            return n;
        }
    }

    private static final Map<BlockPos, Entry> MAP = new HashMap<>();
    private static ResourceKey<Level> dim;
    private static BlockPos lastUsed;
    private static long lastUsedTime;
    private static int lastContainerId = -1;
    private static BlockPos openPos, openPos2;

    private ContainerMemory() {}

    /** Игрок кликнул ПКМ по блоку (UseBlockCallback). */
    public static void onUseBlock(BlockPos pos) {
        lastUsed = pos.immutable();
        lastUsedTime = System.currentTimeMillis();
    }

    /** Позиция хранилища, открытого сейчас (или null). */
    public static BlockPos openPos() {
        return lastContainerId >= 0 ? openPos : null;
    }

    public static boolean isStorageMenu(AbstractContainerMenu m) {
        return m instanceof ChestMenu || m instanceof ShulkerBoxMenu;
    }

    /** Сколько слотов в открытом меню принадлежат самому хранилищу (остальные - инвентарь игрока). */
    public static int containerSlots(AbstractContainerMenu m) {
        return Math.max(0, m.slots.size() - 36);
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            MAP.clear();
            dim = null;
            lastContainerId = -1;
            return;
        }
        if (dim != mc.level.dimension()) {
            MAP.clear();
            dim = mc.level.dimension();
        }
        AbstractContainerMenu m = mc.player.containerMenu;
        if (!(mc.screen instanceof AbstractContainerScreen<?>) || !isStorageMenu(m)) {
            lastContainerId = -1;
            return;
        }
        if (m.containerId != lastContainerId) {
            lastContainerId = m.containerId;
            openPos = null;
            openPos2 = null;
            if (lastUsed != null && System.currentTimeMillis() - lastUsedTime < 2000) {
                BlockState st = mc.level.getBlockState(lastUsed);
                BlockEntity be = mc.level.getBlockEntity(lastUsed);
                if (be != null && !(st.getBlock() instanceof EnderChestBlock)) {
                    openPos = lastUsed;
                    if (be instanceof ChestBlockEntity && st.hasProperty(ChestBlock.TYPE) && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        Direction d = ChestBlock.getConnectedDirection(st);
                        openPos2 = lastUsed.relative(d);
                    }
                }
            }
        }
        if (openPos == null) return;
        int n = containerSlots(m);
        List<ItemStack> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) items.add(m.getSlot(i).getItem().copy());
        Entry e = new Entry(Collections.unmodifiableList(items), System.currentTimeMillis());
        MAP.put(openPos, e);
        if (openPos2 != null) MAP.put(openPos2, e);
    }

    public static Entry get(BlockPos pos) {
        return MAP.get(pos);
    }

    public static Map<BlockPos, Entry> all() {
        return MAP;
    }

    /** Блок сломали - забываем. */
    public static void forget(BlockPos pos) {
        MAP.remove(pos);
    }
}
