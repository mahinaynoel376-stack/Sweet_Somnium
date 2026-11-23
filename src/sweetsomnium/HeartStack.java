package sweetsomnium;

/**
 * A last-in, first-out stack of hearts, built on a plain array.
 *
 * Each heart is stored as its number (1, 2, 3...). {@code size} is both how many
 * hearts are left and the index where the next push goes, so push and pop are O(1).
 *
 * Used for the player's health: every level pushes 3 hearts, and taking damage pops
 * the top one (the rightmost heart on the HUD). When the stack is empty it's Game Over.
 */
final class HeartStack {

    private final int[] hearts;
    private int size;

    HeartStack(int capacity) {
        hearts = new int[capacity];
    }

    /** Puts a heart on top of the stack. */
    void push(int heart) {
        if (size == hearts.length) {
            throw new IllegalStateException("the stack is full");
        }
        hearts[size] = heart;
        size++;
    }

    /** Removes and returns the heart on top, or 0 if the stack is empty. */
    int pop() {
        if (size == 0) {
            return 0;
        }
        size--;
        return hearts[size];
    }

    /** The heart on top without removing it, or 0 if the stack is empty. */
    int peek() {
        return size == 0 ? 0 : hearts[size - 1];
    }

    boolean isEmpty() {
        return size == 0;
    }

    /** How many hearts are left. */
    int size() {
        return size;
    }

    void clear() {
        size = 0;
    }
}
