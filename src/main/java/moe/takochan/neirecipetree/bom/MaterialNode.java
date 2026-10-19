package moe.takochan.neirecipetree.bom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import codechicken.nei.PositionedStack;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;
import moe.takochan.neirecipetree.recipe.RecipeAdapter;
import moe.takochan.neirecipetree.recipe.RecipeLookup;

public class MaterialNode {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final int MAX_DEPTH = 64;
    /** How many alternatives of a single slot are probed before giving up on finding a usable one. */
    private static final int MAX_ALTERNATIVE_PROBES = 32;

    /** The alternative of {@link #permutations} this node currently represents. */
    public ItemStack ingredient;
    /**
     * Every item NEI reported for this ingredient slot. A recipe slot may be satisfied by any of
     * them, so the tree must not hard-code the first entry: it is not necessarily the one that can
     * actually be decomposed.
     */
    public final ItemStack[] permutations;
    /** Index of the selected alternative inside {@link #permutations}. */
    public int permutationIndex = 0;
    public ItemStack remainder;
    public NEIRecipeRef recipe;
    public List<MaterialNode> children;
    public float consumeChance = 1;
    public float produceChance = 1;
    public long amount = 1;
    public long divisor = 1;
    public long remainderAmount = 0;
    public boolean catalyst = false;
    public FoldState state = FoldState.EXPANDED;
    public ProgressState progress = ProgressState.UNSTARTED;
    public long neededBatches = 0;
    public long totalNeeded = 0;

    /** Stack size of the selected alternative, used to keep {@link #amount} in sync when switching. */
    private long baseAmount = 1;
    /** Set when no alternative of this slot could be decomposed, to avoid repeating the lookup. */
    private boolean alternativeProbeFailed = false;

    public MaterialNode(ItemStack stack) {
        this.permutations = new ItemStack[] { stack.copy() };
        this.baseAmount = permutations[0].stackSize;
        this.amount = this.baseAmount;
        applyPermutation(permutations[0]);
    }

    public MaterialNode(PositionedStack ps) {
        this.permutations = RecipeAdapter.getPermutations(ps);
        if (this.permutations.length == 0) {
            throw new IllegalArgumentException("PositionedStack contains no items");
        }
        this.baseAmount = permutations[0].stackSize;
        this.amount = this.baseAmount;
        applyPermutation(permutations[0]);
    }

    public MaterialNode(MaterialNode node) {
        this.ingredient = node.ingredient;
        this.permutations = node.permutations;
        this.permutationIndex = node.permutationIndex;
        this.remainder = node.remainder;
        this.recipe = node.recipe;
        this.amount = node.amount;
        this.divisor = node.divisor;
        this.remainderAmount = node.remainderAmount;
        this.baseAmount = node.baseAmount;
    }

    public boolean hasAlternatives() {
        return permutations != null && permutations.length > 1;
    }

    /**
     * Switches this node to the {@code index}-th alternative of its ingredient slot.
     *
     * <p>
     * The old expansion is dropped on purpose: a recipe that produced the previous item must never
     * stay attached to the new one, so the tree has to be recalculated by the caller afterwards.
     *
     * @return {@code true} when the selection actually changed
     */
    public boolean selectPermutation(int index) {
        if (permutations == null || index < 0 || index >= permutations.length || index == permutationIndex) {
            return false;
        }
        ItemStack selected = permutations[index];
        if (selected == null || selected.getItem() == null) {
            return false;
        }
        this.permutationIndex = index;
        applyPermutation(selected);

        this.recipe = null;
        this.children = null;
        this.divisor = 1;
        this.alternativeProbeFailed = false;
        return true;
    }

    /**
     * Switches to the next usable alternative, wrapping around. Returns {@code false} when there is
     * nothing else to switch to.
     */
    public boolean cyclePermutation() {
        return cyclePermutation(false);
    }

