package moe.takochan.neirecipetree.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.MouseEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.bom.TreeStorage;
import moe.takochan.neirecipetree.gui.GuiRecipeTree;
import moe.takochan.neirecipetree.gui.GuiTreeManager;

/**
 * Middle-click shortcut for reopening the active recipe tree.
 *
 * <p>
 * It works in the world and in any container screen. Screens owned by this mod are left alone, and
 * the event is only consumed when there actually is a tree to open, so middle-click keeps its
 * vanilla pick-block behaviour when no tree exists.
 */
public class TreeMouseHandler {

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!event.buttonstate || event.button != 2) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        GuiScreen current = mc.currentScreen;
        if (current instanceof GuiRecipeTree || current instanceof GuiTreeManager) {
            return;
        }
        if (current != null && !(current instanceof GuiContainer)) {
            return;
        }

        TreeStorage.load();
        if (BoM.tree == null) {
            return;
        }

        event.setCanceled(true);
        TreeStorage.setLastView(TreeStorage.View.TREE);
        mc.displayGuiScreen(new GuiRecipeTree(current));
    }
}
