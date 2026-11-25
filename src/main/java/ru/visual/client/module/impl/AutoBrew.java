package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Navigator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;

/**
 * Помощник зельевара. Бот сам ходит между зельеварками (путь A*), открывает каждую,
 * кладёт топливо, бутылки и нужный ингредиент из инвентаря и забирает готовые зелья.
 */
public class AutoBrew extends Module {
    /** Зелье и форма: 0 - обычное, 1 - взрывное, 2 - оседающее. */
    private record State(Holder<Potion> potion, int form) {}

    private record Step(State in, Item ingredient, State out) {}

    private final ModeSetting potion = add(new ModeSetting("Зелье", "Какое зелье варим. ЛКМ - следующее, ПКМ - предыдущее.", "Сила",
            "Сила", "Скорость", "Исцеление", "Регенерация", "Огнестойкость", "Ночное зрение", "Подводное дыхание",
            "Невидимость", "Прыгучесть", "Медленное падение", "Черепашья мощь", "Яд", "Слабость", "Медлительность", "Вред"));
    private final BoolSetting strong = add(new BoolSetting("Усиленное (светящаяся пыль)", "Варить усиленную версию (II), если у зелья она есть.", false));
    private final BoolSetting longer = add(new BoolSetting("Продлённое (редстоун)", "Варить продлённую версию, если у зелья она есть (если включено и 'Усиленное' - выбирается усиленное).", false));
    private final BoolSetting splash = add(new BoolSetting("Взрывное (порох)", "Превращать готовое зелье во взрывное порохом.", false));
    private final BoolSetting lingering = add(new BoolSetting("Оседающее (драконье дыхание)", "После пороха превращать во оседающее зелье драконьим дыханием.", false));
    private final BoolSetting panel = add(new BoolSetting("Панель", "Показывать рецепт, нужные ингредиенты и что сейчас делает бот.", true));
    private final BoolSetting collect = add(new BoolSetting("Забирать готовое", "Автоматически забирать готовые зелья в инвентарь.", true));
    private final NumSetting reach = add(new NumSetting("Радиус", "До каких зельеварок бот дотягивается (блоки).", 4.0, 2.0, 4.5, 0.5));
    private final NumSetting delay = add(new NumSetting("Задержка (тики)", "Пауза между действиями в окне зельеварки. Больше - медленнее и безопаснее.", 3, 1, 12, 1));
    private final BoolSetting walk = add(new BoolSetting("Ходить к зельеваркам", "Бот сам идёт к каждой зельеварке в радиусе обхода (ищет путь, прыгает через ступеньки, обходит лаву).", true));
    private final NumSetting walkRadius = add(new NumSetting("Радиус обхода", "Как далеко от тебя бот ищет зельеварки (блоки).", 20, 6, 48, 2));
    private final BoolSetting back = add(new BoolSetting("Возвращаться назад", "После обхода вернуться на место, откуда бот начал.", true));
    private final NumSetting revisit = add(new NumSetting("Повторный заход (сек)", "Как часто бот заходит в одну и ту же зельеварку.", 8, 2, 40, 1));

    private final Map<BlockPos, Long> lastVisit = new HashMap<>();
    private BlockPos current;
    private int wait, openWait;
    private long tick;
    private final List<Step> chain = new ArrayList<>();
    private State finalState;

    private final Navigator nav = new Navigator();
    private BlockPos target, home;
    private boolean goingHome;
    private List<BlockPos> standCache = new ArrayList<>();
    private long lastScan = -100;
    private int repaths;
    private String status = "ищу зельеварки";

    public AutoBrew() {
        super("AutoBrew", "Помощь зельеварам: бот сам раскладывает бутылки, ингредиенты и топливо по зельеваркам и забирает зелья.", Category.HELPERS);
    }

    @Override
    protected void onDisable() {
        chain.clear();
        lastVisit.clear();
        current = null;
        target = null;
        home = null;
        goingHome = false;
        wait = 0;
        openWait = 0;
        Minecraft mc = Minecraft.getInstance();
        nav.clear();
        if (mc.options != null) nav.stop(mc);
        if (mc.player != null && mc.screen instanceof BrewingStandScreen) mc.player.closeContainer();
    }

