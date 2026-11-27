package ru.visual.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Чертёж-гайд. Слои снизу вверх, в слое строки по оси Z (строка 0 - "перед", север), символы по оси X.
 * '.' и ' ' - любой блок (не проверяется). Остальные символы - из легенды {@link Spec}.
 * Состояния блоков (поворот поршней, наблюдателей, воронок, повторителей) задаются в системе чертежа
 * и поворачиваются вместе с ним.
 */
public final class Blueprint {
    /** Как проверять клетку. */
    public enum Match {
        /** Блок и его поворот (facing/axis/half/face). */
        STATE,
        /** Только блок (поворот любой) или один из альтернативных. */
        BLOCK,
        /** Любой полный твёрдый блок. */
        SOLID,
        /** Должно быть пусто (воздух/трава/вода) - "выкопать". */
        AIR,
        /** Источник воды (ведро). */
        WATER,
        /** Источник лавы (ведро). */
        LAVA,
        /** Уже есть в мире (спавнер) - не ставится, по нему выравнивается чертёж. */
        EXISTING
    }

    public record Spec(BlockState state, Match match, String note, Set<Block> alts) {
        public Block block() {
            return state.getBlock();
        }

        /** Подходит ли блок в мире под клетку (rotated - ожидаемое состояние с учётом поворота чертежа). */
        public boolean matches(BlockGetter level, BlockPos pos, BlockState have, BlockState rotated) {
            switch (match) {
                case AIR:
                    return have.isAir() || have.canBeReplaced();
                case WATER: {
                    FluidState f = level.getFluidState(pos);
                    return f.is(FluidTags.WATER) && f.isSource();
                }
                case LAVA: {
                    FluidState f = level.getFluidState(pos);
                    return f.is(FluidTags.LAVA) && f.isSource();
                }
                case SOLID:
                    return have.isCollisionShapeFullBlock(level, pos);
                case EXISTING:
                case BLOCK:
                    if (have.is(state.getBlock())) return true;
                    for (Block b : alts) if (have.is(b)) return true;
                    return false;
                default:
                    return (have.is(rotated.getBlock()) || alts.contains(have.getBlock())) && sameOrientation(rotated, have);
            }
        }

        /** Предмет, которым ставится клетка (для списка материалов). */
        public Item item() {
            return switch (match) {
                case WATER -> Items.WATER_BUCKET;
                case LAVA -> Items.LAVA_BUCKET;
                case SOLID -> Items.COBBLESTONE;
                case AIR, EXISTING -> Items.AIR;
                default -> state.getBlock().asItem();
            };
        }

        public String displayName() {
            return switch (match) {
                case SOLID -> Lang.t("Любой твёрдый блок");
                case WATER -> Lang.t("Вода (ведро)");
                case LAVA -> Lang.t("Лава (ведро)");
                case AIR -> Lang.t("Пусто (выкопать)");
                default -> state.getBlock().getName().getString();
            };
        }
    }

    private static final Set<String> ORIENTATION = Set.of("facing", "axis", "half", "face", "hinge", "rotation");

    /** Сравнивает только свойства, задающие поворот (питание, задержка и т.п. не важны). */
    public static boolean sameOrientation(BlockState want, BlockState have) {
        for (Property<?> p : want.getProperties()) {
            String n = p.getName();
            boolean orient = ORIENTATION.contains(n) || (n.equals("type") && want.getBlock() instanceof SlabBlock)
                    || (n.equals("shape") && want.getBlock() instanceof BaseRailBlock);
            if (!orient || !have.hasProperty(p)) continue;
            if (!Objects.equals(want.getValue(p), have.getValue(p))) return false;
        }
        return true;
    }

    // ---------- фабрики клеток ----------

    public static Spec block(Block b, Block... alts) {
        return new Spec(b.defaultBlockState(), Match.BLOCK, null, Set.copyOf(Arrays.asList(alts)));
    }

    public static Spec state(BlockState s) {
        return new Spec(s, Match.STATE, null, Set.of());
    }

    public static Spec state(BlockState s, String note) {
        return new Spec(s, Match.STATE, note, Set.of());
    }

