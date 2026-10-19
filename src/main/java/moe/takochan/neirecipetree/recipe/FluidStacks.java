package moe.takochan.neirecipetree.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import codechicken.nei.recipe.StackInfo;

/**
 * Fluid handling for recipe stacks.
 *
 * <p>
 * NEI represents a fluid ingredient or output as a display item whose volume lives in the NBT
 * (GT writes {@code mFluidDisplayAmount}, AE2FC keeps a nested {@code FluidStack} with an
 * {@code Amount}). The item itself is always a single unit, so anything that adds up material
 * requirements has to read the volume through {@link StackInfo#getFluid(ItemStack)} and must not
 * key entries by the raw NBT: otherwise the same fluid requested by two recipes ends up as two
 * separate material cost rows.
 */
public final class FluidStacks {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    /** Tag GT's {@code ItemFluidDisplay} uses for the volume in mB. */
    private static final String GT_AMOUNT_TAG = "mFluidDisplayAmount";
    /** Compound AE2FC fluid packets and fluid drops keep their {@link FluidStack} in. */
    private static final String AE2_FLUID_TAG = "FluidStack";

    private FluidStacks() {}

    /**
     * Returns the fluid carried by {@code stack}, or {@code null} for ordinary items.
     */
    public static FluidStack fluidOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        try {
            FluidStack fluid = StackInfo.getFluid(stack);
            if (fluid == null || fluid.getFluid() == null) {
                return null;
            }
            // Buckets and cells are real items whose *contents* are a fluid; summing them as fluid
            // volume would replace "1 bucket" with "1000L"
            return StackInfo.isFluidContainer(stack) ? null : fluid;
        } catch (Exception e) {
            LOG.debug("Could not read a fluid from {}: {}", stack, e.toString());
            return null;
        }
    }

    public static boolean isFluid(ItemStack stack) {
        return fluidOf(stack) != null;
    }

    /**
     * Amount represented by a single unit of {@code stack}: millibuckets for fluids, the stack size
     * for ordinary items.
     */
    public static long unitAmount(ItemStack stack) {
        if (stack == null) {
            return 1;
        }
        FluidStack fluid = fluidOf(stack);
        if (fluid == null) {
            return Math.max(1, stack.stackSize);
        }
        int volume = fluid.amount;
        if (volume <= 0) {
            // Some handlers hide the volume; counting display units is the best we can do then
            return Math.max(1, stack.stackSize);
        }
        return stack.stackSize > 1 ? Math.max(1, volume / stack.stackSize) : volume;
    }

    /**
     * Copy of {@code stack} with every volume-bearing tag removed, so keys built from it aggregate
     * all amounts of the same fluid into a single entry.
     */
    public static ItemStack aggregationKey(ItemStack stack) {
        if (!isFluid(stack)) {
            return stack;
        }
        ItemStack key = stack.copy();
        key.stackSize = 1;
        NBTTagCompound tag = key.getTagCompound();
        if (tag == null) {
            return key;
        }
        tag.removeTag(GT_AMOUNT_TAG);
        if (tag.hasKey(AE2_FLUID_TAG, 10)) {
            tag.getCompoundTag(AE2_FLUID_TAG)
                .removeTag("Amount");
        }
        return key;
    }
}