    // ---------- рецепт ----------

    private void buildChain() {
        chain.clear();
        State water = new State(Potions.WATER, 0), awkward = new State(Potions.AWKWARD, 0);
        Holder<Potion> base, strongP = null, longP = null;
        Item ingr;
        Holder<Potion> pre = null; // промежуточное зелье для "испорченных" рецептов
        Item preIngr = null;
        boolean fromWater = false;
        switch (potion.get()) {
            case "Скорость" -> { base = Potions.SWIFTNESS; strongP = Potions.STRONG_SWIFTNESS; longP = Potions.LONG_SWIFTNESS; ingr = Items.SUGAR; }
            case "Исцеление" -> { base = Potions.HEALING; strongP = Potions.STRONG_HEALING; ingr = Items.GLISTERING_MELON_SLICE; }
            case "Регенерация" -> { base = Potions.REGENERATION; strongP = Potions.STRONG_REGENERATION; longP = Potions.LONG_REGENERATION; ingr = Items.GHAST_TEAR; }
            case "Огнестойкость" -> { base = Potions.FIRE_RESISTANCE; longP = Potions.LONG_FIRE_RESISTANCE; ingr = Items.MAGMA_CREAM; }
            case "Ночное зрение" -> { base = Potions.NIGHT_VISION; longP = Potions.LONG_NIGHT_VISION; ingr = Items.GOLDEN_CARROT; }
            case "Подводное дыхание" -> { base = Potions.WATER_BREATHING; longP = Potions.LONG_WATER_BREATHING; ingr = Items.PUFFERFISH; }
            case "Невидимость" -> { pre = Potions.NIGHT_VISION; preIngr = Items.GOLDEN_CARROT; base = Potions.INVISIBILITY; longP = Potions.LONG_INVISIBILITY; ingr = Items.FERMENTED_SPIDER_EYE; }
            case "Прыгучесть" -> { base = Potions.LEAPING; strongP = Potions.STRONG_LEAPING; longP = Potions.LONG_LEAPING; ingr = Items.RABBIT_FOOT; }
            case "Медленное падение" -> { base = Potions.SLOW_FALLING; longP = Potions.LONG_SLOW_FALLING; ingr = Items.PHANTOM_MEMBRANE; }
            case "Черепашья мощь" -> { base = Potions.TURTLE_MASTER; strongP = Potions.STRONG_TURTLE_MASTER; longP = Potions.LONG_TURTLE_MASTER; ingr = Items.TURTLE_HELMET; }
            case "Яд" -> { base = Potions.POISON; strongP = Potions.STRONG_POISON; longP = Potions.LONG_POISON; ingr = Items.SPIDER_EYE; }
            case "Слабость" -> { fromWater = true; base = Potions.WEAKNESS; longP = Potions.LONG_WEAKNESS; ingr = Items.FERMENTED_SPIDER_EYE; }
            case "Медлительность" -> { pre = Potions.SWIFTNESS; preIngr = Items.SUGAR; base = Potions.SLOWNESS; strongP = Potions.STRONG_SLOWNESS; longP = Potions.LONG_SLOWNESS; ingr = Items.FERMENTED_SPIDER_EYE; }
            case "Вред" -> { pre = Potions.HEALING; preIngr = Items.GLISTERING_MELON_SLICE; base = Potions.HARMING; strongP = Potions.STRONG_HARMING; ingr = Items.FERMENTED_SPIDER_EYE; }
            default -> { base = Potions.STRENGTH; strongP = Potions.STRONG_STRENGTH; longP = Potions.LONG_STRENGTH; ingr = Items.BLAZE_POWDER; }
        }
        State cur;
        if (fromWater) {
            chain.add(new Step(water, ingr, new State(base, 0)));
        } else {
            chain.add(new Step(water, Items.NETHER_WART, awkward));
            if (pre != null) {
                chain.add(new Step(awkward, preIngr, new State(pre, 0)));
                chain.add(new Step(new State(pre, 0), ingr, new State(base, 0)));
            } else {
                chain.add(new Step(awkward, ingr, new State(base, 0)));
            }
        }
        cur = new State(base, 0);
        if (strong.get() && strongP != null) {
            State s = new State(strongP, 0);
            chain.add(new Step(cur, Items.GLOWSTONE_DUST, s));
            cur = s;
        } else if (longer.get() && longP != null) {
            State s = new State(longP, 0);
            chain.add(new Step(cur, Items.REDSTONE, s));
            cur = s;
        }
        if (splash.get() || lingering.get()) {
            State s = new State(cur.potion, 1);
            chain.add(new Step(cur, Items.GUNPOWDER, s));
            cur = s;
            if (lingering.get()) {
                State l = new State(cur.potion, 2);
                chain.add(new Step(cur, Items.DRAGON_BREATH, l));
                cur = l;
            }
        }
        finalState = cur;
    }

