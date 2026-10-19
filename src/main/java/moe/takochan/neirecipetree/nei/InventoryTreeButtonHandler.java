package moe.takochan.neirecipetree.nei;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moe.takochan.neirecipetree.bom.TreeStorage;
import moe.takochan.neirecipetree.gui.GuiTreeManager;

/**
 * Puts {@link InventoryTreeButton} on the player inventory screen so the recipe tree list is always
 * one click away, and remembers which item the player hovers there as the default goal for a new
 * tree.
 */
public class InventoryTreeButtonHandler {

    /** GuiInventory is a plain 176x166 container; its guiLeft/guiTop are not accessible from here. */
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    /** Far away from the small ids vanilla screens use. */
    private static final int BUTTON_ID = 0x54524545;

    private InventoryTreeButton button;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiInventory)) {
            return;
        }
        // Restore the saved list here rather than at mod init: by the time the player opens the
        // inventory the server's item id mapping is final, so stored registry names resolve to the
        // same items that were saved
        TreeStorage.load();
        GuiScreen gui = event.gui;
        int guiLeft = (gui.width - GUI_WIDTH) / 2;
        int guiTop = (gui.height - GUI_HEIGHT) / 2;
        // Bottom-left of the inventory panel, outside the hotbar row
        int buttonX = guiLeft - InventoryTreeButton.SIZE - 4;
        int buttonY = guiTop + GUI_HEIGHT - InventoryTreeButton.SIZE - 4;
        button = new InventoryTreeButton(BUTTON_ID, buttonX, buttonY);
        event.buttonList.add(button);
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiInventory)) {
            return;
        }
        GuiInventory inventory = (GuiInventory) event.gui;
        rememberHoveredItem(inventory, event.mouseX, event.mouseY);

        if (button == null || !button.shouldShow() || !button.isMouseOver(event.mouseX, event.mouseY)) {
            return;
        }
        button.drawToolTip(event.mouseX, event.mouseY);
    }

    /**
     * Keeps the last hovered inventory item around: the player usually points at an item and then
     * moves to the button, and that item is what the tree manager offers for a new tree.
     */
    @SuppressWarnings("unchecked")
    private void rememberHoveredItem(GuiContainer gui, int mouseX, int mouseY) {
        int guiLeft = (gui.width - GUI_WIDTH) / 2;
        int guiTop = (gui.height - GUI_HEIGHT) / 2;
        List<Slot> slots = gui.inventorySlots.inventorySlots;
        for (Slot slot : slots) {
            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;
            if (mouseX < x - 1 || mouseX >= x + 17 || mouseY < y - 1 || mouseY >= y + 17) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack != null && stack.getItem() != null) {
                GuiTreeManager.hoveredInventoryGoal = stack.copy();
            }
            return;
        }
    }
}
