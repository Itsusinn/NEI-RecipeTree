package moe.takochan.neirecipetree.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.bom.MaterialTree;
import moe.takochan.neirecipetree.bom.TreeStorage;
import moe.takochan.neirecipetree.gui.GuiRecipeTree;
import moe.takochan.neirecipetree.gui.GuiTreeManager;
import moe.takochan.neirecipetree.gui.TreeRenderer;

/**
 * Inventory button that reopens whatever the player last used: the active tree screen or the tree
 * list.
 *
 * <p>
 * Uses the original framed-button design: the active tree's goal item is drawn on a dark 20x20
 * button, so the button also shows which tree is selected. When no tree exists yet the tree glyph
 * is used as a placeholder, and the button stays visible so the manager can still be opened.
 */
public class InventoryTreeButton extends GuiButton {

    public static final int SIZE = 20;
    private static final ResourceLocation FALLBACK_ICON = new ResourceLocation(
        "neirecipetree",
        "textures/gui/tree_button.png");

    public InventoryTreeButton(int id, int x, int y) {
        super(id, x, y, SIZE, SIZE, "");
    }

    /**
     * Always shown: even with an empty list the manager can start a tree from the held or last
     * hovered inventory item, and saved trees are restored when the game starts.
     */
    public boolean shouldShow() {
        return true;
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

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GuiDraw.drawRect(xPosition, yPosition, width, height, background);
        GuiDraw.drawRect(xPosition, yPosition, width, 1, border);
        GuiDraw.drawRect(xPosition, yPosition + height - 1, width, 1, border);
        GuiDraw.drawRect(xPosition, yPosition, 1, height, border);
        GuiDraw.drawRect(xPosition + width - 1, yPosition, 1, height, border);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        ItemStack goal = activeGoal();
        if (goal != null) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            TreeRenderer.drawItemStack(xPosition + 2, yPosition + 2, goal);
            return;
        }

        // Empty list: show the tree glyph instead of a missing item icon
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GuiDraw.drawRect(xPosition + 2, yPosition + 2, 16, 16, 0xFFC6C6C6);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        mc.getTextureManager()
            .bindTexture(FALLBACK_ICON);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTexturedModalRect(xPosition + 2, yPosition + 2, 0, 0, 16, 16);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
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
        GuiScreen parent = mc.currentScreen;
        if (TreeStorage.getLastView() == TreeStorage.View.TREE && BoM.tree != null) {
            // Keep a selector behind the tree so E still has a place to return to.
            mc.displayGuiScreen(new GuiRecipeTree(new GuiTreeManager(parent)));
        } else {
            mc.displayGuiScreen(new GuiTreeManager(parent));
        }
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
