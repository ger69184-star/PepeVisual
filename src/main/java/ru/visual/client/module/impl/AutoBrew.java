package ru.visual.client.module.impl;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Navigator;
import ru.visual.client.util.Projection;
import ru.visual.client.util.R3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Помощник зельевара. Бот ЗАПОМИНАЕТ каждую зельеварку рядом (что в ней, сколько ещё вариться, чего не хватает)
 * и подходит только к тем, где есть дело: варка закончилась, можно доложить бутылки/ингредиент/топливо
 * или забрать готовое. Раньше он по кругу открывал все зельеварки подряд каждые 8 секунд.
 * Ходит сам (поиск пути A*: плиты, ковры, двери, ступеньки, спуски до 3 блоков), над каждой зельеваркой - её состояние.
 */
public class AutoBrew extends Module {
    /** Зелье и форма: 0 - обычное, 1 - взрывное, 2 - оседающее. */
    private record State(Holder<Potion> potion, int form) {}

    private record Step(State in, Item ingredient, State out) {}

    private enum Kind {
        UNKNOWN("не проверена", 0xFFB0B0C0),
        BREWING("варится", 0xFFFFD24A),
        READY("готово", 0xFF7CFFB0),
        EMPTY("ждёт бутылки", 0xFF8AB4FF),
        NEED_INGREDIENT("нет ингредиента", 0xFFFF9A4A),
        NEED_FUEL("нет топлива", 0xFFFF6A6A),
        FOREIGN("чужие зелья", 0xFFC080FF),
        UNREACHABLE("не дойти", 0xFFFF4040);

        final String title;
        final int color;

        Kind(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }

    /** Что бот знает о конкретной зельеварке. */
    private static final class Stand {
        final BlockPos pos;
        Kind kind = Kind.UNKNOWN;
        long readyAt, retryAt;
        State contents;
        int number;

        Stand(BlockPos pos) {
            this.pos = pos;
        }
    }

    private final ModeSetting potion = add(new ModeSetting("Зелье", "Какое зелье варим.", "Сила",
            "Сила", "Скорость", "Исцеление", "Регенерация", "Огнестойкость", "Ночное зрение", "Подводное дыхание",
            "Невидимость", "Прыгучесть", "Медленное падение", "Черепашья мощь", "Яд", "Слабость", "Медлительность", "Вред"));
    private final BoolSetting strong = add(new BoolSetting("Усиленное (светящаяся пыль)", "Варить усиленную версию (II), если у зелья она есть.", false));
    private final BoolSetting longer = add(new BoolSetting("Продлённое (редстоун)", "Варить продлённую версию, если у зелья она есть (если включено и 'Усиленное' - выбирается усиленное).", false));
    private final BoolSetting splash = add(new BoolSetting("Взрывное (порох)", "Превращать готовое зелье во взрывное порохом.", false));
    private final BoolSetting lingering = add(new BoolSetting("Оседающее (драконье дыхание)", "После пороха превращать во оседающее зелье драконьим дыханием.", false));
    private final BoolSetting walk = add(new BoolSetting("Ходить к зельеваркам", "Бот сам идёт к зельеварке, где есть дело. Выключи - будет обслуживать только те, до которых дотягиваешься.", true));
    private final NumSetting walkRadius = add(new NumSetting("Радиус обхода", "Как далеко от места старта бот ищет зельеварки (блоки).", 16, 6, 48, 2));
    private final BoolSetting back = add(new BoolSetting("Возвращаться назад", "Когда дел нет - вернуться на место, где бота включили.", true));
    private final BoolSetting collect = add(new BoolSetting("Забирать готовое", "Забирать готовые зелья в инвентарь.", true));
    private final BoolSetting panel = add(new BoolSetting("Панель", "Список зельеварок с таймерами и чего не хватает.", true));
    private final BoolSetting labels = add(new BoolSetting("Метки над зельеварками", "Над каждой зельеваркой её номер, состояние и таймер.", true));
    private final NumSetting reach = add(new NumSetting("Дотягиваться", "С какого расстояния бот открывает зельеварку (блоки).", 4.0, 2.0, 4.5, 0.5));
    private final NumSetting delay = add(new NumSetting("Задержка (тики)", "Пауза между кликами в окне зельеварки. Больше - медленнее и незаметнее.", 3, 1, 12, 1));

