package moe.takochan.neirecipetree.nei;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Puts {@link InventoryTreeButton} on the player inventory screen so the current recipe tree can be
 * reopened without going through an NEI recipe page first.
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
        GuiScreen gui = event.gui;
        int guiLeft = (gui.width - GUI_WIDTH) / 2;
        int guiTop = (gui.height - GUI_HEIGHT) / 2;
        button = new InventoryTreeButton(BUTTON_ID, guiLeft - InventoryTreeButton.SIZE - 4, guiTop + 4);
        event.buttonList.add(button);
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (button == null || !(event.gui instanceof GuiInventory)) {
            return;
        }
        if (!button.shouldShow() || !button.isMouseOver(event.mouseX, event.mouseY)) {
            return;
        }
        button.drawToolTip(event.mouseX, event.mouseY);
    }
}