    private static State stateOf(ItemStack st) {
        if (st.isEmpty()) return null;
        int form;
        if (st.is(Items.POTION)) form = 0;
        else if (st.is(Items.SPLASH_POTION)) form = 1;
        else if (st.is(Items.LINGERING_POTION)) form = 2;
        else return null;
        PotionContents pc = st.get(DataComponents.POTION_CONTENTS);
        if (pc == null) return null;
        Optional<Holder<Potion>> o = pc.potion();
        if (o.isEmpty()) return null;
        return new State(o.get(), form);
    }

    private static boolean same(State a, State b) {
        return a != null && b != null && a.form == b.form && a.potion.value() == b.potion.value();
    }

    private Step stepFor(State cur) {
        for (Step s : chain) if (same(s.in, cur)) return s;
        return null;
    }

    // ---------- тик ----------

    @Override
    public void onTick(Minecraft mc) {
        tick++;
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        if (wait > 0) {
            wait--;
            return;
        }
        if (mc.screen instanceof BrewingStandScreen && mc.player.containerMenu instanceof BrewingStandMenu m) {
            openWait = 0;
            nav.stop(mc);
            status = "работаю в зельеварке";
            handle(mc, m);
            return;
        }
        if (mc.screen != null) {
            nav.stop(mc);
            return;
        }
        if (openWait > 0) {
            if (--openWait == 0 && current != null) {
                lastVisit.put(current, tick);
                current = null;
            }
            return;
        }

        // идём домой после обхода
        if (goingHome) {
            status = "возвращаюсь на место";
            walkHome(mc);
            return;
        }

        if (target == null) {
            target = pickStand(mc);
            repaths = 0;
            if (target == null) {
                status = stands(mc).isEmpty() ? "рядом нет зельеварок" : "жду: всё варится";
                finishRound(mc);
                return;
            }
            if (home == null) home = mc.player.blockPosition();
        }

        if (inReach(mc, target)) {
            nav.stop(mc);
            nav.clear();
            lookAt(mc, target);
            current = target;
            openWait = 12;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
            target = null;
            return;
        }

        if (!walk.get()) {
            lastVisit.put(target, tick);
            target = null;
            return;
        }
        status = "иду к зельеварке " + target.toShortString();
        walkToStand(mc);
    }

    private void walkToStand(Minecraft mc) {
        final BlockPos stand = target;
        if (!nav.hasPath()) {
            boolean ok = nav.findPath(mc.level, mc.player.blockPosition(), stand,
                    pos -> Vec3.atBottomCenterOf(pos).add(0, 1.62, 0).distanceTo(Vec3.atCenterOf(stand)) <= reach.get() - 0.4, 6000);
            if (!ok) {
                give_up(mc);
                return;
            }
        }
        boolean arrived = nav.tick(mc, mc.player);
        if (nav.failed) {
            nav.clear();
            if (++repaths > 2) give_up(mc);
            return;
        }
        if (arrived && !inReach(mc, stand)) {
            nav.clear();
            if (++repaths > 2) give_up(mc);
        }
    }

    private void give_up(Minecraft mc) {
        nav.stop(mc);
        nav.clear();
        if (target != null) lastVisit.put(target, tick);
        target = null;
    }

    private void finishRound(Minecraft mc) {
        nav.stop(mc);
        if (back.get() && home != null && mc.player.blockPosition().distSqr(home) > 4) {
            goingHome = true;
            nav.clear();
            return;
        }
        home = null;
    }

