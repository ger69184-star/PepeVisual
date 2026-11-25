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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Помощник фермера: ломает созревший урожай вокруг тебя и сажает заново. Стой на месте или ходи по грядкам. */
public class AutoFarm extends Module {
    private final BoolSetting crops = add(new BoolSetting("Злаки и овощи", "Собирать созревшую пшеницу, морковь, картошку и свёклу.", true));
    private final BoolSetting wart = add(new BoolSetting("Незер-бородавка", "Собирать созревшую незер-бородавку.", true));
    private final BoolSetting fruits = add(new BoolSetting("Тыквы и арбузы", "Ломать тыквы и арбузы, выросшие от стебля (декоративные не трогает).", false));
    private final BoolSetting cane = add(new BoolSetting("Тростник и кактус", "Ломать сахарный тростник и кактус, оставляя нижний блок.", false));
    private final BoolSetting replant = add(new BoolSetting("Пересаживать", "Сажать семена обратно. Семена должны лежать в хотбаре.", true));
    private final NumSetting reach = add(new NumSetting("Радиус", "Как далеко от тебя собирать урожай (блоки, не больше досягаемости руки).", 4.0, 1.5, 4.5, 0.5));
    private final BoolSetting rotate = add(new BoolSetting("Смотреть на блок", "Поворачивать голову к блоку перед действием (выглядит естественнее, меньше вопросов от античита).", true));
    private final NumSetting delay = add(new NumSetting("Задержка (тики)", "Пауза между действиями. Больше - медленнее и менее подозрительно.", 3, 1, 12, 1));

    private BlockPos breaking;
    private int cd;
    /** Слот хотбара до пересадки (вернём его на следующем тике), -1 - не меняли. */
    private int restoreSlot = -1;
    private long tick;
    private final Map<BlockPos, long[]> pending = new HashMap<>();
    private final Map<BlockPos, Item> pendingItem = new HashMap<>();

    public AutoFarm() {
        super("AutoFarm", "Помощь фермерам: автоматически собирает созревший урожай вокруг и сажает заново.", Category.HELPERS);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (breaking != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        if (restoreSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
        breaking = null;
        pending.clear();
        pendingItem.clear();
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

    private void lookAt(LocalPlayer p, Vec3 target) {
        if (!rotate.get()) return;
        Vec3 eye = p.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        p.setYRot(p.getYRot() + net.minecraft.util.Mth.wrapDegrees(yaw - p.getYRot()));
        p.setXRot(net.minecraft.util.Mth.clamp(pitch, -90f, 90f));
    }

    private Item seedFor(BlockState st) {
        if (st.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if (st.is(Blocks.CARROTS)) return Items.CARROT;
        if (st.is(Blocks.POTATOES)) return Items.POTATO;
        if (st.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        if (st.is(Blocks.NETHER_WART)) return Items.NETHER_WART;
        return null;
    }

    @Override
    public void onTick(Minecraft mc) {
        tick++;
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
                lookAt(p, Vec3.atCenterOf(breaking));
                mc.gameMode.continueDestroyBlock(breaking, Direction.UP);
                p.swing(InteractionHand.MAIN_HAND);
                return;
            }
        }

        if (cd > 0) {
            cd--;
            return;
        }

        if (restoreSlot >= 0 && pending.isEmpty()) {
            p.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
        }

        if (replant.get() && tryReplant(mc, p, level)) {
            cd = delay.asInt();
            return;
        }

        BlockPos target = findTarget(p, level);
        if (target == null) return;
        BlockState st = level.getBlockState(target);
        Item seed = seedFor(st);
        lookAt(p, Vec3.atCenterOf(target));
        mc.gameMode.startDestroyBlock(target, Direction.UP);
        p.swing(InteractionHand.MAIN_HAND);
        if (!level.getBlockState(target).isAir()) breaking = target.immutable();
        if (seed != null && replant.get()) {
            pending.put(target.immutable(), new long[]{tick});
            pendingItem.put(target.immutable(), seed);
        }
        cd = delay.asInt();
    }

    private BlockPos findTarget(LocalPlayer p, ClientLevel level) {
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

    private boolean tryReplant(Minecraft mc, LocalPlayer p, ClientLevel level) {
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
            lookAt(p, Vec3.atCenterOf(below).add(0, 0.5, 0));
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
            p.swing(InteractionHand.MAIN_HAND);
            it.remove();
            pendingItem.remove(pos);
            return true;
        }
        return false;
    }
}
