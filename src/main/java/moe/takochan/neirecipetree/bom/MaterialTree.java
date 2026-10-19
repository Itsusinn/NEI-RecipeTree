package moe.takochan.neirecipetree.bom;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import moe.takochan.neirecipetree.recipe.FluidStacks;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;

public class MaterialTree {

    public MaterialNode goal;
    public TreeCost cost = new TreeCost();
    public Map<ItemStackKey, NEIRecipeRef> resolutions = new HashMap<>();
    /** Materials the player removed from this tree; they stay out of the layout and total cost. */
    public Set<ItemStackKey> removedMaterials = new HashSet<>();
    public long batches = 1;

    public MaterialTree(NEIRecipeRef recipe) {
        ItemStack output = recipe.getOutput();
        if (output == null) {
            // Fallback: try to get output from other stacks
            java.util.List<ItemStack> allOutputs = recipe.getAllOutputs();
            if (!allOutputs.isEmpty()) {
                output = allOutputs.get(0);
            }
        }
        if (output == null || output.getItem() == null) {
            throw new IllegalArgumentException("Recipe has no valid output");
        }
        goal = new MaterialNode(output);
        goal.defineRecipe(recipe);
        // Store root recipe in resolutions so recalculate uses the same reference.
        ItemStackKey key = ItemStackKey.of(output);
        if (key != null) {
            resolutions.put(key, recipe);
        }
        recalculate();
    }

    /**
     * Creates a tree rooted at {@code goalStack} without a recipe yet. The tree resolves one through
     * {@link BoM#getRecipe(ItemStack)} while recalculating, and stays a plain leaf when the item has
     * several candidate recipes, so the player picks one from the tree as usual.
     */
    public MaterialTree(ItemStack goalStack) {
        this(goalStack, true);
    }

    /**
     * @param calculate whether to resolve the recipe chain immediately. Restored trees pass
     *                  {@code false} and are calculated when the player opens them.
     */
    public MaterialTree(ItemStack goalStack, boolean calculate) {
        ItemStack goalItem = goalStack.copy();
        if (goalItem.getItem() == null) {
            throw new IllegalArgumentException("Goal stack has no item");
        }
        goal = new MaterialNode(goalItem);
        if (calculate) {
            recalculate();
        }
    }

    public NEIRecipeRef getRecipe(ItemStack stack) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key != null && resolutions.containsKey(key)) {
            return resolutions.get(key);
        }
        return BoM.getRecipe(stack);
    }

    public void addResolution(ItemStack stack, NEIRecipeRef recipe) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key != null) {
            resolutions.put(key, recipe);
            goal.applyResolution(key, recipe);
            recalculate();
        }
    }

    public void recalculate() {
        goal.recalculate(this);
        pruneRemoved(goal);
    }

    /**
     * Removes {@code node} from this tree. The material is remembered by item key, so every
     * occurrence of it disappears and it is left out of the total cost.
     *
     * @return {@code true} when the material was not already removed
     */
    public boolean removeMaterial(MaterialNode node) {
        if (node == null || node == goal) {
            return false;
        }
        ItemStackKey key = materialKey(node.ingredient);
        if (key == null || !removedMaterials.add(key)) {
            return false;
        }
        recalculate();
        return true;
    }

    private void pruneRemoved(MaterialNode node) {
        if (node.children == null) {
            return;
        }
        node.children.removeIf(child -> {
            ItemStackKey key = materialKey(child.ingredient);
            return key != null && removedMaterials.contains(key);
        });
        for (MaterialNode child : node.children) {
            pruneRemoved(child);
        }
    }

    /** Fluid volumes are ignored so removing one fluid also hides the same fluid from other recipes. */
    private static ItemStackKey materialKey(ItemStack stack) {
        return ItemStackKey.of(FluidStacks.aggregationKey(stack));
    }

    public void calculateCost() {
        cost.calculate(goal, batches);
    }

    public void calculateProgress(Map<ItemStackKey, Long> inventory) {
        cost.calculateProgress(goal, batches, inventory);
    }
}
