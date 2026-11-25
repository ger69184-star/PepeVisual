package ru.visual.client.module.impl;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.mixin.AbstractContainerScreenAccessor;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.ContainerMemory;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.R3D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сортировка по сундукам.
 * <ul>
 *     <li>Кнопки в окне сундука/бочки/шалкера: отсортировать, сложить похожие (как "быстрая раскладка"), забрать всё.</li>
 *     <li>Авто-раскладка: открываешь сундук - туда сами уходят предметы, которые в нём уже лежат.
 *     Обойди склад, открывая сундуки, и всё разложится по своим местам.</li>
 *     <li>Поиск: сундуки, где (по памяти) лежит предмет из твоей руки, подсвечиваются в мире.</li>
 * </ul>
 */
public class ChestSorter extends Module {
    private static ChestSorter instance;

    private final ModeSetting order = add(new ModeSetting("Порядок", "По типу - как в творческом меню (блоки рядом с блоками). По имени - по алфавиту. По количеству - чего больше, то первее.",
            "По типу", "По типу", "По имени", "По количеству"));
    private final BoolSetting buttons = add(new BoolSetting("Кнопки в окне", "Показывать кнопки сортировки рядом с окном сундука и инвентаря.", true));
    private final BoolSetting autoSort = add(new BoolSetting("Авто-сортировка", "Сортировать сундук сразу при открытии.", false));
    private final BoolSetting autoStack = add(new BoolSetting("Авто-раскладка", "При открытии сундука перекладывать в него предметы из инвентаря, которые в нём уже есть. Так склад раскладывается сам.", false));
    private final BoolSetting keepHotbar = add(new BoolSetting("Не трогать хотбар", "Раскладка не забирает предметы из хотбара.", true));
    private final NumSetting speed = add(new NumSetting("Кликов за тик", "Скорость перекладывания. Меньше - надёжнее на серверах с задержкой.", 4, 1, 20, 1));
    private final BoolSetting search = add(new BoolSetting("Поиск по сундукам", "Подсвечивать сундуки, где по памяти лежит предмет из твоей руки (сундук нужно хоть раз открыть).", true));
    private final NumSetting searchRange = add(new NumSetting("Дальность поиска", "Как далеко подсвечивать сундуки с нужным предметом (блоки).", 48, 8, 128, 4));

    private record Click(int containerId, int slot, int button, ClickType type) {}

    private final Deque<Click> queue = new ArrayDeque<>();
    private int lastAutoContainer = -1;
    private int moved;
    private String status = "";
    private long statusTime;

    public ChestSorter() {
        super("ChestSorter", "Сортировка по сундукам: кнопки сортировки, авто-раскладка предметов по сундукам и поиск, где что лежит.", Category.HELPERS);
        instance = this;
    }

    @Override
    protected void onDisable() {
        queue.clear();
    }

    @Override
    public String info() {
        int n = ContainerMemory.all().size();
        return n > 0 ? n + " в памяти" : null;
    }

    // ---------- кнопки ----------

