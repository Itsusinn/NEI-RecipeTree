package moe.takochan.neirecipetree.bom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import codechicken.nei.FavoriteRecipes;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import moe.takochan.neirecipetree.recipe.FluidStacks;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;
import moe.takochan.neirecipetree.recipe.RecipeLookup;

public class BoM {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");

    /** Every tree opened during this session, in creation order. */
    public static final List<MaterialTree> trees = new ArrayList<>();
    /** The tree currently being browsed, {@code null} when no tree is selected. */
    public static MaterialTree tree;
    public static Map<ItemStackKey, NEIRecipeRef> defaultRecipes = new HashMap<>();
    public static Map<ItemStackKey, NEIRecipeRef> addedRecipes = new HashMap<>();
    public static Set<NEIRecipeRef> disabledRecipes = new HashSet<>();
    public static Set<ItemStackKey> userExpandedNodes = new HashSet<>();
    /** Tracks the selected recipe index per ingredient for cycling */
    public static Map<ItemStackKey, Integer> recipeIndices = new HashMap<>();
    public static boolean craftingMode = false;
    /** Set when user clicks a tree node to select a recipe from NEI. Cleared after selection. */
    public static ItemStack pendingResolution = null;

    /**
     * Adds a tree for {@code recipe} (or reuses the one already open for it) and selects it.
     */
    public static MaterialTree setGoal(NEIRecipeRef recipe) {
        // Make sure trees restored from disk are in the list before appending a new one
        TreeStorage.load();
        MaterialTree existing = findTreeFor(recipe);
        if (existing != null) {
            activate(existing);
            return existing;
        }
        MaterialTree created = new MaterialTree(recipe);
        trees.add(created);
        tree = created;
        craftingMode = false;
        TreeStorage.save();
        return created;
    }

    /**
     * Adds a tree rooted at {@code goal} even when another tree already targets the same item. The
     * inventory manager's "New" action uses this so pressing it never just switches to an older
     * tree with the same goal.
     */
    public static MaterialTree createTree(ItemStack goal) {
        TreeStorage.load();
        MaterialTree created = new MaterialTree(goal);
        trees.add(created);
        tree = created;
        craftingMode = false;
        TreeStorage.save();
        return created;
    }

    /**
     * Adds a tree rooted at {@code goal} (or reuses the one already open for that item) and selects
     * it.
     */
    public static MaterialTree setGoal(ItemStack goal) {
        // Make sure trees restored from disk are in the list before appending a new one
        TreeStorage.load();
        MaterialTree existing = findTreeFor(goal);
        if (existing != null) {
            activate(existing);
            return existing;
        }
        MaterialTree created = new MaterialTree(goal);
        trees.add(created);
        tree = created;
        craftingMode = false;
        TreeStorage.save();
        return created;
    }

    /** Makes {@code target} the tree the GUI works on. */
    public static void activate(MaterialTree target) {
        if (target == null || !trees.contains(target)) {
            return;
        }
        tree = target;
        craftingMode = false;
        TreeStorage.applyResolutions(target);
        recalculate();
        TreeStorage.save();
    }

    /** Drops {@code target} and selects a neighbouring tree when it was the active one. */
    public static void removeTree(MaterialTree target) {
        int index = trees.indexOf(target);
        if (index < 0) {
            return;
        }
        trees.remove(index);
        if (tree != target) {
            TreeStorage.save();
            return;
        }
        tree = null;
        if (!trees.isEmpty()) {
            activate(trees.get(Math.min(index, trees.size() - 1)));
        }
        TreeStorage.save();
    }

    private static MaterialTree findTreeFor(NEIRecipeRef recipe) {
        for (MaterialTree candidate : trees) {
            if (candidate.goal != null && recipe.equals(candidate.goal.recipe)) {
                return candidate;
            }
        }
        return null;
    }

    private static MaterialTree findTreeFor(ItemStack goal) {
        ItemStackKey goalKey = keyOf(goal);
        if (goalKey == null) {
            return null;
        }
        for (MaterialTree candidate : trees) {
            if (candidate.goal != null && goalKey.equals(keyOf(candidate.goal.ingredient))) {
                return candidate;
            }
        }
        return null;
    }

    /** Slot/item keys ignore fluid volumes so the same fluid still matches across recipes. */
    private static ItemStackKey keyOf(ItemStack stack) {
        return ItemStackKey.of(FluidStacks.aggregationKey(stack));
    }

    /**
     * Get the resolved recipe for a given item.
     * Priority: user overrides → favorites → single-recipe auto-resolve.
     * Items with multiple recipes and no favorite stay as leaves until user chooses.
     */
    public static NEIRecipeRef getRecipe(ItemStack stack) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return null;

        // 1. User explicit overrides (from tree interaction)
        NEIRecipeRef recipe = addedRecipes.get(key);
        if (recipe != null) return recipe;

        recipe = defaultRecipes.get(key);
        if (recipe != null && !disabledRecipes.contains(recipe)) {
            return recipe;
        }

        // 2. Check NEI favorite/bookmarked recipes
        NEIRecipeRef favoriteRecipe = findFavoriteRecipe(stack);
        if (favoriteRecipe != null) {
            defaultRecipes.put(key, favoriteRecipe);
            return favoriteRecipe;
        }

