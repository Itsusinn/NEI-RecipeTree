package moe.takochan.neirecipetree.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.bom.MaterialTree;
import moe.takochan.neirecipetree.gui.GuiTreeManager;
import moe.takochan.neirecipetree.gui.TreeRenderer;

/**
 * Button added to the player inventory that opens the recipe tree list.
 *
 * <p>
 * Its icon is the goal of the selected tree, so it also tells which tree is active. Nothing is drawn
 * while no tree has been opened yet.
 */
public class InventoryTreeButton extends GuiButton {

    public static final int SIZE = 20;

    public InventoryTreeButton(int id, int x, int y) {
        super(id, x, y, SIZE, SIZE, "");
    }

    /**
     * Only shown once at least one tree exists: there is nothing to manage before that.
     */
    public boolean shouldShow() {
        return !BoM.trees.isEmpty();
    }

    private ItemStack activeGoal() {
        MaterialTree active = BoM.tree;
        if (active == null || active.goal == null) {
            return null;
        }
        ItemStack goal = active.goal.ingredient;
        return goal != null && goal.getItem() != null ? goal : null;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!shouldShow()) {
            return;
        }
        boolean hovered = isMouseOver(mouseX, mouseY);
        int background = hovered ? 0xFF3B3B3B : 0xFF2A2A2A;
        int border = hovered ? 0xFFF0F0F0 : 0xFF8B8B8B;
        GuiDraw.drawRect(xPosition, yPosition, width, height, background);
        GuiDraw.drawRect(xPosition, yPosition, width, 1, border);
        GuiDraw.drawRect(xPosition, yPosition + height - 1, width, 1, border);
        GuiDraw.drawRect(xPosition, yPosition, 1, height, border);
        GuiDraw.drawRect(xPosition + width - 1, yPosition, 1, height, border);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        TreeRenderer.drawItemStack(xPosition + 2, yPosition + 2, activeGoal());
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return visible && mouseX >= xPosition
            && mouseX < xPosition + width
            && mouseY >= yPosition
            && mouseY < yPosition + height;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!enabled || !shouldShow() || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        mc.displayGuiScreen(new GuiTreeManager(mc.currentScreen));
        return true;
    }

    public List<String> getToolTip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add("\u00a7e" + StatCollector.translateToLocal("neirecipetree.button.inventory"));
        ItemStack goal = activeGoal();
        if (goal != null) {
            tooltip.add(
                "\u00a77" + StatCollector.translateToLocal("neirecipetree.tree_manager.current")
                    + ": "
                    + goal.getDisplayName());
        }
        tooltip.add("\u00a78" + StatCollector.translateToLocal("neirecipetree.button.inventory.desc"));
        return tooltip;
    }

    /**
     * Draws the tooltip itself; {@link net.minecraft.client.gui.GuiScreen#drawHoveringText} is not
     * reachable from here and the inventory has no NEI tooltip pipeline.
     */
    public void drawToolTip(int mouseX, int mouseY) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        List<String> lines = getToolTip();
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, fontRenderer.getStringWidth(line));
        }
        int height = lines.size() * 10 + 4;
        int x = mouseX + 12;
        int y = mouseY - 12;

        GuiDraw.drawRect(x - 3, y - 3, width + 6, height + 2, 0xF0100010);
        GuiDraw.drawRect(x - 3, y - 3, width + 6, 1, 0x505000FF);
        GuiDraw.drawRect(x - 3, y + height - 2, width + 6, 1, 0x5028007F);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawStringWithShadow(lines.get(i), x, y + i * 10, 0xFFFFFFFF);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
    }
}
