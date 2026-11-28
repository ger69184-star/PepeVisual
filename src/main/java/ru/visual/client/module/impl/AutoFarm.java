package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import net.minecraft.world.phys.AABB;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Aim;
import ru.visual.client.util.Human;
import ru.visual.client.util.Keys;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Помощник фермера: ломает созревший урожай вокруг тебя и сажает заново. Стой на месте или ходи по грядкам.
 * Работает "как человек": камера плавно поворачивается к случайной точке на блоке (каждый кадр, с учётом чувствительности мыши),
 * семена берутся в руку заранее, а удар и посадка - это обычные нажатия ЛКМ/ПКМ, которые выполняет сама игра,
 * когда прицел действительно на блоке. Блоки за стенами не трогает, паузы между действиями всегда разные.
 * Двинул мышь или нажал кнопку мыши сам - помощник ждёт.
 */
public class AutoFarm extends Module {
    private final Human.Tune tune = humanTune(true, true);
    private final BoolSetting crops = add(new BoolSetting("Злаки и овощи", "Собирать созревшую пшеницу, морковь, картошку и свёклу.", true));
    private final BoolSetting wart = add(new BoolSetting("Незер-бородавка", "Собирать созревшую незер-бородавку.", true));
    private final BoolSetting fruits = add(new BoolSetting("Тыквы и арбузы", "Ломать тыквы и арбузы, выросшие от стебля (декоративные не трогает).", false));
    private final BoolSetting cane = add(new BoolSetting("Тростник и кактус", "Ломать сахарный тростник и кактус, оставляя нижний блок.", false));
    private final BoolSetting replant = add(new BoolSetting("Пересаживать", "Сажать семена обратно. Семена должны лежать в хотбаре.", true));
    private final BoolSetting plantEmpty = add(new BoolSetting("Засеивать пустые грядки", "Сажать семена на любую пустую вспаханную землю (и незерский нарост на песок душ) рядом.", false));
    private final ModeSetting seedMode = add(new ModeSetting("Что сажать", "Что сажать на пустые грядки. Любое - первое, что найдётся в хотбаре.", "Любое",
            "Любое", "Пшеница", "Морковь", "Картофель", "Свёкла"));
    private final BoolSetting berries = add(new BoolSetting("Сладкие ягоды", "Собирать спелые ягоды с кустов (куст не ломается).", false));
    private final BoolSetting boneMeal = add(new BoolSetting("Костная мука", "Удобрять незрелые посевы костной мукой из хотбара.", false));
    private final NumSetting reach = add(new NumSetting("Радиус", "Как далеко от тебя собирать урожай (блоки, не больше досягаемости руки).", 4.0, 1.5, 4.5, 0.5));
    private final BoolSetting rotate = addIf(new BoolSetting("Смотреть на блок", "Поворачивать голову к блоку перед действием (выглядит естественнее, меньше вопросов от античита).", true), () -> !tune.advanced());
    private final BoolSetting show = add(new BoolSetting("Показывать зону", "3D: круг досягаемости, созревшие растения в радиусе подсвечены, ломаемый блок - ярче.", true));
    private final NumSetting delay = add(new NumSetting("Задержка (тики)", "Средняя пауза между действиями. Каждая пауза немного разная, после серии действий - короткий перерыв, как у человека.", 4, 1, 12, 1));

    private enum Act { BREAK, HOLD, USE }

    /** Одно действие: на какой блок смотреть, куда, с каким предметом в руке. replantAt - грядка, куда потом посадить. */
    private record Job(Act act, BlockPos pos, Vec3 point, Item item, BlockPos replantAt, Item replantSeed) {}

    private record Replant(BlockPos ground, Item seed, long tick) {}

    private Job job;
    private boolean holding;
    private int jobTicks, reaction, swapWait, pause, idleTicks;
    /** Слот хотбара до того, как помощник взял семена (вернём, когда работа кончится), -1 - не меняли. */
    private int restoreSlot = -1;
    private long tick;
    private int harvested;
    private final java.util.ArrayDeque<Replant> replants = new java.util.ArrayDeque<>();
    private final Map<BlockPos, Long> skip = new HashMap<>();
    private final Human.Pacer pacer = new Human.Pacer();
    // базовый режим
    private BlockPos breaking;
    private int cd;
    private final Map<BlockPos, long[]> pending = new HashMap<>();
    private final Map<BlockPos, Item> pendingItem = new HashMap<>();
    private Boolean lastAdvanced;

