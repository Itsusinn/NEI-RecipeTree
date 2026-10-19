package moe.takochan.neirecipetree.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.bom.TreeStorage;
import moe.takochan.neirecipetree.gui.GuiRecipeTree;

/**
 * Configurable world-only shortcut for reopening the active recipe tree.
 *
 * <p>
 * The default is the middle mouse button. Container screens are deliberately ignored so the
 * shortcut cannot steal a middle-click sort action; the binding can be changed in the vanilla
 * controls screen.
 */
public class TreeKeyHandler {

    /** -98 is vanilla's key code for mouse button 2 (middle click). */
    public static final KeyBinding OPEN_TREE = new KeyBinding(
        "key.neirecipetree.open_tree",
        -98,
        "key.categories.neirecipetree");

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean pressed = OPEN_TREE.isPressed();
        Minecraft mc = Minecraft.getMinecraft();
        if (!pressed || mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }
        openTree(mc);
    }

    /**
     * Mouse-bound keys also arrive as a vanilla {@link MouseEvent}. Handling that event first keeps
     * the shortcut from also triggering pick-block when it is bound to the middle button; a
     * keyboard binding has a non-negative key code and is left to the tick handler.
     */
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        int keyCode = OPEN_TREE.getKeyCode();
        if (keyCode >= 0 || !event.buttonstate || event.button != keyCode + 100) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }
        if (openTree(mc)) {
            event.setCanceled(true);
        }
    }

    private boolean openTree(Minecraft mc) {
        TreeStorage.load();
        if (BoM.tree == null) {
            return false;
        }
        TreeStorage.setLastView(TreeStorage.View.TREE);
        mc.displayGuiScreen(new GuiRecipeTree(null));
        return true;
    }
}