    private final Map<BlockPos, Stand> stands = new HashMap<>();
    private Stand current;
    private int wait, openWait, startWait, pauseTicks;
    /** Окно открыл бот (тогда он его и закрывает); если открыл игрок - бот помогает, но окно не закрывает. */
    private boolean botOpened;
    private long tick, lastScan = -100;
    private final List<Step> chain = new ArrayList<>();
    private State finalState;
    private String recipeKey = "";

    private final Navigator nav = new Navigator();
    private Stand target;
    private BlockPos home;
    private boolean goingHome;
    private int repaths;
    private String status = "ищу зельеварки";

    public AutoBrew() {
        super("AutoBrew", "Зельевар: запоминает все зельеварки рядом, сам ходит к тем, где есть дело, и варит выбранное зелье.", Category.HELPERS);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        home = mc.player != null ? mc.player.blockPosition() : null;
        stands.clear();
        lastScan = -100;
    }

    @Override
    protected void onDisable() {
        chain.clear();
        stands.clear();
        current = null;
        target = null;
        home = null;
        goingHome = false;
        wait = openWait = startWait = pauseTicks = 0;
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

    // ---------- память зельеварок ----------

    /** Раз в секунду обновляет список зельеварок вокруг точки старта (новые - "не проверена", сломанные - забываем). */
    private void scan(Minecraft mc) {
        if (tick - lastScan < 20) return;
        lastScan = tick;
        BlockPos center = home != null ? home : mc.player.blockPosition();
        double radius = walk.get() ? walkRadius.get() : reach.get() + 1;
        int cr = (int) Math.ceil(radius / 16.0) + 1;
        int pcx = center.getX() >> 4, pcz = center.getZ() >> 4;
        Map<BlockPos, Boolean> seen = new HashMap<>();
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                ChunkAccess ca = mc.level.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (!(ca instanceof LevelChunk lc)) continue;
                for (BlockEntity be : lc.getBlockEntities().values()) {
                    if (!(be instanceof BrewingStandBlockEntity)) continue;
                    BlockPos p = be.getBlockPos();
                    double hx = p.getX() + 0.5 - (center.getX() + 0.5), hz = p.getZ() + 0.5 - (center.getZ() + 0.5);
                    if (Math.sqrt(hx * hx + hz * hz) > radius || Math.abs(p.getY() - center.getY()) > 8) continue;
                    if (!walk.get() && !inReach(mc, p)) continue;
                    seen.put(p.immutable(), true);
                    stands.computeIfAbsent(p.immutable(), Stand::new);
                }
            }
        }
        for (Iterator<Map.Entry<BlockPos, Stand>> it = stands.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Stand> e = it.next();
            if (!seen.containsKey(e.getKey())) {
                if (target == e.getValue()) target = null;
                it.remove();
            }
        }
        // постоянные номера: по расстоянию от точки старта
        List<Stand> list = new ArrayList<>(stands.values());
        list.sort(Comparator.comparingDouble((Stand st) -> st.pos.distSqr(center)).thenComparingLong(st -> st.pos.asLong()));
        for (int i = 0; i < list.size(); i++) list.get(i).number = i + 1;
    }

    /** Есть ли смысл идти к зельеварке прямо сейчас. */
    private boolean needsVisit(Minecraft mc, Stand s) {
        return switch (s.kind) {
            case UNKNOWN -> true;
            case BREWING -> tick >= s.readyAt;
            case READY -> collect.get() && hasFreeSlot(mc);
            case EMPTY -> hasAnyInput(mc);
            case NEED_INGREDIENT -> {
                Step st = s.contents == null ? null : stepFor(s.contents);
                yield st != null && countInv(mc, st.ingredient) > 0;
            }
            case NEED_FUEL -> countInv(mc, Items.BLAZE_POWDER) > 0;
            case FOREIGN, UNREACHABLE -> tick >= s.retryAt;
        };
    }

