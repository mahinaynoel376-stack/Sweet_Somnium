package sweetsomnium;

/**
 * A first-in, first-out queue built on a circular array.
 *
 * Items leave in the same order they arrived. {@code head} points at the oldest
 * item and new items go in at {@code (head + size) % items.length}; both ends
 * wrap around the array, so enqueue and dequeue never shift elements and are O(1).
 * When the array is full it doubles in size.
 *
 * Used for the on-screen toast messages: if several messages happen at once
 * they are shown one after another instead of overwriting each other.
 */
final class MessageQueue<T> {

    private Object[] items;
    private int head;
    private int size;

    MessageQueue() {
        items = new Object[4];
    }

    /** Adds an item at the back of the queue. */
    void enqueue(T item) {
        if (size == items.length) {
            grow();
        }
        items[(head + size) % items.length] = item;
        size++;
    }

    /** Removes and returns the item at the front, or null if the queue is empty. */
    T dequeue() {
        if (size == 0) {
            return null;
        }
        T item = peek();
        items[head] = null; // let it be garbage collected
        head = (head + 1) % items.length;
        size--;
        return item;
    }

    /** The item at the front without removing it, or null if the queue is empty. */
    @SuppressWarnings("unchecked")
    T peek() {
        return size == 0 ? null : (T) items[head];
    }

    /** True if {@code item} is already waiting somewhere in the queue. */
    boolean contains(T item) {
        for (int i = 0; i < size; i++) {
            if (items[(head + i) % items.length].equals(item)) {
                return true;
            }
        }
        return false;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    void clear() {
        items = new Object[4];
        head = 0;
        size = 0;
    }

    /** Copies the items into an array twice as big, oldest first, so the wrap-around is undone. */
    private void grow() {
        Object[] bigger = new Object[items.length * 2];
        for (int i = 0; i < size; i++) {
            bigger[i] = items[(head + i) % items.length];
        }
        items = bigger;
        head = 0;
    }
}
