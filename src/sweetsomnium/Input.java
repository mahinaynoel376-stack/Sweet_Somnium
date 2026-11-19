package sweetsomnium;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keyboard state. Swing writes it (key events), the game loop reads it.
 * "Held" keys are plain flags; one-shot presses (jump, confirm) are latched
 * so a quick tap between two frames is never lost.
 */
public class Input extends KeyAdapter {

    private volatile boolean left;
    private volatile boolean right;
    private volatile boolean jumpHeld;
    private final AtomicBoolean jumpPressed = new AtomicBoolean();
    private final AtomicBoolean confirmPressed = new AtomicBoolean();

    @Override
    public void keyPressed(KeyEvent e) {
        handle(e.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        handle(e.getKeyCode(), false);
    }

    private void handle(int key, boolean down) {
        switch (key) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> left = down;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> right = down;
            case KeyEvent.VK_SPACE, KeyEvent.VK_UP, KeyEvent.VK_W -> {
                if (down && !jumpHeld) {
                    jumpPressed.set(true); // holding the key (auto-repeat) doesn't re-trigger
                }
                jumpHeld = down;
            }
            case KeyEvent.VK_ENTER -> {
                if (down) {
                    confirmPressed.set(true);
                }
            }
            default -> { }
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
    public boolean consumeJump() {
        return jumpPressed.getAndSet(false);
    }

    /** True once per Enter press. */
    public boolean consumeConfirm() {
        return confirmPressed.getAndSet(false);
    }

    /** Forget queued presses, e.g. when switching screens. */
    public void clearPresses() {
        jumpPressed.set(false);
        confirmPressed.set(false);
    }

    /** Called when the window loses focus, so no key gets stuck "down". */
    public void releaseAll() {
        left = false;
        right = false;
        jumpHeld = false;
        clearPresses();
    }
}
