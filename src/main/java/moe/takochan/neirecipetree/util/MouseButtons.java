package moe.takochan.neirecipetree.util;

import org.lwjgl.input.Mouse;

/**
 * Maps the two extra mouse buttons to browser-style navigation.
 *
 * <p>
 * LWJGL reports the first side button (back) as button 3 and the second (forward) as button 4.
 */
public final class MouseButtons {

    public static final int BACK = 3;
    public static final int FORWARD = 4;

    private MouseButtons() {}

    public static boolean backPressed() {
        return Mouse.getEventButtonState() && Mouse.getEventButton() == BACK;
    }

    public static boolean forwardPressed() {
        return Mouse.getEventButtonState() && Mouse.getEventButton() == FORWARD;
    }
}