    private Stand pickStand(Minecraft mc) {
        Vec3 me = mc.player.position();
        Stand best = null;
        double bestD = Double.MAX_VALUE;
        for (Stand s : stands.values()) {
            if (!needsVisit(mc, s)) continue;
            double d = Vec3.atCenterOf(s.pos).distanceToSqr(me);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    // ---------- тик ----------

    private static boolean physical(Minecraft mc, KeyMapping km) {
        return ru.visual.client.util.Keys.physical(mc, km);
    }

    @Override
    public void onTick(Minecraft mc) {
        tick++;
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        if (home == null) home = mc.player.blockPosition();
        buildChain();
        String key = potion.get() + strong.get() + longer.get() + splash.get() + lingering.get();
        if (!key.equals(recipeKey)) {
            // поменяли рецепт - всё, кроме варящихся, надо пересмотреть
            recipeKey = key;
            for (Stand s : stands.values()) if (s.kind != Kind.BREWING) s.kind = Kind.UNKNOWN;
        }
        scan(mc);

        if (wait > 0) {
            wait--;
            return;
        }
        if (mc.screen instanceof BrewingStandScreen && mc.player.containerMenu instanceof BrewingStandMenu m) {
            openWait = 0;
            nav.stop(mc);
            if (current == null) {
                current = standUnderCrosshair(mc); // открыл сам - тоже запоминаем
                botOpened = false;
            }
            status = current != null ? "работаю в зельеварке №" + current.number : "работаю в зельеварке";
            handle(mc, m);
            return;
        }
        if (mc.screen != null) {
            nav.stop(mc);
            return;
        }
        if (openWait > 0) {
            if (--openWait == 0 && current != null) {
                // окно так и не открылось (заслонили, далеко, лаг) - попробуем позже
                current.kind = Kind.UNREACHABLE;
                current.retryAt = tick + 200;
                current = null;
            }
            return;
        }

        // игрок сам жмёт W A S D - не мешаем
        if (physical(mc, mc.options.keyUp) || physical(mc, mc.options.keyDown) || physical(mc, mc.options.keyLeft)
                || physical(mc, mc.options.keyRight) || physical(mc, mc.options.keyJump)) {
            if (pauseTicks == 0) {
                nav.stop(mc);
                nav.clear();
            }
            pauseTicks = 60;
        }
        if (pauseTicks > 0) {
            if (--pauseTicks == 0) {
                // игрок сам отошёл - новое "место ожидания" там, где он остановился
                home = mc.player.blockPosition();
                lastScan = -100;
                goingHome = false;
            }
            status = "пауза: ты управляешь";
            return;
        }

        if (target != null && !needsVisit(mc, target)) {
            target = null;
            nav.stop(mc);
            nav.clear();
        }
        if (target == null) {
            target = pickStand(mc);
            repaths = 0;
            if (target == null) {
                status = stands.isEmpty() ? "рядом нет зельеварок" : idleStatus(mc);
                idle(mc);
                return;
            }
            goingHome = false;
            nav.stop(mc);
            nav.clear();
        }

        if (inReach(mc, target.pos)) {
            nav.stop(mc);
            nav.clear();
            lookAt(mc, target.pos);
            current = target;
            botOpened = true;
            openWait = 15;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target.pos), Direction.UP, target.pos, false));
            target = null;
            return;
        }