    /**
     * Switches to the next usable alternative, skipping entries that cannot be represented.
     *
     * @param backwards walk the alternatives in reverse order
     * @return {@code false} when there is nothing else to switch to
     */
    public boolean cyclePermutation(boolean backwards) {
        if (!hasAlternatives()) {
            return false;
        }
        int size = permutations.length;
        for (int step = 1; step < size; step++) {
            int index = Math.floorMod(backwards ? permutationIndex - step : permutationIndex + step, size);
            if (selectPermutation(index)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies an alternative to this node. {@link #amount} and {@link #remainderAmount} may carry
     * merged duplicate inputs, so they are adjusted by the delta instead of being overwritten.
     */
    private void applyPermutation(ItemStack selected) {
        long previousBase = this.baseAmount;
        long previousRemainder = this.remainder != null ? this.remainder.stackSize : 0;

        this.ingredient = selected.copy();
        this.ingredient.stackSize = 1;
        this.baseAmount = selected.stackSize;
        this.amount += this.baseAmount - previousBase;

        ItemStack newRemainder = RecipeAdapter.getContainerItem(selected);
        this.remainder = newRemainder;
        if (newRemainder != null) {
            this.remainderAmount += newRemainder.stackSize - previousRemainder;
            if (this.remainderAmount < 1) {
                this.remainderAmount = newRemainder.stackSize;
            }
            newRemainder.stackSize = 1;
        } else {
            this.remainderAmount = 0;
        }

        this.catalyst = RecipeAdapter.isCatalyst(selected);
        // Catalysts default to collapsed since they are not consumed
        if (this.catalyst) {
            this.state = FoldState.COLLAPSED;
        }
    }

    public void recalculate(MaterialTree tree) {
        // Use IdentityHashMap as a set — comparing recipe objects by reference (==), not equals().
        // This matches EMI's approach: each recipe object is unique, so reference equality avoids
        // false cycle detection when different items happen to share the same handler class + index.
        recalculate(tree, new IdentityHashMap<>(), 0);
    }

    private void recalculate(MaterialTree tree, IdentityHashMap<NEIRecipeRef, Boolean> used, int depth) {
        if (depth > MAX_DEPTH) {
            LOG.warn("Max recursion depth reached for {}", ingredient.getDisplayName());
            return;
        }

        // Check if this ingredient was explicitly cleared (null in resolutions map)
        ItemStackKey key = ItemStackKey.of(ingredient);
        if (key != null && tree.resolutions.containsKey(key) && tree.resolutions.get(key) == null) {
            // Explicitly cleared by user — revert to leaf node
            this.recipe = null;
            this.children = null;
            return;
        }

        // Determine which recipe to use: tree resolution takes priority
        NEIRecipeRef resolvedRecipe = tree.getRecipe(ingredient);
        if (resolvedRecipe == null) {
            resolvedRecipe = this.recipe; // Keep existing if tree has none
        }

        // A slot may offer several substitutable items and the first one is not necessarily
        // craftable. Try another alternative before giving up on the node entirely.
        if (resolvedRecipe == null && shouldProbeAlternatives() && switchToDecomposableAlternative()) {
            key = ItemStackKey.of(ingredient);
            resolvedRecipe = tree.getRecipe(ingredient);
        }

        if (resolvedRecipe == null) return; // No recipe available, leaf node

        if (key != null && BoM.userExpandedNodes.contains(key)) {
            this.state = FoldState.EXPANDED;
        }

        // Cycle detection: compare by object reference (identity), like EMI
        if (used.containsKey(resolvedRecipe)) {
            return;
        }
        used.put(resolvedRecipe, Boolean.TRUE);

        // Pin this recipe in tree resolutions so the same reference is used on subsequent recalculates.
        if (key != null && !tree.resolutions.containsKey(key)) {
            tree.resolutions.put(key, resolvedRecipe);
        }

        // Only rebuild children if recipe actually changed or hasn't been built yet
        if (this.children == null || this.recipe == null
            || (this.recipe != resolvedRecipe && !this.recipe.equals(resolvedRecipe))) {
            defineRecipe(resolvedRecipe);
        }

        if (children != null) {
            for (MaterialNode node : children) {
                if (!node.catalyst) {
                    node.recalculate(tree, used, depth + 1);
                }
            }
        }
        used.remove(resolvedRecipe); // Backtrack — same recipe can appear in other branches
    }

    private boolean shouldProbeAlternatives() {
        if (!hasAlternatives() || alternativeProbeFailed) {
            return false;
        }
        // Only a slot whose selected item cannot be crafted at all is worth re-targeting; when it
        // has recipes, leaving the choice to the user is the intended behaviour.
        return RecipeLookup.getRecipeCount(ingredient) == 0;
    }

    /**
     * Switches to the first alternative that has at least one recipe, so a slot whose primary item
     * is not craftable can still be decomposed.
     *
     * @return {@code true} when the selection changed
     */
    private boolean switchToDecomposableAlternative() {
        int probes = 0;
        for (int i = 0; i < permutations.length && probes < MAX_ALTERNATIVE_PROBES; i++) {
            if (i == permutationIndex) continue;
            ItemStack candidate = permutations[i];
            if (candidate == null || candidate.getItem() == null) continue;
            probes++;
            if (RecipeLookup.getRecipeCount(candidate) > 0) {
                LOG.debug(
                    "MaterialNode: {} has no recipe, substituting {}",
                    ingredient.getDisplayName(),
                    candidate.getDisplayName());
                return selectPermutation(i);
            }
        }
        alternativeProbeFailed = true;
        return false;
    }

    public void defineRecipe(NEIRecipeRef recipeRef) {
        produceChance = 1;
        if (recipeRef == null) return;

        // If the same recipe is already set and children exist, preserve them (keeps fold state)
        if (this.recipe != null && (this.recipe == recipeRef || this.recipe.equals(recipeRef))
            && this.children != null) {
            return;
        }

        // Save existing children state before recreating. Keyed by the slot's own primary item so
        // the state survives even when the node currently shows another alternative.
        Map<ItemStackKey, FoldState> savedFoldStates = new HashMap<>();
        Map<ItemStackKey, Integer> savedPermutationIndices = new HashMap<>();
        if (this.children != null) {
            for (MaterialNode child : children) {
                ItemStackKey key = ItemStackKey.of(child.permutations[0]);
                if (key != null) {
                    savedFoldStates.put(key, child.state);
                    savedPermutationIndices.put(key, child.permutationIndex);
                }
            }
        }

        this.recipe = recipeRef;
        updateDivisor();

        // Build children from recipe inputs
        this.children = new ArrayList<>();
        List<PositionedStack> inputs = recipeRef.getInputs();
        if (inputs != null) {
            outer: for (PositionedStack inputPs : inputs) {
                if (inputPs == null || inputPs.items.length == 0) continue;

                ItemStack inputStack = inputPs.items[0];
                if (inputStack == null || inputStack.getItem() == null) continue;

                // Merge duplicate inputs, comparing the slot primary so a node showing another
                // alternative still merges with its sibling
                for (MaterialNode existing : children) {
                    if (existing.permutations.length > 0
                        && ItemStackKey.matches(inputStack, existing.permutations[0])) {
                        existing.amount += inputStack.stackSize;
                        ItemStack existingRemainder = RecipeAdapter.getContainerItem(inputStack);
                        if (existingRemainder != null) {
                            existing.remainderAmount += existingRemainder.stackSize;
                        }
                        continue outer;
                    }
                }

                MaterialNode child = new MaterialNode(inputPs);
                // Restore fold state and alternative selection from previous children
                ItemStackKey childKey = ItemStackKey.of(child.permutations[0]);
                if (childKey != null) {
                    if (savedFoldStates.containsKey(childKey)) {
                        child.state = savedFoldStates.get(childKey);
                    }
                    Integer savedIndex = savedPermutationIndices.get(childKey);
                    if (savedIndex != null) {
                        child.selectPermutation(savedIndex);
                    }
                }
                children.add(child);
            }
        }
    }

    /**
     * Recomputes how many items one craft of {@link #recipe} yields of the currently selected
     * alternative.
     */
    private void updateDivisor() {
        divisor = 1;
        if (recipe == null) {
            return;
        }

        divisor = 0;

        // Calculate divisor from recipe output
        PositionedStack outputPs = recipe.getOutputPositioned();
        if (outputPs != null) {
            for (ItemStack outputItem : outputPs.items) {
                if (ItemStackKey.matches(outputItem, ingredient)) {
                    divisor += outputItem.stackSize;
                    break;
                }
            }
        }

        // Also check other outputs
        List<PositionedStack> otherOutputs = recipe.getOtherOutputs();
        if (otherOutputs != null) {
            for (PositionedStack ps : otherOutputs) {
                if (ps != null && ps.items.length > 0 && ItemStackKey.matches(ps.items[0], ingredient)) {
                    divisor += ps.items[0].stackSize;
                }
            }
        }

        if (divisor <= 0) {
            divisor = 1;
        }
    }

    public void applyResolution(ItemStackKey targetKey, NEIRecipeRef recipeRef) {
        ItemStackKey selfKey = ItemStackKey.of(ingredient);
        if (selfKey != null && selfKey.equals(targetKey)) {
            defineRecipe(recipeRef);
            state = FoldState.EXPANDED;
        }

        if (children != null) {
            for (MaterialNode child : children) {
                child.applyResolution(targetKey, recipeRef);
            }
        }
    }

    public void pruneUnusedAutoResolutions(MaterialTree tree, boolean isRoot) {
        ItemStackKey key = ItemStackKey.of(ingredient);
        boolean manuallySelected = key != null && BoM.addedRecipes.containsKey(key);

        if (!isRoot && totalNeeded <= 0 && !manuallySelected) {
            recipe = null;
            children = null;
            divisor = 1;
            state = FoldState.COLLAPSED;
            if (key != null) {
                BoM.userExpandedNodes.remove(key);
            }
            return;
        }

        if (children != null) {
            for (MaterialNode child : children) {
                child.pruneUnusedAutoResolutions(tree, false);
            }
        }
    }
}
