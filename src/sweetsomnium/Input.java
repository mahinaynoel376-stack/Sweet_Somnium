package sweetsomnium;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Keyboard state. Swing writes it (key events), the game loop reads it.
 * "Held" keys are plain flags; one-shot presses (jump, confirm) are latched
 * so a quick tap between two frames is never lost.
 *
 * Two threads use this object, so the methods that touch the one-shot presses
 * are {@code synchronized}: only one thread can be inside them at a time.
 */
public class Input extends KeyAdapter {

    private volatile boolean left;
    private volatile boolean right;
    private volatile boolean jumpHeld;
    private boolean jumpPressed;
    private boolean confirmPressed;

    @Override
    public void keyPressed(KeyEvent e) {
        handle(e.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        handle(e.getKeyCode(), false);
    }

    private synchronized void handle(int key, boolean down) {
        switch (key) {
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                left = down;
                break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                right = down;
                break;
            case KeyEvent.VK_SPACE:
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                if (down && !jumpHeld) {
                    jumpPressed = true; // holding the key (auto-repeat) doesn't re-trigger
                }
                jumpHeld = down;
                break;
            case KeyEvent.VK_ENTER:
                if (down) {
                    confirmPressed = true;
                }
                break;
            default:
                break;
        }
    }

    public boolean left() {
        return left;
    }

    public boolean right() {
        return right;
    }

    public boolean jumpHeld() {
        return jumpHeld;
    }

    /** True once per jump-key press. */
    public synchronized boolean consumeJump() {
        boolean wasPressed = jumpPressed;
        jumpPressed = false;
        return wasPressed;
    }

    /** True once per Enter press. */
    public synchronized boolean consumeConfirm() {
        boolean wasPressed = confirmPressed;
        confirmPressed = false;
        return wasPressed;
    }

    /** Forget queued presses, e.g. when switching screens. */
    public synchronized void clearPresses() {
        jumpPressed = false;
        confirmPressed = false;
    }

    /** Called when the window loses focus, so no key gets stuck "down". */
    public void releaseAll() {
        left = false;
        right = false;
        jumpHeld = false;
        clearPresses();
    }
}
