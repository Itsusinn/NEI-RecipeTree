package moe.takochan.neirecipetree.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.bom.MaterialTree;
import moe.takochan.neirecipetree.gui.GuiTreeManager;

/**
 * Inventory button that opens the recipe tree list.
 *
 * <p>
 * Styled like the other inventory buttons of the pack (EZMiner, ServerUtilities sidebar): a bare
 * 16x16 icon on a transparent background with a faint white overlay while hovered, no custom frame.
 * Nothing is drawn while no tree has been opened yet.
 */
public class InventoryTreeButton extends GuiButton {

    public static final int SIZE = 16;
    private static final ResourceLocation ICON = new ResourceLocation("neirecipetree", "textures/gui/tree_button.png");
    private static final int HOVER_OVERLAY = 0x22FFFFFF;

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

        mc.getTextureManager()
            .bindTexture(ICON);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTexturedModalRect(xPosition, yPosition, 0, 0, width, height);

        if (hovered) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GuiDraw.drawRect(xPosition, yPosition, width, height, HOVER_OVERLAY);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
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