    public AutoFarm() {
        super("AutoFarm", "Помощь фермерам: автоматически собирает созревший урожай вокруг и сажает заново.", Category.HELPERS);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        stopHold(mc);
        Aim.release(this);
        if (breaking != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        breaking = null;
        pending.clear();
        pendingItem.clear();
        lastAdvanced = null;
        if (restoreSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
        job = null;
        replants.clear();
        skip.clear();
    }

    private boolean harvestable(ClientLevel level, BlockState st, BlockPos pos) {
        Block b = st.getBlock();
        if (crops.get() && b instanceof CropBlock cb && cb.isMaxAge(st)) return true;
        if (wart.get() && st.is(Blocks.NETHER_WART) && st.getValue(NetherWartBlock.AGE) >= 3) return true;
        if (fruits.get() && (st.is(Blocks.PUMPKIN) || st.is(Blocks.MELON)) && grownFromStem(level, pos, st)) return true;
        if (cane.get() && (st.is(Blocks.SUGAR_CANE) || st.is(Blocks.CACTUS)) && level.getBlockState(pos.below()).is(b)) return true;
        return false;
    }

    /** Тыква/арбуз выросли от стебля: рядом есть "прикреплённый" стебель, смотрящий на этот блок. */
    private static boolean grownFromStem(ClientLevel level, BlockPos pos, BlockState fruit) {
        Block stem = fruit.is(Blocks.PUMPKIN) ? Blocks.ATTACHED_PUMPKIN_STEM : Blocks.ATTACHED_MELON_STEM;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = level.getBlockState(pos.relative(d));
            if (s.is(stem) && s.getValue(AttachedStemBlock.FACING) == d.getOpposite()) return true;
        }
        return false;
    }

    private Item seedFor(BlockState st) {
        if (st.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if (st.is(Blocks.CARROTS)) return Items.CARROT;
        if (st.is(Blocks.POTATOES)) return Items.POTATO;
        if (st.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        if (st.is(Blocks.NETHER_WART)) return Items.NETHER_WART;
        return null;
    }

    /** Ломается не сразу (нужно держать ЛКМ). */
    private static boolean needsHold(BlockState st) {
        return st.is(Blocks.PUMPKIN) || st.is(Blocks.MELON) || st.is(Blocks.CACTUS);
    }

    private void stopHold(Minecraft mc) {
        if (holding && mc.options != null) Keys.release(mc, mc.options.keyAttack);
        holding = false;
    }

    @Override
    public void onTick(Minecraft mc) {
        tick++;
        boolean adv = tune.advanced();
        if (lastAdvanced == null || lastAdvanced != adv) {
            // переключили режим - начинаем с чистого листа
            if (lastAdvanced != null) {
                Minecraft m = mc;
                stopHold(m);
                Aim.release(this);
                if (breaking != null && m.gameMode != null) m.gameMode.stopDestroyBlock();
                breaking = null;
                job = null;
                pending.clear();
                pendingItem.clear();
                replants.clear();
            }
            lastAdvanced = adv;
        }
        if (!adv) {
            basicTick(mc);
            return;
        }
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.gameMode == null) return;
        if (mc.screen != null || p.isUsingItem()) {
            stopHold(mc);
            return;
        }
        Aim.yieldToUser = tune.yieldToMouse();
        // игрок сам двигает мышь или жмёт кнопки мыши - не мешаем
        if ((tune.yieldToMouse() && Aim.userActive()) || mc.mouseHandler.isLeftPressed() || mc.mouseHandler.isRightPressed()) {
            stopHold(mc);
            Aim.release(this);
            job = null;
            pause = Human.range(8, 20);
            return;
        }
        if (pause > 0) {
            pause--;
            return;
        }

        // тыква/арбуз/кактус: держим ЛКМ, пока блок не сломается (как игрок)
        if (holding) {
            BlockState st = job == null ? null : level.getBlockState(job.pos);
            if (job == null || !harvestable(level, st, job.pos) || !Aim.crosshairOn(mc, job.pos) || ++jobTicks > 120) {
                if (job != null && st != null && !harvestable(level, st, job.pos)) harvested++;
                stopHold(mc);
                job = null;
                pacer.done(delay.get(), tune.breaks());
                return;
            }
            Aim.look(this, job.point, tune.cameraSpeed());
            if (mc.mouseHandler.isMouseGrabbed()) {
                mc.options.keyAttack.setDown(true);
            } else {
                // окно игры не в фокусе - ванильный код не держит ЛКМ, ломаем так же, как он
                mc.gameMode.continueDestroyBlock(job.pos, Direction.UP);
                p.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }

        if (job != null && !valid(level, p, job)) job = null;
        if (job == null) {
            if (!pacer.ready()) return;
            job = nextJob(mc, p, level);
            if (job == null) {
                Aim.release(this);
                // работы давно нет - возвращаем слот, который был до семян
                if (restoreSlot >= 0 && ++idleTicks > 30) {
                    p.getInventory().setSelectedSlot(restoreSlot);
                    restoreSlot = -1;
                }
                return;
            }
            idleTicks = 0;
            jobTicks = 0;
            reaction = tune.react(1, 3);
        }

        Aim.look(this, job.point, tune.cameraSpeed());
        if (++jobTicks > 60) { // так и не навели прицел (заслонили, далеко) - пропустим этот блок
            skip.put(job.pos, tick + 100);
            job = null;
            return;
        }
        // нужный предмет берём в руку, пока камера ещё поворачивается
        if (job.item != null && !p.getMainHandItem().is(job.item)) {
            if (!selectHotbar(p, job.item)) {
                job = null;
                return;
            }
            swapWait = tune.react(2, 4);
            return;
        }
        if (swapWait > 0) {
            swapWait--;
            return;
        }
        if (!Aim.crosshairOn(mc, job.pos) || !Aim.settled(this)) return;
        if (reaction-- > 0) return;

        switch (job.act) {
            case BREAK -> {
                Keys.click(mc.options.keyAttack);
                harvested++;
                if (job.replantAt != null) replants.add(new Replant(job.replantAt, job.replantSeed, tick));
            }
            case HOLD -> {
                Keys.click(mc.options.keyAttack);
                if (mc.mouseHandler.isMouseGrabbed()) mc.options.keyAttack.setDown(true);
                holding = true;
                jobTicks = 0;
                return;
            }
            case USE -> Keys.click(mc.options.keyUse);
        }
        Aim.freeze(Human.range(1, 2));
        pacer.done(delay.get(), tune.breaks());
        job = null;
    }

    /** Действие ещё имеет смысл (блок не сломал кто-то другой, семена не кончились). */
    private boolean valid(ClientLevel level, LocalPlayer p, Job j) {
        BlockState st = level.getBlockState(j.pos);
        if (p.getEyePosition().distanceToSqr(j.point) > (reach.get() + 0.6) * (reach.get() + 0.6)) return false;
        if (j.item != null && !hasHotbar(p, j.item)) return false;
        return switch (j.act) {
            case BREAK, HOLD -> harvestable(level, st, j.pos);
            case USE -> {
                if (j.item == Items.BONE_MEAL) yield st.getBlock() instanceof CropBlock cb && !cb.isMaxAge(st);
                if (st.is(Blocks.SWEET_BERRY_BUSH)) yield st.getValue(SweetBerryBushBlock.AGE) >= 3;
                yield (st.is(Blocks.FARMLAND) || st.is(Blocks.SOUL_SAND)) && level.getBlockState(j.pos.above()).isAir();
            }
        };
    }

    private Job nextJob(Minecraft mc, LocalPlayer p, ClientLevel level) {
        skip.values().removeIf(t -> t < tick);
        // 1. посадить туда, где только что собрали
        while (replant.get() && !replants.isEmpty()) {
            Replant r = replants.peekFirst();
            if (tick - r.tick > 100 || !hasHotbar(p, r.seed)) {
                replants.pollFirst();
                continue;
            }
            BlockState g = level.getBlockState(r.ground);
            boolean okGround = r.seed == Items.NETHER_WART ? g.is(Blocks.SOUL_SAND) : g.is(Blocks.FARMLAND);
            BlockState above = level.getBlockState(r.ground.above());
            if (!okGround || (!above.isAir() && tick - r.tick > 10)) {
                replants.pollFirst();
                continue;
            }
            if (!above.isAir()) return null; // урожай ещё не исчез - подождём тик
            replants.pollFirst();
            Vec3 pt = Aim.pointOn(level, p, r.ground, 0.97, 1.0);
            if (pt != null) return new Job(Act.USE, r.ground, pt, r.seed, null, null);
        }
        // 2. ягоды
        if (berries.get()) {
            Job j = best(p, level, (pos, st) -> st.is(Blocks.SWEET_BERRY_BUSH) && st.getValue(SweetBerryBushBlock.AGE) >= 3,
                    (pos, pt) -> new Job(Act.USE, pos, pt, null, null, null));
            if (j != null) return j;
        }
        // 3. созревший урожай (семена для пересадки - сразу в руку, как держит игрок)
        Job j = best(p, level, (pos, st) -> harvestable(level, st, pos), (pos, pt) -> {
            BlockState st = level.getBlockState(pos);
            if (needsHold(st)) return new Job(Act.HOLD, pos, pt, null, null, null);
            Item seed = seedFor(st);
            boolean re = replant.get() && seed != null && hasHotbar(p, seed);
            return new Job(Act.BREAK, pos, pt, re ? seed : null, re ? pos.below() : null, re ? seed : null);
        });
        if (j != null) return j;
        // 4. пустые грядки
        if (plantEmpty.get()) {
            j = best(p, level, (pos, st) -> (st.is(Blocks.FARMLAND) || st.is(Blocks.SOUL_SAND)) && level.getBlockState(pos.above()).isAir()
                    && pickSeed(p, st.is(Blocks.SOUL_SAND)) != null, (pos, pt) -> {
                Item seed = pickSeed(p, level.getBlockState(pos).is(Blocks.SOUL_SAND));
                return seed == null ? null : new Job(Act.USE, pos, pt, seed, null, null);
            });
            if (j != null) return j;
        }
        // 5. костная мука
        if (boneMeal.get() && hasHotbar(p, Items.BONE_MEAL)) {
            j = best(p, level, (pos, st) -> st.getBlock() instanceof CropBlock cb && !cb.isMaxAge(st),
                    (pos, pt) -> new Job(Act.USE, pos, pt, Items.BONE_MEAL, null, null));
            if (j != null) return j;
        }
        return null;
    }

    /**
     * Лучший блок под условие: ближе к тебе и ближе к прицелу (камера идёт вдоль ряда, а не мечется),
     * и обязательно видимый - сквозь блоки человек не кликает.
     */
    private Job best(LocalPlayer p, ClientLevel level, java.util.function.BiPredicate<BlockPos, BlockState> test,
                     java.util.function.BiFunction<BlockPos, Vec3, Job> make) {
        Vec3 eye = p.getEyePosition();
        int r = (int) Math.ceil(reach.get());
        double max = reach.get() * reach.get();
        BlockPos base = p.blockPosition();
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        java.util.Map<BlockPos, Double> score = new HashMap<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
            Vec3 c = Vec3.atCenterOf(m);
            double d = eye.distanceToSqr(c);
            if (d > max || skip.containsKey(m)) continue;
            BlockState st = level.getBlockState(m);
            if (st.isAir() || !test.test(m, st)) continue;
            BlockPos im = m.immutable();
            found.add(im);
            score.put(im, Math.sqrt(d) + Aim.angleTo(p, c) / 14.0);
        }
        found.sort(java.util.Comparator.comparingDouble(score::get));
        int tries = 0;
        for (BlockPos pos : found) {
            if (tries++ >= 8) break;
            boolean flat = level.getBlockState(pos).is(Blocks.FARMLAND) || level.getBlockState(pos).is(Blocks.SOUL_SAND);
            Vec3 pt = flat ? Aim.pointOn(level, p, pos, 0.97, 1.0) : Aim.pointOn(level, p, pos, 0.3, 0.85);
            if (pt == null || eye.distanceToSqr(pt) > max) continue;
            Job j = make.apply(pos, pt);
            if (j != null) return j;
        }
        return null;
    }

    @Override
    public String info() {
        return harvested > 0 ? String.valueOf(harvested) : null;
    }

    private boolean selectHotbar(LocalPlayer p, Item item) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) {
                if (inv.getSelectedSlot() != i) {
                    if (restoreSlot < 0) restoreSlot = inv.getSelectedSlot();
                    inv.setSelectedSlot(i);
                }
                return true;
            }
        }
        return false;
    }

    private Item pickSeed(LocalPlayer p, boolean soul) {
        if (soul) return hasHotbar(p, Items.NETHER_WART) ? Items.NETHER_WART : null;
        Item[] order = switch (seedMode.get()) {
            case "Пшеница" -> new Item[]{Items.WHEAT_SEEDS};
            case "Морковь" -> new Item[]{Items.CARROT};
            case "Картофель" -> new Item[]{Items.POTATO};
            case "Свёкла" -> new Item[]{Items.BEETROOT_SEEDS};
            default -> new Item[]{Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS};
        };
        for (Item it : order) if (hasHotbar(p, it)) return it;
        return null;
    }

    private static boolean hasHotbar(LocalPlayer p, Item it) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(it)) return true;
        return false;
    }


    // ================================================================ базовый режим (как раньше)

    private void basicLook(LocalPlayer p, Vec3 target) {
        if (!rotate.get()) return;
        Vec3 eye = p.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        p.setYRot(p.getYRot() + net.minecraft.util.Mth.wrapDegrees(yaw - p.getYRot()));
        p.setXRot(net.minecraft.util.Mth.clamp(pitch, -90f, 90f));
    }

    /** Базовый режим (как раньше): камера сразу на блок, действия напрямую, пауза всегда одинаковая. */
    private void basicTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.gameMode == null || mc.screen != null || p.isUsingItem()) return;

        // Ломание блока продолжаем КАЖДЫЙ тик (иначе тыквы/арбузы ломались в несколько раз дольше)
        if (breaking != null) {
            BlockState st = level.getBlockState(breaking);
            if (!harvestable(level, st, breaking) || p.getEyePosition().distanceToSqr(Vec3.atCenterOf(breaking)) > 36) {
                if (!st.isAir()) mc.gameMode.stopDestroyBlock();
                breaking = null;
                cd = delay.asInt();
            } else {
                basicLook(p, Vec3.atCenterOf(breaking));
                mc.gameMode.continueDestroyBlock(breaking, Direction.UP);
                p.swing(InteractionHand.MAIN_HAND);
                return;
            }
        }

        if (cd > 0) {
            cd--;
            return;
        }

        if (replant.get() && basicReplant(mc, p, level)) {
            cd = delay.asInt();
            return;
        }
        if (berries.get() && basicBerries(mc, p, level)) {
            cd = delay.asInt();
            return;
        }

        BlockPos target = basicFindTarget(p, level);
        if (target == null) {
            if (plantEmpty.get() && basicPlantEmpty(mc, p, level)) {
                cd = delay.asInt();
                return;
            }
            if (boneMeal.get() && basicBoneMeal(mc, p, level)) {
                cd = delay.asInt();
                return;
            }
            // работы нет - возвращаем слот, который был выбран до пересадки
            if (restoreSlot >= 0 && pending.isEmpty()) {
                p.getInventory().setSelectedSlot(restoreSlot);
                restoreSlot = -1;
            }
            return;
        }
        harvested++;
        BlockState st = level.getBlockState(target);
        Item seed = seedFor(st);
        basicLook(p, Vec3.atCenterOf(target));
        mc.gameMode.startDestroyBlock(target, Direction.UP);
        p.swing(InteractionHand.MAIN_HAND);
        if (!level.getBlockState(target).isAir()) breaking = target.immutable();
        if (seed != null && replant.get()) {
            pending.put(target.immutable(), new long[]{tick});
            pendingItem.put(target.immutable(), seed);
        }
        cd = delay.asInt();
    }

    private BlockPos basicFindTarget(LocalPlayer p, ClientLevel level) {
        Vec3 eye = p.getEyePosition();
        int r = (int) Math.ceil(reach.get());
        BlockPos base = p.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE, max = reach.get() * reach.get();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    double d = eye.distanceToSqr(Vec3.atCenterOf(m));
                    if (d > max || d >= bestD) continue;
                    if (harvestable(level, level.getBlockState(m), m)) {
                        bestD = d;
                        best = m.immutable();
                    }
                }
            }
        }
        return best;
    }

    private void basicUse(Minecraft mc, LocalPlayer p, BlockPos pos, Direction face) {
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        basicLook(p, hit);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        p.swing(InteractionHand.MAIN_HAND);
    }

    /** Ближайший блок в радиусе, подходящий под условие. */
    private BlockPos basicNearest(LocalPlayer p, ClientLevel level, java.util.function.BiPredicate<BlockPos, BlockState> test) {
        Vec3 eye = p.getEyePosition();
        int r = (int) Math.ceil(reach.get());
        BlockPos base = p.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE, max = reach.get() * reach.get();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
            double d = eye.distanceToSqr(Vec3.atCenterOf(m));
            if (d > max || d >= bestD) continue;
            if (test.test(m, level.getBlockState(m))) {
                bestD = d;
                best = m.immutable();
            }
        }
        return best;
    }

    private boolean basicPlantEmpty(Minecraft mc, LocalPlayer p, ClientLevel level) {
        BlockPos ground = basicNearest(p, level, (pos, st) -> (st.is(Blocks.FARMLAND) || st.is(Blocks.SOUL_SAND)) && level.getBlockState(pos.above()).isAir());
        if (ground == null) return false;
        Item seed = pickSeed(p, level.getBlockState(ground).is(Blocks.SOUL_SAND));
        if (seed == null || !selectHotbar(p, seed)) return false;
        basicUse(mc, p, ground, Direction.UP);
        return true;
    }

    private boolean basicBerries(Minecraft mc, LocalPlayer p, ClientLevel level) {
        BlockPos bush = basicNearest(p, level, (pos, st) -> st.is(Blocks.SWEET_BERRY_BUSH) && st.getValue(SweetBerryBushBlock.AGE) >= 3);
        if (bush == null) return false;
        basicUse(mc, p, bush, Direction.UP);
        harvested++;
        return true;
    }

    private boolean basicBoneMeal(Minecraft mc, LocalPlayer p, ClientLevel level) {
        if (!hasHotbar(p, Items.BONE_MEAL)) return false;
        BlockPos crop = basicNearest(p, level, (pos, st) -> st.getBlock() instanceof CropBlock cb && !cb.isMaxAge(st));
        if (crop == null || !selectHotbar(p, Items.BONE_MEAL)) return false;
        basicUse(mc, p, crop, Direction.UP);
        return true;
    }

    private boolean basicReplant(Minecraft mc, LocalPlayer p, ClientLevel level) {
        Iterator<Map.Entry<BlockPos, long[]>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, long[]> e = it.next();
            BlockPos pos = e.getKey();
            Item seed = pendingItem.get(pos);
            if (tick - e.getValue()[0] > 100 || seed == null) {
                it.remove();
                pendingItem.remove(pos);
                continue;
            }
            if (!level.getBlockState(pos).isAir()) continue;
            BlockPos below = pos.below();
            BlockState ground = level.getBlockState(below);
            boolean okGround = seed == Items.NETHER_WART ? ground.is(Blocks.SOUL_SAND) : ground.is(Blocks.FARMLAND);
            if (!okGround) {
                it.remove();
                pendingItem.remove(pos);
                continue;
            }
            Inventory inv = p.getInventory();
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).is(seed)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                it.remove();
                pendingItem.remove(pos);
                continue;
            }
            if (inv.getSelectedSlot() != slot) {
                if (restoreSlot < 0) restoreSlot = inv.getSelectedSlot();
                inv.setSelectedSlot(slot);
            }
            basicLook(p, Vec3.atCenterOf(below).add(0, 0.5, 0));
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
            p.swing(InteractionHand.MAIN_HAND);
            it.remove();
            pendingItem.remove(pos);
            return true;
        }
        return false;
    }


    private int ripeTimer;
    private java.util.List<BlockPos> ripe = new java.util.ArrayList<>();

    @Override
    public void onWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, float pt, Minecraft mc) {
        if (!show.get() || mc.player == null || mc.level == null) return;
        Vec3 pp = mc.player.getPosition(pt);
        ru.visual.client.util.R3D.circle(pp.x, pp.y + 0.05, pp.z, reach.get(), 48, 0x8055FF77, 1.4f);
        // список созревших обновляем раз в 10 тиков: 9x9x5 блоков каждый кадр - лишняя нагрузка
        if (ripeTimer++ % 10 == 0) {
            java.util.List<BlockPos> out = new java.util.ArrayList<>();
            int r = (int) Math.ceil(reach.get());
            BlockPos base = mc.player.blockPosition();
            for (BlockPos q : BlockPos.betweenClosed(base.offset(-r, -2, -r), base.offset(r, 2, r))) {
                if (harvestable(mc.level, mc.level.getBlockState(q), q)) out.add(q.immutable());
                if (out.size() >= 64) break;
            }
            ripe = out;
        }
        Job cur = job;
        BlockPos hl = cur == null ? breaking : cur.pos;
        for (BlockPos q : ripe) {
            AABB box = new AABB(q).deflate(0.15);
            ru.visual.client.util.R3D.boxOutline(box, q.equals(hl) ? 0xFFFFE040 : 0x9055FF77, q.equals(hl) ? 2.2f : 1.2f);
        }
    }
}