        if (!walk.get()) {
            target.kind = Kind.UNREACHABLE;
            target.retryAt = tick + 100;
            target = null;
            return;
        }
        status = "иду к зельеварке №" + target.number;
        walkTo(mc, target.pos, true);
    }

    private String idleStatus(Minecraft mc) {
        long soonest = Long.MAX_VALUE;
        for (Stand s : stands.values()) if (s.kind == Kind.BREWING) soonest = Math.min(soonest, s.readyAt);
        if (soonest != Long.MAX_VALUE) return "жду: ближайшая готова через " + Math.max(0, (soonest - tick) / 20) + " с";
        return "нечего делать: не хватает предметов";
    }

    private Stand standUnderCrosshair(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            BlockPos p = bh.getBlockPos();
            if (mc.level.getBlockEntity(p) instanceof BrewingStandBlockEntity) return stands.computeIfAbsent(p.immutable(), Stand::new);
        }
        return null;
    }

    /** Дел нет: возвращаемся на место старта (если включено) и стоим. */
    private void idle(Minecraft mc) {
        if (!back.get() || home == null || !walk.get()) {
            nav.stop(mc);
            return;
        }
        if (mc.player.blockPosition().distSqr(home) <= 2) {
            nav.stop(mc);
            nav.clear();
            goingHome = false;
            return;
        }
        goingHome = true;
        status = status + ", иду на место";
        walkTo(mc, home, false);
    }

    private void walkTo(Minecraft mc, BlockPos dest, boolean stand) {
        if (!nav.hasPath()) {
            BlockPos start = Navigator.standingCell(mc.level, mc.player);
            double r = reach.get() - 0.3;
            boolean ok = stand
                    ? nav.findPath(mc.level, start, dest, pos -> {
                        double fy = Navigator.floorY(mc.level, pos);
                        if (Double.isNaN(fy)) fy = pos.getY();
                        return new Vec3(pos.getX() + 0.5, fy + 1.62, pos.getZ() + 0.5).distanceTo(Vec3.atCenterOf(dest)) <= r;
                    }, 8000)
                    : nav.findPath(mc.level, start, dest, pos -> pos.distSqr(dest) <= 2, 8000);
            if (!ok) {
                giveUp(mc, "путь не найден");
                return;
            }
        }
        boolean arrived = nav.tick(mc, mc.player);
        if (nav.failed) {
            nav.clear();
            if (++repaths > 3) giveUp(mc, "застрял");
            return;
        }
        if (arrived) {
            nav.clear();
            if (stand && target != null && !inReach(mc, target.pos) && ++repaths > 3) giveUp(mc, "не дотянуться");
            if (!stand) goingHome = false;
        }
    }

    private void giveUp(Minecraft mc, String why) {
        nav.stop(mc);
        nav.clear();
        if (target != null) {
            target.kind = Kind.UNREACHABLE;
            target.retryAt = tick + 600;
            status = "зельеварка №" + target.number + ": " + why;
            target = null;
        } else if (goingHome) {
            goingHome = false;
            home = mc.player.blockPosition(); // назад не пройти - новое место ожидания здесь
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

    /** Закрыть окно и запомнить, в каком состоянии оставили зельеварку. */
    private void finishVisit(Minecraft mc, Kind kind, State contents, long readyIn) {
        if (botOpened) mc.player.closeContainer();
        botOpened = false;
        if (current != null) {
            current.kind = kind;
            current.contents = contents;
            if (kind == Kind.BREWING) current.readyAt = tick + readyIn + 4;
            if (kind == Kind.FOREIGN) current.retryAt = tick + 1200;
        }
        current = null;
        startWait = 0;
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
        int d = delay.asInt();

        // 1. варится - запоминаем, когда будет готово, и уходим
        if (m.getBrewingTicks() > 0) {
            finishVisit(mc, Kind.BREWING, null, m.getBrewingTicks());
            return;
        }
        // 2. что в бутылочных слотах
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
        if (unknown || (cur != null && !same(cur, finalState) && stepFor(cur) == null)) {
            finishVisit(mc, Kind.FOREIGN, cur, 0);
            return;
        }
        // 3. готово - забираем
        if (filled > 0 && same(cur, finalState)) {
            if (collect.get() && hasFreeSlot(mc)) {
                for (int i = 0; i < 3; i++) {
                    if (!m.getSlot(i).getItem().isEmpty()) {
                        click(mc, m, i, 0, ClickType.QUICK_MOVE);
                        wait = d;
                        return;
                    }
                }
            }
            finishVisit(mc, Kind.READY, cur, 0);
            return;
        }
        // 4. топливо (огненный порошок): кладём, только если зельеварке есть что варить
        if (m.getFuel() <= 0 && m.getSlot(4).getItem().isEmpty()) {
            int inv = findItem(m, Items.BLAZE_POWDER);
            if (inv >= 0) {
                placeOne(mc, m, inv, 4);
                wait = d;
                return;
            }
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
            finishVisit(mc, Kind.EMPTY, null, 0);
            return;
        }
        // 6. ингредиент
        Step step = stepFor(cur);
        ItemStack ingSlot = m.getSlot(3).getItem();
        if (ingSlot.isEmpty()) {
            int inv = step == null ? -1 : findItem(m, step.ingredient);
            if (inv < 0) {
                finishVisit(mc, Kind.NEED_INGREDIENT, cur, 0);
                return;
            }
            placeOne(mc, m, inv, 3);
            wait = d;
            return;
        }
        if (step != null && !ingSlot.is(step.ingredient)) {
            finishVisit(mc, Kind.FOREIGN, cur, 0); // чужой ингредиент - не трогаем
            return;
        }
        // 7. всё положено, но варка не началась
        if (m.getFuel() <= 0 && m.getSlot(4).getItem().isEmpty()) {
            finishVisit(mc, Kind.NEED_FUEL, cur, 0);
            return;
        }
        if (++startWait < 10) {
            wait = 2; // сервер ещё не прислал, что варка пошла
            return;
        }
        finishVisit(mc, Kind.BREWING, cur, 400);
    }

    // ---------- инвентарь ----------

    private boolean hasFreeSlot(Minecraft mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) return true;
        return false;
    }

    private boolean hasAnyInput(Minecraft mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            State s = stateOf(inv.getItem(i));
            if (s != null && stepFor(s) != null) return true;
        }
        return false;
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

    private String standLine(Stand s) {
        String t = s.kind.title;
        if (s.kind == Kind.BREWING) {
            long left = Math.max(0, s.readyAt - tick) / 20;
            t = left > 0 ? "варится, " + left + " с" : "должна быть готова";
        } else if (s.kind == Kind.NEED_INGREDIENT && s.contents != null) {
            Step st = stepFor(s.contents);
            if (st != null) t = "нужен " + shortName(st.ingredient);
        }
        return t;
    }

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        if (!labels.get()) return;
        for (Stand s : stands.values()) {
            int c = s == target || s == current ? Draw.accent() : s.kind.color;
            R3D.boxOutline(new AABB(s.pos).deflate(0.05), Draw.withAlpha(c, 200), 1.6f);
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (labels.get()) {
            double[] o = new double[3];
            for (Stand s : stands.values()) {
                if (!Projection.project(s.pos.getX() + 0.5, s.pos.getY() + 1.25, s.pos.getZ() + 0.5, o)) continue;
                String text = "№" + s.number + " " + standLine(s);
                int w = Draw.width(text) + 8;
                int x = (int) o[0] - w / 2, y = (int) o[1] - 6;
                Draw.panel(g, x, y, x + w, y + 12, Draw.bg(), false);
                Draw.text(g, text, x + 4, y + 2, s.kind.color);
            }
        }
        if (!panel.get() || mc.screen != null || finalState == null) return;
        List<String> lines = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        lines.add("AutoBrew: " + potionName(finalState));
        cols.add(Draw.accent());
        lines.add(status);
        cols.add(Draw.WHITE);
        // чего не хватает - одной строкой
        StringBuilder miss = new StringBuilder();
        if (!hasAnyInput(mc)) miss.append(chain.isEmpty() ? "" : "бутылки (" + potionName(chain.get(0).in) + ")");
        if (countInv(mc, Items.BLAZE_POWDER) == 0) miss.append(miss.length() > 0 ? ", " : "").append("огненный порошок");
        for (Step st : chain) {
            if (countInv(mc, st.ingredient) == 0) miss.append(miss.length() > 0 ? ", " : "").append(shortName(st.ingredient));
        }
        if (miss.length() > 0) {
            lines.add("Нет: " + miss);
            cols.add(0xFFFF8A6A);
        }
        List<Stand> list = new ArrayList<>(stands.values());
        list.sort(Comparator.comparingInt(st -> st.number));
        int shown = 0;
        for (Stand s : list) {
            if (shown++ >= 8) {
                lines.add("... ещё " + (list.size() - 8));
                cols.add(Draw.GRAY);
                break;
            }
            lines.add("№" + s.number + "  " + standLine(s));
            cols.add(s.kind.color);
        }
        lines.add("Готово в инвентаре: " + countPotions(mc, finalState));
        cols.add(Draw.GRAY);
        int w = 0;
        for (String l : lines) w = Math.max(w, Draw.width(l));
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = 4, y = sh - 40 - lines.size() * 10;
        Draw.panel(g, x, y, x + w + 10, y + lines.size() * 10 + 4, Draw.bg());
        Draw.rect(g, x, y + 2, x + 2, y + lines.size() * 10 + 2, Draw.accent());
        for (int i = 0; i < lines.size(); i++) Draw.text(g, lines.get(i), x + 6, y + 3 + i * 10, cols.get(i));
    }
}