        // 3. Auto-resolve only if exactly 1 recipe exists (EMI behavior).
        // Multiple recipes → leave as leaf, user must choose.
        List<NEIRecipeRef> found = RecipeLookup.findRecipes(stack);
        if (found.size() == 1) {
            defaultRecipes.put(key, found.get(0));
            return found.get(0);
        }
        return null;
    }

    /**
     * Look up the NEI favorite recipe for a given item.
     * Returns null if no favorite exists or if it can't be resolved to a handler.
     */
    private static NEIRecipeRef findFavoriteRecipe(ItemStack stack) {
        try {
            RecipeId recipeId = FavoriteRecipes.getFavorite(stack);
            if (recipeId == null) return null;

            RecipeHandlerRef handlerRef = RecipeHandlerRef.of(recipeId);
            if (handlerRef != null) {
                return new NEIRecipeRef(handlerRef.handler, handlerRef.recipeIndex);
            }
        } catch (Exception e) {
            LOG.debug("Could not resolve favorite recipe for {}: {}", stack.getDisplayName(), e.getMessage());
        }
        return null;
    }

    public static void addRecipe(ItemStack stack, NEIRecipeRef recipe) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key != null) {
            disabledRecipes.remove(recipe);
            addedRecipes.put(key, recipe);
            recalculate();
        }
    }

    public static void removeRecipe(ItemStack stack, NEIRecipeRef recipe) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key != null) {
            addedRecipes.remove(key);
            disabledRecipes.add(recipe);
            recalculate();
        }
    }

    public static void addResolution(ItemStack stack, NEIRecipeRef recipe) {
        if (tree != null) {
            ItemStackKey key = ItemStackKey.of(stack);
            if (key != null) {
                userExpandedNodes.add(key);
            }
            tree.addResolution(stack, recipe);
        }
    }

    public static void clearResolution(ItemStack stack) {
        if (tree != null) {
            ItemStackKey key = ItemStackKey.of(stack);
            if (key != null) {
                tree.resolutions.put(key, null);
                addedRecipes.remove(key);
                defaultRecipes.remove(key);
                recipeIndices.remove(key);
                userExpandedNodes.remove(key);
                tree.recalculate();
                // Persist immediately: a save used to happen only when a tree was opened or the list
                // changed, so every choice made afterwards was lost on the next restart.
                TreeStorage.save();
            }
        }
    }

    public static void setResolution(ItemStack stack, NEIRecipeRef recipe) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (tree == null || key == null || recipe == null) {
            return;
        }

        if (tree.resolutions.containsKey(key) || addedRecipes.containsKey(key) || defaultRecipes.containsKey(key)) {
            // Same bookkeeping as clearResolution, but without its save: this method saves once at
            // the end with the new recipe already in place.
            tree.resolutions.put(key, null);
            addedRecipes.remove(key);
            defaultRecipes.remove(key);
            recipeIndices.remove(key);
            userExpandedNodes.remove(key);
        }

        disabledRecipes.remove(recipe);
        addResolution(stack, recipe);
        addedRecipes.put(key, recipe);
        TreeStorage.save();
    }

    /**
     * Cycle to the next handler type's first recipe for this ingredient.
     * Skips to the first recipe of the next different handler, so user cycles
     * through machine types (Furnace → Assembler → ...) instead of individual recipes.
     * Returns true if a new recipe was selected.
     */
    public static boolean cycleRecipe(ItemStack stack) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return false;

        List<NEIRecipeRef> recipes = RecipeLookup.findRecipes(stack);
        if (recipes.size() <= 1) return false;

        int currentIndex = recipeIndices.getOrDefault(key, 0);
        String currentHandlerId = recipes.get(currentIndex % recipes.size()).handler.getHandlerId();

        // Find the first recipe from a different handler type
        int nextIndex = (currentIndex + 1) % recipes.size();
        while (nextIndex != currentIndex) {
            if (!recipes.get(nextIndex).handler.getHandlerId()
                .equals(currentHandlerId)) {
                break;
            }
            nextIndex = (nextIndex + 1) % recipes.size();
        }

        recipeIndices.put(key, nextIndex);

        NEIRecipeRef nextRecipe = recipes.get(nextIndex);
        setResolution(stack, nextRecipe);
        return true;
    }

    /**
     * Get the current recipe index and total count for display.
     */
    public static int getRecipeIndex(ItemStack stack) {
        ItemStackKey key = ItemStackKey.of(stack);
        return key != null ? recipeIndices.getOrDefault(key, 0) : 0;
    }

    private static void recalculate() {
        if (tree != null) {
            tree.recalculate();
        }
    }

    /**
     * Resolve the pending ingredient with the given recipe, then return to tree view.
     */
    public static void resolveAndReturn(NEIRecipeRef recipe) {
        if (pendingResolution != null && tree != null) {
            setResolution(pendingResolution, recipe);
            pendingResolution = null;
            Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.displayGuiScreen(new moe.takochan.neirecipetree.gui.GuiRecipeTree(null));
        }
    }

    public static void clear() {
        trees.clear();
        tree = null;
        defaultRecipes.clear();
        addedRecipes.clear();
        disabledRecipes.clear();
        userExpandedNodes.clear();
        recipeIndices.clear();
        craftingMode = false;
        pendingResolution = null;
        RecipeLookup.clearCache();
        TreeStorage.save();
    }
}