    private void walkHome(Minecraft mc) {
        if (home == null) {
            goingHome = false;
            return;
        }
        final BlockPos h = home;
        if (!nav.hasPath()) {
            boolean ok = nav.findPath(mc.level, mc.player.blockPosition(), h, pos -> pos.distSqr(h) <= 2, 6000);
            if (!ok) {
                goingHome = false;
                home = null;
                nav.stop(mc);
                return;
            }
        }
        boolean arrived = nav.tick(mc, mc.player);
        if (arrived || nav.failed) {
            nav.stop(mc);
            nav.clear();
            goingHome = false;
            home = null;
        }
        // если появилась зельеварка, которую пора навестить, прерываем возвращение
        if (goingHome && tick % 20 == 0 && pickStand(mc) != null) {
            goingHome = false;
            nav.stop(mc);
            nav.clear();
        }
    }

    private boolean inReach(Minecraft mc, BlockPos stand) {
        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(stand)) <= reach.get();
    }

    private void lookAt(Minecraft mc, BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 c = Vec3.atCenterOf(pos);
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        double hd = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, hd)));
    }

    /** Все зельеварки в загруженных чанках в радиусе обхода (кэш на секунду). */
    private List<BlockPos> stands(Minecraft mc) {
        if (tick - lastScan < 20) return standCache;
        lastScan = tick;
        List<BlockPos> out = new ArrayList<>();
        double radius = walk.get() ? walkRadius.get() : reach.get();
        int cr = (int) Math.ceil(radius / 16.0) + 1;
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        Vec3 me = mc.player.position();
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                ChunkAccess ca = mc.level.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (!(ca instanceof LevelChunk lc)) continue;
                for (BlockEntity be : lc.getBlockEntities().values()) {
                    if (!(be instanceof BrewingStandBlockEntity)) continue;
                    BlockPos p = be.getBlockPos();
                    double hx = p.getX() + 0.5 - me.x, hz = p.getZ() + 0.5 - me.z;
                    if (Math.sqrt(hx * hx + hz * hz) <= radius && Math.abs(p.getY() - me.y) <= 10) out.add(p);
                }
            }
        }
        standCache = out;
        return out;
    }

    /** Ближайшая зельеварка, которую пора навестить. */
    private BlockPos pickStand(Minecraft mc) {
        long cooldown = (long) (revisit.get() * 20);
        Vec3 me = mc.player.position();
        return stands(mc).stream()
                .filter(p -> {
                    Long last = lastVisit.get(p);
                    return last == null || tick - last >= cooldown;
                })
                .min(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(me)))
                .orElse(null);
    }

    private void finishVisit(Minecraft mc) {
        mc.player.closeContainer();
        if (current != null) lastVisit.put(current, tick);
        current = null;
        wait = delay.asInt();
    }

    // ---------- работа внутри окна зельеварки ----------

    private void click(Minecraft mc, BrewingStandMenu m, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(m.containerId, slot, button, type, mc.player);
    }

    private void placeOne(Minecraft mc, BrewingStandMenu m, int invSlot, int target) {
        click(mc, m, invSlot, 0, ClickType.PICKUP);
        click(mc, m, target, 1, ClickType.PICKUP);
        click(mc, m, invSlot, 0, ClickType.PICKUP);
    }

    private int findItem(BrewingStandMenu m, Item item) {
        for (int i = 5; i < m.slots.size(); i++) {
            if (m.getSlot(i).getItem().is(item)) return i;
        }
        return -1;
    }

    private int findPotion(BrewingStandMenu m, State cur) {
        if (cur != null) {
            for (int i = 5; i < m.slots.size(); i++) {
                if (same(stateOf(m.getSlot(i).getItem()), cur)) return i;
            }
            return -1;
        }
        for (Step s : chain) {
            for (int i = 5; i < m.slots.size(); i++) {
                if (same(stateOf(m.getSlot(i).getItem()), s.in)) return i;
            }
        }
        return -1;
    }

    private void handle(Minecraft mc, BrewingStandMenu m) {
        buildChain();
        int d = delay.asInt();

        // 1. топливо
        if (m.getFuel() <= 0 && m.getSlot(4).getItem().isEmpty()) {
            int inv = findItem(m, Items.BLAZE_POWDER);
            if (inv >= 0) {
                placeOne(mc, m, inv, 4);
                wait = d;
                return;
            }
        }
        // 2. идёт варка - ждём
        if (m.getBrewingTicks() > 0) {
            finishVisit(mc);
            return;
        }
        // 3. что сейчас в бутылочных слотах
        State cur = null;
        int filled = 0;
        boolean unknown = false;
        for (int i = 0; i < 3; i++) {
            ItemStack st = m.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            filled++;
            State s = stateOf(st);
            if (s == null) unknown = true;
            else if (cur == null) cur = s;
            else if (!same(cur, s)) unknown = true;
        }
        if (unknown) {
            finishVisit(mc);
            return;
        }
        // 4. готово - забираем
        if (filled > 0 && same(cur, finalState)) {
            if (collect.get()) {
                for (int i = 0; i < 3; i++) {
                    if (!m.getSlot(i).getItem().isEmpty()) {
                        click(mc, m, i, 0, ClickType.QUICK_MOVE);
                        wait = d;
                        return;
                    }
                }
            }
            finishVisit(mc);
            return;
        }
        // 5. докладываем бутылки
        if (filled < 3) {
            int inv = findPotion(m, cur);
            if (inv >= 0) {
                click(mc, m, inv, 0, ClickType.QUICK_MOVE);
                wait = d;
                return;
            }
        }
        if (filled == 0) {
            finishVisit(mc);
            return;
        }
        // 6. ингредиент
        if (m.getSlot(3).getItem().isEmpty()) {
            Step st = stepFor(cur);
            if (st != null) {
                int inv = findItem(m, st.ingredient);
                if (inv >= 0) {
                    placeOne(mc, m, inv, 3);
                    wait = d;
                    return;
                }
            }
        }
        finishVisit(mc);
    }

    // ---------- панель ----------

    private static String shortName(Item it) {
        return new ItemStack(it).getHoverName().getString();
    }

    private static String potionName(State st) {
        ItemStack s = new ItemStack(st.form == 2 ? Items.LINGERING_POTION : st.form == 1 ? Items.SPLASH_POTION : Items.POTION);
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(st.potion));
        return s.getHoverName().getString();
    }

    private int countInv(Minecraft mc, Item it) {
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(it)) n += s.getCount();
        }
        return n;
    }

    private int countPotions(Minecraft mc, State st) {
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (same(stateOf(inv.getItem(i)), st)) n++;
        return n;
    }

    @Override
    public String info() {
        return potion.get();
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (!panel.get() || mc.screen != null) return;
        buildChain();
        List<String> lines = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        lines.add("AutoBrew: " + potionName(finalState));
        cols.add(Draw.accent());
        lines.add("Сейчас: " + status);
        cols.add(Draw.WHITE);
        int bottles = countPotions(mc, chain.get(0).in);
        lines.add("Бутылки с водой: " + bottles);
        cols.add(bottles > 0 ? 0xFF7CFFB0 : 0xFFFF6A6A);
        int fuel = countInv(mc, Items.BLAZE_POWDER);
        lines.add("Огненный порошок (топливо): " + fuel);
        cols.add(fuel > 0 ? 0xFF7CFFB0 : 0xFFFF6A6A);
        for (Step st : chain) {
            int have = countInv(mc, st.ingredient);
            lines.add((have > 0 ? "✔ " : "✘ ") + shortName(st.ingredient) + ": " + have);
            cols.add(have > 0 ? 0xFF7CFFB0 : 0xFFFF6A6A);
        }
        int ready = countPotions(mc, finalState);
        lines.add("Готово в инвентаре: " + ready + ", зельеварок рядом: " + standCache.size());
        cols.add(Draw.GRAY);
        int w = 0;
        for (String l : lines) w = Math.max(w, Draw.width(l));
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = 4, y = sh - 40 - lines.size() * 10;
        Draw.rect(g, x, y, x + w + 10, y + lines.size() * 10 + 4, Draw.bg());
        Draw.rect(g, x, y, x + 2, y + lines.size() * 10 + 4, Draw.accent());
        for (int i = 0; i < lines.size(); i++) Draw.text(g, lines.get(i), x + 6, y + 3 + i * 10, cols.get(i));
    }
}
