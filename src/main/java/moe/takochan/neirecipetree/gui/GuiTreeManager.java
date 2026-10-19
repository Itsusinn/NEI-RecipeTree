package moe.takochan.neirecipetree.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.bom.MaterialTree;

/**
 * Lists the recipe trees opened in this session and lets the player switch between them, create a
 * new tree from the item held on the cursor, and delete trees.
 */
public class GuiTreeManager extends GuiScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_ROWS = 7;
    private static final int TITLE_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 30;
    private static final int ICON_SIZE = 16;
    private static final int BUTTON_WIDTH = 52;
    private static final int BUTTON_HEIGHT = 18;
    private static final int DELETE_WIDTH = 16;
    private static final int ROW_INSET = 4;

    private final GuiScreen parentScreen;

    /**
     * Last item the player hovered in the inventory; used as the goal of a newly created tree when
     * nothing is held on the cursor.
     */
    public static ItemStack hoveredInventoryGoal;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int visibleRows = 1;
    private int scroll = 0;
    private int newButtonX;
    private int newButtonY;

    public GuiTreeManager(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        visibleRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, BoM.trees.size()));
        panelHeight = TITLE_HEIGHT + visibleRows * ROW_HEIGHT + FOOTER_HEIGHT;
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - panelHeight) / 2;
        newButtonX = panelX + ROW_INSET;
        newButtonY = panelY + panelHeight - FOOTER_HEIGHT + 2;
        scroll = clampScroll(scroll);
    }

    private int clampScroll(int value) {
        int max = Math.max(0, BoM.trees.size() - visibleRows);
        return Math.max(0, Math.min(max, value));
    }

    private int rowWidth() {
        return PANEL_WIDTH - ROW_INSET * 2;
    }

    private int rowX() {
        return panelX + ROW_INSET;
    }

    private int rowY(int visibleIndex) {
        return panelY + TITLE_HEIGHT + visibleIndex * ROW_HEIGHT;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xCC000000);
        drawRect(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + panelHeight + 1, 0xFF50505A);
        drawRect(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xFF14141C);

        drawCenteredString(
            fontRendererObj,
            "\u00a7l" + StatCollector.translateToLocal("neirecipetree.tree_manager.title"),
            width / 2,
            panelY + 6,
            0xFFFFFFFF);

        if (BoM.trees.isEmpty()) {
            drawCenteredString(
                fontRendererObj,
                "\u00a77" + StatCollector.translateToLocal("neirecipetree.tree_manager.empty"),
                width / 2,
                panelY + TITLE_HEIGHT + 4,
                0xFFAAAAAA);
        } else {
            for (int i = 0; i < visibleRows && scroll + i < BoM.trees.size(); i++) {
                drawRow(i, mouseX, mouseY);
            }
        }

        drawNewButton(mouseX, mouseY);

        drawCenteredString(
            fontRendererObj,
            "\u00a78" + StatCollector.translateToLocal("neirecipetree.tree_manager.help"),
            width / 2,
            panelY + panelHeight - 11,
            0xFF888888);
    }

    private void drawRow(int visibleIndex, int mouseX, int mouseY) {
        MaterialTree candidate = BoM.trees.get(scroll + visibleIndex);
        int x = rowX();
        int y = rowY(visibleIndex);
        int width = rowWidth();
        int height = ROW_HEIGHT - 2;

        boolean active = candidate == BoM.tree;
        boolean hovered = isInside(mouseX, mouseY, x, y, width, height);
        int background = active ? 0xFF2C3A2C : hovered ? 0xFF2A2A34 : 0xFF1E1E26;
        drawRect(x, y, x + width, y + height, background);
        if (active) {
            // Left bar marks the tree the GUI currently works on
            drawRect(x, y, x + 2, y + height, 0xFF55CC55);
        }

        ItemStack goal = goalOf(candidate);
        if (goal != null) {
            TreeRenderer.drawItemStack(x + 4, y + 2, goal);
        }

        String name = goal != null ? goal.getDisplayName()
            : StatCollector.translateToLocal("neirecipetree.tree_manager.unknown");
        String text = fontRendererObj.trimStringToWidth(name, width - (ICON_SIZE + 12) - DELETE_WIDTH - 6);
        fontRendererObj.drawStringWithShadow(text, x + ICON_SIZE + 8, y + 6, active ? 0xFFFFFFFF : 0xFFD0D0D0);

        boolean deleteHovered = isInside(mouseX, mouseY, deleteX(x, width), y + 3, DELETE_WIDTH, height - 6);
        fontRendererObj
            .drawStringWithShadow("\u00a7lX", deleteX(x, width) + 4, y + 6, deleteHovered ? 0xFFFF5555 : 0xFF9A6666);
    }

    private int deleteX(int rowX, int rowWidth) {
        return rowX + rowWidth - DELETE_WIDTH - 2;
    }

    private void drawNewButton(int mouseX, int mouseY) {
        boolean enabled = newTreeGoal() != null;
        boolean hovered = enabled && isInside(mouseX, mouseY, newButtonX, newButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        int background = !enabled ? 0xFF232329 : hovered ? 0xFF3E4A3E : 0xFF2C2C34;
        int border = !enabled ? 0xFF3A3A42 : hovered ? 0xFF9CE89C : 0xFF6E8E6E;
        drawRect(newButtonX, newButtonY, newButtonX + BUTTON_WIDTH, newButtonY + BUTTON_HEIGHT, background);
        drawRect(newButtonX, newButtonY, newButtonX + BUTTON_WIDTH, newButtonY + 1, border);
        drawRect(
            newButtonX,
            newButtonY + BUTTON_HEIGHT - 1,
            newButtonX + BUTTON_WIDTH,
            newButtonY + BUTTON_HEIGHT,
            border);
        drawRect(newButtonX, newButtonY, newButtonX + 1, newButtonY + BUTTON_HEIGHT, border);
        drawRect(
            newButtonX + BUTTON_WIDTH - 1,
            newButtonY,
            newButtonX + BUTTON_WIDTH,
            newButtonY + BUTTON_HEIGHT,
            border);

        String label = StatCollector.translateToLocal("neirecipetree.tree_manager.new");
        fontRendererObj.drawStringWithShadow(
            enabled ? label : "\u00a78" + label,
            newButtonX + (BUTTON_WIDTH - fontRendererObj.getStringWidth(label)) / 2,
            newButtonY + 5,
            enabled ? 0xFFFFFFFF : 0xFF808080);

        String hint = StatCollector
            .translateToLocal(enabled ? "neirecipetree.tree_manager.new_ready" : "neirecipetree.tree_manager.new_hint");
        fontRendererObj
            .drawStringWithShadow("\u00a78" + hint, newButtonX + BUTTON_WIDTH + 8, newButtonY + 5, 0xFF808080);
    }

    private MaterialTree clickedTree(int mouseX, int mouseY) {
        for (int i = 0; i < visibleRows && scroll + i < BoM.trees.size(); i++) {
            if (isInside(mouseX, mouseY, rowX(), rowY(i), rowWidth(), ROW_HEIGHT - 2)) {
                return BoM.trees.get(scroll + i);
            }
        }
        return null;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return;
        }

        if (isInside(mouseX, mouseY, newButtonX, newButtonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            ItemStack goal = newTreeGoal();
            if (goal != null) {
                BoM.setGoal(goal);
                if (BoM.tree != null) {
                    mc.displayGuiScreen(new GuiRecipeTree(this));
                }
            }
            return;
        }

        for (int i = 0; i < visibleRows && scroll + i < BoM.trees.size(); i++) {
            int x = rowX();
            int y = rowY(i);
            if (!isInside(mouseX, mouseY, x, y, rowWidth(), ROW_HEIGHT - 2)) {
                continue;
            }
            MaterialTree clicked = BoM.trees.get(scroll + i);
            if (isInside(mouseX, mouseY, deleteX(x, rowWidth()), y + 3, DELETE_WIDTH, ROW_HEIGHT - 8)) {
                BoM.removeTree(clicked);
                initGui();
                return;
            }
            BoM.activate(clicked);
            mc.displayGuiScreen(new GuiRecipeTree(this));
            return;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || BoM.trees.size() <= visibleRows) {
            return;
        }
        scroll = clampScroll(scroll + (wheel > 0 ? -1 : 1));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESC
            mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private ItemStack goalOf(MaterialTree target) {
        if (target == null || target.goal == null) {
            return null;
        }
        ItemStack goal = target.goal.ingredient;
        return goal != null && goal.getItem() != null ? goal : null;
    }

    /** The item under the cursor, which is what "new tree" starts from. */
    private ItemStack cursorGoal() {
        if (mc.thePlayer == null) {
            return null;
        }
        ItemStack cursor = mc.thePlayer.inventory.getItemStack();
        return cursor != null && cursor.getItem() != null ? cursor.copy() : null;
    }

    /**
     * Goal for a new tree: the held item, or else the item the player last hovered in the
     * inventory, which is how the manager is usually reached.
     */
    private ItemStack newTreeGoal() {
        ItemStack cursor = cursorGoal();
        if (cursor != null) {
            return cursor;
        }
        ItemStack hovered = hoveredInventoryGoal;
        return hovered != null && hovered.getItem() != null ? hovered.copy() : null;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
