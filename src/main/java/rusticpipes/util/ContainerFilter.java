package rusticpipes.util;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import rusticpipes.RusticPipes;
import rusticpipes.handlers.ForgeConfigHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether item pipes may pull from / push into a container, from the
 * "Blocked Containers" config (block id first, then NBT checks) and the Locks mod.
 */
public final class ContainerFilter {

    private ContainerFilter() {}

    public enum Direction { PULL, PUSH }

    private static List<Rule> rules = null;

    private static boolean locksInit = false;
    private static Method locksLocked = null;

    /** Drop parsed rules so the next check re-reads the config. */
    public static void invalidate() {
        rules = null;
    }

    /**
     * Per-transfer cache: a tile's NBT is only serialized once per pipe transfer, and only
     * when a rule's id matched and that rule has NBT checks.
     */
    public static final class Context {
        private final java.util.Map<BlockPos, NBTTagCompound> nbt = new java.util.HashMap<>();
        private final java.util.Map<BlockPos, Boolean> locked = new java.util.HashMap<>();
    }

    public static boolean isBlocked(World world, BlockPos pos, Direction dir, Context ctx) {
        if (ForgeConfigHandler.pipes.respectLocksMod && isLocksLocked(world, pos, ctx)) return true;

        List<Rule> list = getRules();
        if (list.isEmpty()) return false;

        ResourceLocation id = world.getBlockState(pos).getBlock().getRegistryName();
        if (id == null) return false;
        String idStr = id.toString();

        for (Rule rule : list) {
            if (!rule.appliesTo(dir)) continue;
            if (!rule.idPattern.matcher(idStr).matches()) continue;
            if (rule.checks.isEmpty()) return true;

            NBTTagCompound tag = ctx.nbt.get(pos);
            if (tag == null) {
                TileEntity te = world.getTileEntity(pos);
                if (te == null) continue;
                try {
                    tag = te.writeToNBT(new NBTTagCompound());
                } catch (Exception e) {
                    continue;
                }
                ctx.nbt.put(pos, tag);
            }
            boolean all = true;
            for (NbtCheck check : rule.checks) {
                if (!check.test(tag)) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Locks mod (melonslise) — locks live in a world capability, not in tile NBT
    // -----------------------------------------------------------------------

    private static boolean isLocksLocked(World world, BlockPos pos, Context ctx) {
        if (!locksInit) {
            locksInit = true;
            if (Loader.isModLoaded("locks")) {
                try {
                    locksLocked = Class.forName("melonslise.locks.common.util.LocksUtil")
                            .getMethod("locked", World.class, BlockPos.class);
                } catch (Exception e) {
                    RusticPipes.LOGGER.warn("Locks mod support disabled, API changed: {}", e.toString());
                }
            }
        }
        if (locksLocked == null) return false;
        Boolean cached = ctx.locked.get(pos);
        if (cached != null) return cached;
        boolean result;
        try {
            result = Boolean.TRUE.equals(locksLocked.invoke(null, world, pos));
        } catch (Exception e) {
            result = false;
        }
        ctx.locked.put(pos, result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    private static List<Rule> getRules() {
        if (rules != null) return rules;
        List<Rule> parsed = new ArrayList<>();
        String[] raw = ForgeConfigHandler.pipes.blockedContainers;
        if (raw != null) {
            for (String line : raw) {
                if (line == null || line.trim().isEmpty()) continue;
                try {
                    parsed.add(Rule.parse(line.trim()));
                } catch (Exception e) {
                    RusticPipes.LOGGER.warn("Ignoring invalid 'Blocked Containers' entry '{}': {}", line, e.getMessage());
                }
            }
        }
        rules = parsed;
        return parsed;
    }

    private static final class Rule {
        final Pattern idPattern;
        final List<NbtCheck> checks;
        final boolean pull, push;

        Rule(Pattern idPattern, List<NbtCheck> checks, boolean pull, boolean push) {
            this.idPattern = idPattern;
            this.checks = checks;
            this.pull = pull;
            this.push = push;
        }

        boolean appliesTo(Direction dir) {
            return dir == Direction.PULL ? pull : push;
        }

        static Rule parse(String line) {
            boolean pull = true, push = true;
            int at = line.lastIndexOf('@');
            if (at >= 0) {
                String d = line.substring(at + 1).trim().toLowerCase();
                line = line.substring(0, at).trim();
                switch (d) {
                    case "pull": push = false; break;
                    case "push": pull = false; break;
                    case "both": break;
                    default: throw new IllegalArgumentException("unknown direction '" + d + "'");
                }
            }

            String idPart = line;
            List<NbtCheck> checks = new ArrayList<>();
            int bar = line.indexOf('|');
            if (bar >= 0) {
                idPart = line.substring(0, bar).trim();
                for (String c : line.substring(bar + 1).split("&")) {
                    if (!c.trim().isEmpty()) checks.add(NbtCheck.parse(c.trim()));
                }
            }
            if (idPart.isEmpty()) throw new IllegalArgumentException("missing block id");
            if (!idPart.contains(":") && !idPart.equals("*")) idPart = "minecraft:" + idPart;
            return new Rule(glob(idPart), checks, pull, push);
        }

        private static Pattern glob(String s) {
            String[] parts = s.split("\\*", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(".*");
                if (!parts[i].isEmpty()) sb.append(Pattern.quote(parts[i]));
            }
            return Pattern.compile(sb.toString());
        }
    }

    private static final class NbtCheck {
        enum Op { EXISTS, MISSING, EQUALS, NOT_EQUALS }

        final String[] path;
        final Op op;
        final String value;

        NbtCheck(String[] path, Op op, @Nullable String value) {
            this.path = path;
            this.op = op;
            this.value = value;
        }

        static NbtCheck parse(String s) {
            int ne = s.indexOf("!=");
            if (ne > 0) return new NbtCheck(split(s.substring(0, ne)), Op.NOT_EQUALS, unquote(s.substring(ne + 2)));
            int eq = s.indexOf('=');
            if (eq > 0) return new NbtCheck(split(s.substring(0, eq)), Op.EQUALS, unquote(s.substring(eq + 1)));
            if (s.startsWith("!")) return new NbtCheck(split(s.substring(1)), Op.MISSING, null);
            return new NbtCheck(split(s), Op.EXISTS, null);
        }

        private static String[] split(String key) {
            key = key.trim();
            if (key.isEmpty()) throw new IllegalArgumentException("empty nbt key");
            return key.split("\\.");
        }

        private static String unquote(String v) {
            v = v.trim();
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            return v;
        }

        boolean test(NBTTagCompound root) {
            NBTBase tag = resolve(root);
            switch (op) {
                case EXISTS:     return tag != null;
                case MISSING:    return tag == null;
                case EQUALS:     return tag != null && asString(tag).equals(value);
                case NOT_EQUALS: return tag != null && !asString(tag).equals(value);
                default:         return false;
            }
        }

        @Nullable
        private NBTBase resolve(NBTTagCompound root) {
            NBTBase cur = root;
            for (String key : path) {
                if (!(cur instanceof NBTTagCompound)) return null;
                NBTTagCompound c = (NBTTagCompound) cur;
                if (!c.hasKey(key)) return null;
                cur = c.getTag(key);
            }
            return cur;
        }

        /** Strings compare by content; everything else by its NBT text form (1b, 5, 2.0f, ...). */
        private static String asString(NBTBase tag) {
            return tag instanceof NBTTagString ? ((NBTTagString) tag).getString() : tag.toString();
        }
    }
}
