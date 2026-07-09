package rusticpipes.util;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.ShapedOreRecipe;
import rusticpipes.RusticPipes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the RusticPipes recipe string format into a {@link ShapedOreRecipe}.
 *
 * Format:
 *   {[slot1],[slot2],[slot3],[slot4],[slot5],[slot6],[slot7],[slot8],[slot9]} * count
 *
 * Each slot is either:
 *   - [minecraft:air] or []          → empty slot
 *   - [ore:oreName]                  → ore dictionary entry  (e.g. [ore:ingotIron])
 *   - [modid:itemname]               → direct item registry  (e.g. [minecraft:iron_ingot])
 *   - [modid:itemname:meta]          → item with metadata    (e.g. [minecraft:wool:14])
 *
 * Exactly 9 slots must be present. Rows are 1-3, 4-6, 7-9.
 * If the recipe string is malformed, logs a warning and returns null.
 */
public final class RecipeParser {

    private RecipeParser() {}

    /**
     * Parses {@code raw} and returns a {@link ShapedOreRecipe}, or null on failure.
     *
     * @param raw        the full recipe string, e.g. "{[ingotIron],[],[...]} * 4"
     * @param registryId the registry name to assign to the recipe (modid:name)
     * @param output     the output ItemStack (count is overridden by the parsed * N value)
     */
    @Nullable
    public static ShapedOreRecipe parse(String raw, ResourceLocation registryId, ItemStack output) {
        if (raw == null || raw.isEmpty()) return null;

        try {
            // ── Split on " * " to get grid and count ────────────────────────
            int starIdx = raw.lastIndexOf('*');
            int count = 1;
            String gridPart = raw;
            if (starIdx >= 0) {
                String countStr = raw.substring(starIdx + 1).trim();
                try { count = Math.max(1, Integer.parseInt(countStr)); }
                catch (NumberFormatException e) {
                    if (RusticPipes.DEBUG) {
                        RusticPipes.LOGGER.warn("[RusticPipes] Recipe '{}': invalid count '{}', defaulting to 1.",
                                registryId, countStr);
                    }
                }
                gridPart = raw.substring(0, starIdx).trim();
            }

            // ── Strip outer braces ───────────────────────────────────────────
            gridPart = gridPart.trim();
            if (gridPart.startsWith("{")) gridPart = gridPart.substring(1);
            if (gridPart.endsWith("}"))   gridPart = gridPart.substring(0, gridPart.length() - 1);

            // ── Extract slot tokens between [ ] ─────────────────────────────
            List<String> slots = new ArrayList<>();
            int i = 0;
            while (i < gridPart.length()) {
                int open = gridPart.indexOf('[', i);
                if (open < 0) break;
                int close = gridPart.indexOf(']', open);
                if (close < 0) break;
                slots.add(gridPart.substring(open + 1, close).trim());
                i = close + 1;
            }

            if (slots.size() != 9) {
                if (RusticPipes.DEBUG) {
                    RusticPipes.LOGGER.warn("[RusticPipes] Recipe '{}': expected 9 slots, got {}. Skipping.",
                            registryId, slots.size());
                }
                return null;
            }

            // ── Build the ingredient array ───────────────────────────────────
            // ShapedOreRecipe takes: pattern rows + char→ingredient pairs.
            // We use chars A-I for the 9 positions.
            // Empty slots use ' ' (space), which ShapedOreRecipe treats as air.
            char[] chars = {'A','B','C','D','E','F','G','H','I'};
            Object[] ingredients = new Object[slots.size()];
            char[] grid = new char[9];

            for (int s = 0; s < 9; s++) {
                String slot = slots.get(s);
                if (isEmpty(slot)) {
                    grid[s] = ' ';
                    ingredients[s] = null; // not used for spaces
                } else {
                    grid[s] = chars[s];
                    ingredients[s] = resolveIngredient(slot, registryId);
                    if (ingredients[s] == null) {
                        if (RusticPipes.DEBUG) {
                            RusticPipes.LOGGER.warn("[RusticPipes] Recipe '{}': slot {} '{}' could not be resolved. Skipping recipe.",
                                    registryId, s, slot);
                        }
                        return null;
                    }
                }
            }

            // ── Build pattern rows ───────────────────────────────────────────
            String row1 = "" + grid[0] + grid[1] + grid[2];
            String row2 = "" + grid[3] + grid[4] + grid[5];
            String row3 = "" + grid[6] + grid[7] + grid[8];

            // ── Assemble ShapedOreRecipe args ────────────────────────────────
            // Format: output, row1, row2, row3, 'C', ingredient, ...
            List<Object> args = new ArrayList<>();
            args.add(row1);
            args.add(row2);
            args.add(row3);
            for (int s = 0; s < 9; s++) {
                if (grid[s] != ' ') {
                    args.add(chars[s]);
                    args.add(ingredients[s]);
                }
            }

            ItemStack outputStack = output.copy();
            outputStack.setCount(count);

            ShapedOreRecipe recipe = new ShapedOreRecipe(null, outputStack, args.toArray());
            recipe.setRegistryName(registryId);
            return recipe;

        } catch (Exception e) {
            if (RusticPipes.DEBUG) {
                RusticPipes.LOGGER.warn("[RusticPipes] Recipe '{}': parse error — {}. Skipping.",
                        registryId, e.getMessage());
            }
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static boolean isEmpty(String slot) {
        return slot.isEmpty()
                || slot.equals("minecraft:air")
                || slot.equals("air");
    }

    /**
     * Resolves a slot string to either an ore-dict name (String) or an ItemStack.
     * Returns null if the item cannot be found.
     */
    @Nullable
    private static Object resolveIngredient(String slot, ResourceLocation recipeId) {
        // Ore dictionary: prefix "ore:"
        if (slot.startsWith("ore:")) {
            return slot.substring(4);
        }

        // Direct item: modid:itemname or modid:itemname:meta
        String[] parts = slot.split(":");
        if (parts.length < 2) {
            if (RusticPipes.DEBUG) {
                RusticPipes.LOGGER.warn("[RusticPipes] Recipe '{}': cannot parse slot '{}'.", recipeId, slot);
            }
            return null;
        }

        String modid = parts[0];
        String itemName = parts[1];
        int meta = 0;
        if (parts.length >= 3) {
            try { meta = Integer.parseInt(parts[2]); }
            catch (NumberFormatException e) {
                if (RusticPipes.DEBUG) {
                RusticPipes.LOGGER.warn("[RusticPipes] Recipe '{}': invalid meta in slot '{}'.", recipeId, slot);
                }
            }
        }

        ResourceLocation rl = new ResourceLocation(modid, itemName);
        net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null) {
            if (RusticPipes.DEBUG) {
                RusticPipes.LOGGER.warn("[RusticPipes] Recipe '{}': item '{}' not found in registry.", recipeId, rl);
            }
            return null;
        }
        return new ItemStack(item, 1, meta);
    }
}