    public static void onScreenInit(Minecraft mc, Screen screen) {
        ChestSorter m = instance;
        if (m == null || !m.isEnabled() || !m.buttons.get() || mc.player == null) return;
        if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) cs;
        AbstractContainerMenu menu = cs.getMenu();
        int x = acc.pepevisual$leftPos() + acc.pepevisual$imageWidth() + 4;
        int y = acc.pepevisual$topPos() + 4;
        List<net.minecraft.client.gui.components.AbstractWidget> list = Screens.getButtons(screen);
        if (ContainerMemory.isStorageMenu(menu)) {
            list.add(btn("Сортировать", "Сложить одинаковые стопки и упорядочить содержимое.", x, y, b -> m.sortContainer(mc)));
            list.add(btn("Сложить похожие", "Переложить сюда из инвентаря всё, что в этом хранилище уже лежит.", x, y + 22, b -> m.quickStack(mc)));
            list.add(btn("Забрать всё", "Переложить всё содержимое в инвентарь.", x, y + 44, b -> m.takeAll(mc)));
            list.add(btn("Сорт. инвентарь", "Упорядочить твой инвентарь (без хотбара).", x, y + 66, b -> m.sortPlayer(mc)));
        } else if (screen instanceof InventoryScreen && menu instanceof InventoryMenu) {
            list.add(btn("Сортировать", "Упорядочить инвентарь (без хотбара).", x, y, b -> m.sortPlayer(mc)));
        }
    }

    private static Button btn(String text, String tip, int x, int y, Button.OnPress press) {
        return Button.builder(Component.literal(text), press).bounds(x, y, 92, 20).tooltip(Tooltip.create(Component.literal(tip))).build();
    }

    // ---------- действия ----------

    private void sortContainer(Minecraft mc) {
        AbstractContainerMenu m = mc.player.containerMenu;
        if (!ContainerMemory.isStorageMenu(m)) return;
        int n = ContainerMemory.containerSlots(m);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < n; i++) slots.add(i);
        int clicks = planSort(m, slots);
        setStatus(clicks == 0 ? "Уже отсортировано" : "Сортирую...");
    }

    private void sortPlayer(Minecraft mc) {
        AbstractContainerMenu m = mc.player.containerMenu;
        List<Integer> slots = new ArrayList<>();
        if (m instanceof InventoryMenu) {
            for (int i = 9; i <= 35; i++) slots.add(i);
        } else if (ContainerMemory.isStorageMenu(m)) {
            int n = ContainerMemory.containerSlots(m);
            for (int i = n; i < n + 27; i++) slots.add(i);
        } else return;
        int clicks = planSort(m, slots);
        setStatus(clicks == 0 ? "Уже отсортировано" : "Сортирую инвентарь...");
    }

    private void quickStack(Minecraft mc) {
        AbstractContainerMenu m = mc.player.containerMenu;
        if (!ContainerMemory.isStorageMenu(m)) return;
        int n = ContainerMemory.containerSlots(m);
        Set<Item> inside = new HashSet<>();
        for (int i = 0; i < n; i++) {
            ItemStack s = m.getSlot(i).getItem();
            if (!s.isEmpty()) inside.add(s.getItem());
        }
        int end = keepHotbar.get() ? n + 27 : n + 36;
        int count = 0;
        for (int i = n; i < Math.min(end, m.slots.size()); i++) {
            ItemStack s = m.getSlot(i).getItem();
            if (!s.isEmpty() && inside.contains(s.getItem())) {
                queue.add(new Click(m.containerId, i, 0, ClickType.QUICK_MOVE));
                count++;
            }
        }
        moved += count;
        setStatus(count == 0 ? "Нечего раскладывать" : "Раскладываю: " + count + " стопок");
    }

    private void takeAll(Minecraft mc) {
        AbstractContainerMenu m = mc.player.containerMenu;
        if (!ContainerMemory.isStorageMenu(m)) return;
        int n = ContainerMemory.containerSlots(m);
        for (int i = 0; i < n; i++) {
            if (!m.getSlot(i).getItem().isEmpty()) queue.add(new Click(m.containerId, i, 0, ClickType.QUICK_MOVE));
        }
    }

    private void setStatus(String s) {
        status = s;
        statusTime = System.currentTimeMillis();
    }

    // ---------- планирование сортировки ----------

    private Comparator<ItemStack> comparator(Map<Item, Integer> totals) {
        Comparator<ItemStack> byId = Comparator.comparingInt(s -> BuiltInRegistries.ITEM.getId(s.getItem()));
        Comparator<ItemStack> byName = Comparator.comparing(s -> s.getHoverName().getString().toLowerCase());
        Comparator<ItemStack> byCount = Comparator.comparingInt(s -> -s.getCount());
        return switch (order.get()) {
            case "По имени" -> byName.thenComparing(byId).thenComparing(byCount);
            case "По количеству" -> Comparator.<ItemStack>comparingInt(s -> -totals.getOrDefault(s.getItem(), 0)).thenComparing(byId).thenComparing(byCount);
            default -> byId.thenComparing(byName).thenComparing(byCount);
        };
    }

    /** Планирует клики для сортировки выбранных слотов меню. Возвращает число кликов. */
    private int planSort(AbstractContainerMenu m, List<Integer> slots) {
        if (!m.getCarried().isEmpty()) {
            setStatus("Сначала положи предмет из курсора");
            return 0;
        }
        int k = slots.size();
        ItemStack[] cur = new ItemStack[k];
        for (int i = 0; i < k; i++) cur[i] = m.getSlot(slots.get(i)).getItem().copy();
        List<Click> plan = new ArrayList<>();

        // 1. объединяем неполные стопки одного вида
        for (int i = 0; i < k; i++) {
            if (cur[i].isEmpty() || cur[i].getCount() >= cur[i].getMaxStackSize()) continue;
            for (int j = i + 1; j < k && cur[i].getCount() < cur[i].getMaxStackSize(); j++) {
                if (cur[j].isEmpty() || !ItemStack.isSameItemSameComponents(cur[i], cur[j])) continue;
                int space = cur[i].getMaxStackSize() - cur[i].getCount();
                int mv = Math.min(space, cur[j].getCount());
                plan.add(new Click(m.containerId, slots.get(j), 0, ClickType.PICKUP));
                plan.add(new Click(m.containerId, slots.get(i), 0, ClickType.PICKUP));
                cur[i].grow(mv);
                cur[j].shrink(mv);
                if (!cur[j].isEmpty()) plan.add(new Click(m.containerId, slots.get(j), 0, ClickType.PICKUP));
                else cur[j] = ItemStack.EMPTY;
            }
        }

        // 2. желаемый порядок
        Map<Item, Integer> totals = new HashMap<>();
        List<ItemStack> sorted = new ArrayList<>();
        for (ItemStack s : cur) {
            if (s.isEmpty()) continue;
            totals.merge(s.getItem(), s.getCount(), Integer::sum);
            sorted.add(s);
        }
        sorted.sort(comparator(totals));

        // 3. выборочная сортировка обменами: клики [j, i, j]
        for (int i = 0; i < sorted.size(); i++) {
            ItemStack want = sorted.get(i);
            if (same(cur[i], want)) continue;
            int j = -1;
            for (int q = i + 1; q < k; q++) {
                if (same(cur[q], want)) {
                    j = q;
                    break;
                }
            }
            if (j < 0) continue;
            plan.add(new Click(m.containerId, slots.get(j), 0, ClickType.PICKUP));
            plan.add(new Click(m.containerId, slots.get(i), 0, ClickType.PICKUP));
            if (!cur[i].isEmpty()) plan.add(new Click(m.containerId, slots.get(j), 0, ClickType.PICKUP));
            ItemStack t = cur[i];
            cur[i] = cur[j];
            cur[j] = t;
        }
        queue.addAll(plan);
        return plan.size();
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    // ---------- тик ----------

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) {
            queue.clear();
            return;
        }
        AbstractContainerMenu m = mc.player.containerMenu;
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            queue.clear();
            lastAutoContainer = -1;
            return;
        }
        // авто-действия один раз на каждое открытие (ждём пару тиков, пока придёт содержимое)
        if (ContainerMemory.isStorageMenu(m) && m.containerId != lastAutoContainer && queue.isEmpty() && mc.player.tickCount % 2 == 0) {
            lastAutoContainer = m.containerId;
            if (autoStack.get()) quickStack(mc);
            if (autoSort.get() && queue.isEmpty()) sortContainer(mc);
        }
        int n = speed.asInt();
        while (n-- > 0 && !queue.isEmpty()) {
            Click c = queue.peekFirst();
            if (c.containerId != m.containerId) {
                queue.clear();
                break;
            }
            queue.pollFirst();
            if (c.slot >= m.slots.size()) continue;
            mc.gameMode.handleInventoryMouseClick(c.containerId, c.slot, c.button, c.type, mc.player);
        }
        if (queue.isEmpty() && !m.getCarried().isEmpty() && ContainerMemory.isStorageMenu(m)) {
            // на всякий случай: если в курсоре что-то осталось (рассинхрон с сервером) - кладём в пустой слот
            for (int i = 0; i < m.slots.size(); i++) {
                if (m.getSlot(i).getItem().isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(m.containerId, i, 0, ClickType.PICKUP, mc.player);
                    break;
                }
            }
        }
    }

    // ---------- поиск: подсветка сундуков ----------

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        if (!search.get()) return;
        ItemStack hand = mc.player.getMainHandItem();
        if (hand.isEmpty()) return;
        Vec3 me = mc.player.position();
        double r2 = searchRange.get() * searchRange.get();
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 250.0));
        for (Map.Entry<BlockPos, ContainerMemory.Entry> e : ContainerMemory.all().entrySet()) {
            if (Vec3.atCenterOf(e.getKey()).distanceToSqr(me) > r2) continue;
            if (e.getValue().count(hand) <= 0) continue;
            AABB box = new AABB(e.getKey()).deflate(0.04);
            R3D.boxFilled(box, Draw.withAlpha(0x40FF90, (int) (40 + 50 * pulse)));
            R3D.boxOutline(box, 0xFF40FF90, 2.5f);
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (search.get() && mc.screen == null) {
            ItemStack hand = mc.player.getMainHandItem();
            if (!hand.isEmpty()) {
                Vec3 me = mc.player.position();
                double r2 = searchRange.get() * searchRange.get();
                double[] o = new double[3];
                for (Map.Entry<BlockPos, ContainerMemory.Entry> e : ContainerMemory.all().entrySet()) {
                    Vec3 c = Vec3.atCenterOf(e.getKey());
                    if (c.distanceToSqr(me) > r2) continue;
                    int cnt = e.getValue().count(hand);
                    if (cnt <= 0) continue;
                    if (Projection.project(c.x, c.y + 0.8, c.z, o)) {
                        String s = "x" + cnt;
                        int w = Draw.width(s);
                        Draw.rect(g, (int) o[0] - w / 2 - 2, (int) o[1] - 2, (int) o[0] + w / 2 + 2, (int) o[1] + 9, Draw.withAlpha(0, 150));
                        Draw.textCentered(g, s, (int) o[0], (int) o[1], 0xFF40FF90);
                    }
                }
            }
        }
        if (!status.isEmpty() && System.currentTimeMillis() - statusTime < 2500) {
            int sw = mc.getWindow().getGuiScaledWidth();
            String s = "ChestSorter: " + status + (queue.isEmpty() ? "" : " (" + queue.size() + ")");
            int w = Draw.width(s) + 10;
            Draw.panel(g, sw / 2 - w / 2, 4, sw / 2 + w / 2, 17, Draw.bg());
            Draw.textCentered(g, s, sw / 2, 7, Draw.accent());
        }
    }
}