    /** Состояние с поворотом, но блок может быть любым из альтернатив (кнопки из разного дерева). */
    public static Spec state(BlockState s, String note, Block... alts) {
        return new Spec(s, Match.STATE, note, Set.copyOf(Arrays.asList(alts)));
    }

    /** Рельсы вдоль оси (EAST_WEST / NORTH_SOUTH). */
    public static Spec rail(Block b, Direction along, String note) {
        RailShape shape = along.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        BlockState s = b.defaultBlockState().setValue(((BaseRailBlock) b).getShapeProperty(), shape);
        return state(s, note);
    }

    /** Кнопка на стене: out - куда она смотрит (от блока, к которому прикреплена). */
    public static Spec wallButton(Block b, Direction out, String note, Block... alts) {
        BlockState s = b.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, out);
        return state(s, note, alts);
    }

    public static Spec facing(Block b, Direction d) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.FACING)) s = s.setValue(BlockStateProperties.FACING, d);
        else if (s.hasProperty(BlockStateProperties.FACING_HOPPER) && d != Direction.UP) s = s.setValue(BlockStateProperties.FACING_HOPPER, d);
        else if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && d.getAxis() != Direction.Axis.Y) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
        return state(s);
    }

    public static final Spec SOLID = new Spec(Blocks.STONE.defaultBlockState(), Match.SOLID, "Любой полный блок: камень, булыжник, земля, доски...", Set.of());
    public static final Spec AIR = new Spec(Blocks.AIR.defaultBlockState(), Match.AIR, "Здесь должно быть пусто", Set.of());
    public static final Spec WATER = new Spec(Blocks.WATER.defaultBlockState(), Match.WATER, "Вылей ведро воды (нужен источник)", Set.of());
    public static final Spec LAVA = new Spec(Blocks.LAVA.defaultBlockState(), Match.LAVA, "Вылей ведро лавы (нужен источник). Осторожно!", Set.of());

    /** Клетка с пояснением (поворот не проверяется). */
    public static Spec block(String note, Block b, Block... alts) {
        return new Spec(b.defaultBlockState(), Match.BLOCK, note, Set.copyOf(Arrays.asList(alts)));
    }

    /** Повторитель/компаратор: out - куда идёт сигнал (свойство facing у них смотрит назад, на вход). */
    public static Spec diode(Block b, Direction out) {
        return state(b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out.getOpposite()));
    }

    /** Факел на стене: out - куда смотрит факел (от стены), стена с противоположной стороны. */
    public static Spec wallTorch(Block b, Direction out, String note) {
        return state(b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out), note);
    }

    public static Spec existing(Block b, String note) {
        return new Spec(b.defaultBlockState(), Match.EXISTING, note, Set.of());
    }

    public static Map<Character, Spec> legend(Object... kv) {
        Map<Character, Spec> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            Spec s = v instanceof Spec sp ? sp : block((Block) v);
            m.put((Character) kv[i], s);
        }
        return m;
    }

    // ---------- сам чертёж ----------

    public final String name;
    public final String category;
    public final String description;
    public final List<String> guide;
    /** Какой слой ставится на уровень "на землю" (слои ниже уходят под землю). */
    public final int ground;
    public final int sx, sy, sz;
    /**
     * Точка чертежа (x, z - угол клетки), которая должна попасть ровно на стык 4 чанков.
     * Нужно фермам на спавнерах FunTime: лимит мобов считается по чанку, поэтому ферму ставят на пересечение 4 чанков.
     */
    public int[] chunkCorner;
    private final String[][] layers;
    private final Map<Character, Spec> legend;

    public Blueprint(String name, String category, String description, List<String> guide, int ground,
                     Map<Character, Spec> legend, String[][] layers) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.guide = guide;
        this.ground = ground;
        this.legend = legend;
        this.layers = layers;
        this.sy = layers.length;
        int z = 0, x = 0;
        for (String[] layer : layers) {
            z = Math.max(z, layer.length);
            for (String row : layer) x = Math.max(x, row.length());
        }
        this.sz = z;
        this.sx = x;
    }

    public Blueprint withChunkCorner(int x, int z) {
        this.chunkCorner = new int[]{x, z};
        return this;
    }

    /** Клетка чертежа или null, если там "любой блок". */
    public Spec specAt(int x, int y, int z) {
        if (y < 0 || y >= layers.length) return null;
        String[] layer = layers[y];
        if (z < 0 || z >= layer.length) return null;
        String row = layer[z];
        if (x < 0 || x >= row.length()) return null;
        char c = row.charAt(x);
        if (c == '.' || c == ' ') return null;
        return legend.get(c);
    }

    /** Старый интерфейс: блок клетки или null. */
    public Block blockAt(int x, int y, int z) {
        Spec s = specAt(x, y, z);
        return s == null || s.match == Match.AIR ? null : s.block();
    }

    // ---------- подсказки по установке ----------

    public static String dirName(Direction d) {
        return switch (d) {
            case NORTH -> Lang.t("север (-Z)");
            case SOUTH -> Lang.t("юг (+Z)");
            case EAST -> Lang.t("восток (+X)");
            case WEST -> Lang.t("запад (-X)");
            case UP -> Lang.t("вверх");
            case DOWN -> Lang.t("вниз");
        };
    }

    /** Куда должен смотреть игрок, когда ставит блок (для стрелки в мире), или null - неважно. */
    public static Direction lookDirection(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof ObserverBlock) return s.getValue(BlockStateProperties.FACING);
        if (b instanceof PistonBaseBlock || b instanceof DispenserBlock) return s.getValue(BlockStateProperties.FACING).getOpposite();
        if (b instanceof DiodeBlock) return s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        if (b instanceof HopperBlock) return s.getValue(BlockStateProperties.FACING_HOPPER);
        if (b instanceof WallSignBlock) return s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        return null;
    }

    /** Как правильно поставить блок (строки для панели). */
    public static String[] placeHint(Spec spec, BlockState s) {
        String[] h = placeHint0(spec, s);
        if (h == null || spec.note == null || spec.match != Match.STATE) return h;
        for (String line : h) if (line.equals(Lang.t(spec.note))) return h;
        String[] out = Arrays.copyOf(h, h.length + 1);
        out[h.length] = Lang.t(spec.note);
        return out;
    }

    private static String[] placeHint0(Spec spec, BlockState s) {
        if (spec.note != null && spec.match != Match.STATE) return new String[]{spec.note};
        Block b = s.getBlock();
        if (b instanceof ObserverBlock) {
            Direction f = s.getValue(BlockStateProperties.FACING);
            return new String[]{Lang.t("Глаз смотрит на ") + dirName(f) + Lang.t(", выход (точка) - назад."),
                    f.getAxis() == Direction.Axis.Y ? Lang.t("Ставь, глядя ") + (f == Direction.DOWN ? Lang.t("вниз") : Lang.t("вверх")) + "."
                            : Lang.t("Ставь, глядя ровно на ") + dirName(f) + Lang.t(" (не сильно вниз!).")};
        }
        if (b instanceof PistonBaseBlock) {
            Direction f = s.getValue(BlockStateProperties.FACING);
            return new String[]{Lang.t("Толкает на ") + dirName(f) + ".", Lang.t("Ставь, глядя на ") + dirName(f.getOpposite()) + Lang.t(" (лицом к поршню спереди).")};
        }
        if (b instanceof DispenserBlock) {
            Direction f = s.getValue(BlockStateProperties.FACING);
            return new String[]{Lang.t("Выбрасывает на ") + dirName(f) + ".", Lang.t("Ставь, глядя на ") + dirName(f.getOpposite()) + "."};
        }
        if (b instanceof DiodeBlock && !(b instanceof ComparatorBlock)) {
            Direction out = s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
            return new String[]{Lang.t("Сигнал идёт на ") + dirName(out) + ".", Lang.t("Ставь, глядя на ") + dirName(out) + "."};
        }
        if (b instanceof HopperBlock) {
            Direction f = s.getValue(BlockStateProperties.FACING_HOPPER);
            if (f == Direction.DOWN) return new String[]{Lang.t("Сыплет вниз."), Lang.t("Ставь сверху на блок/контейнер под ней.")};
            return new String[]{Lang.t("Сыплет на ") + dirName(f) + ".", Lang.t("Зажми Shift и кликни по боковой грани блока, куда она должна сыпать.")};
        }
        if (b instanceof WallSignBlock) {
            Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return new String[]{Lang.t("Табличка на стене (со стороны ") + dirName(f.getOpposite()) + ").", Lang.t("Кликни по этой стене. Вода через табличку не течёт, а мобы проходят.")};
        }
        if (b instanceof RedStoneWireBlock) return new String[]{Lang.t("Редстоун-пыль: ставь сверху на блок.")};
        if (b instanceof RedstoneWallTorchBlock || b instanceof WallTorchBlock) {
            Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
            String[] base = {Lang.t("Факел на стене: кликни по грани блока со стороны ") + dirName(f) + "."};
            return spec.note != null ? new String[]{base[0], spec.note} : base;
        }
        if (b instanceof BaseTorchBlock) return new String[]{Lang.t("Факел ставь сверху на блок.")};
        if (b instanceof ComparatorBlock) {
            Direction out = s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
            return new String[]{Lang.t("Компаратор: сигнал идёт на ") + dirName(out) + Lang.t(", сзади - то, что он читает."), Lang.t("Ставь, глядя на ") + dirName(out) + Lang.t(". Режим - сравнение (огонёк спереди не горит).")};
        }
        if (b instanceof BaseRailBlock rb) {
            RailShape sh = s.getValue(rb.getShapeProperty());
            String along = sh == RailShape.EAST_WEST ? Lang.t("запад-восток (X)") : sh == RailShape.NORTH_SOUTH ? Lang.t("север-юг (Z)") : Lang.t("по склону");
            String[] base = {Lang.t("Рельсы вдоль ") + along + ".", Lang.t("Ставь, глядя вдоль линии. Следующий рельс сам состыкуется.")};
            return spec.note != null ? new String[]{base[0], base[1], spec.note} : base;
        }
        if (b instanceof ButtonBlock) {
            Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
            String[] base = {Lang.t("Кнопка на грани блока со стороны ") + dirName(f) + "."};
            return spec.note != null ? new String[]{base[0], spec.note} : base;
        }
        if (b instanceof FarmBlock) return new String[]{Lang.t("Положи землю и вскопай её мотыгой (грядка).")};
        if (b instanceof StemBlock || b instanceof AttachedStemBlock) return new String[]{Lang.t("Посади семена тыквы или арбуза на грядку.")};
        if (b instanceof SugarCaneBlock) return new String[]{Lang.t("Тростник сажай на песок/землю рядом с водой.")};
        if (b instanceof CropBlock || b instanceof VegetationBlock) return new String[]{Lang.t("Посади на подходящую почву.")};
        if (spec.note != null) return new String[]{spec.note};
        return null;
    }

    /** Порядок установки внутри слоя: 0 - обычные блоки, 1 - воронки/механизмы, 2 - редстоун, 3 - вода, 4 - растения. */
    public static int phase(Spec spec, BlockState s) {
        if (spec.match == Match.AIR) return -1;
        if (spec.match == Match.WATER || spec.match == Match.LAVA) return 3;
        Block b = s.getBlock();
        if (b instanceof SugarCaneBlock || b instanceof CropBlock || b instanceof VegetationBlock) return 4;
        if (b instanceof RedStoneWireBlock || b instanceof BaseTorchBlock || b instanceof DiodeBlock || b instanceof WallSignBlock || b instanceof ButtonBlock
                || b instanceof LeverBlock || b instanceof BaseRailBlock) return 2;
        if (b instanceof HopperBlock || b instanceof ObserverBlock || b instanceof PistonBaseBlock || b instanceof DispenserBlock) return 1;
        return 0;
    }

    public static BlockState rotate(BlockState s, int quarterTurns) {
        Rotation r = switch (((quarterTurns % 4) + 4) % 4) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return s.rotate(r);
    }
}
