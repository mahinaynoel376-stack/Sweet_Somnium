package sweetsomnium;

/**
 * A singly linked list of the candies (or pills) still in the level.
 *
 * Each {@link Node} holds one candy and a link to the next node. {@code head} is the
 * first node and {@code tail} the last, so adding at the end is O(1).
 *
 * A candy stays in the list while it's waiting to be picked up and while its "pop"
 * animation plays, then {@link #removeFinished()} unlinks it. Removing works by
 * walking the list with a {@code previous} pointer and pointing
 * {@code previous.next} past the node, so no other nodes have to move.
 *
 * To visit every candy:
 * <pre>
 * for (CandyList.Node n = candies.first(); n != null; n = n.next) {
 *     n.candy.update();
 * }
 * </pre>
 */
final class CandyList {

    /** One link in the chain. */
    static final class Node {
        final Collectible candy;
        Node next;

        Node(Collectible candy) {
            this.candy = candy;
        }
    }

    private Node head;
    private Node tail;
    private int size;

    /** Adds a candy at the end of the list. */
    void add(Collectible candy) {
        Node node = new Node(candy);
        if (head == null) {
            head = node;
        } else {
            tail.next = node;
        }
        tail = node;
        size++;
    }

    /** The first node, or null if the list is empty. */
    Node first() {
        return head;
    }

    /** Unlinks every candy that has been picked up and finished its pop animation. */
    void removeFinished() {
        Node previous = null;
        Node current = head;
        while (current != null) {
            if (current.candy.isFinished()) {
                if (previous == null) {
                    head = current.next;     // removing the first node
                } else {
                    previous.next = current.next;
                }
                if (current == tail) {
                    tail = previous;         // removing the last node
                }
                size--;
            } else {
                previous = current;
            }
            current = current.next;
        }
    }

    /**
     * Linear search: walks the list looking for a candy that hasn't been picked up.
     * Returns false as soon as it finds one, or true if there are none left.
     */
    boolean allCollected() {
        for (Node n = head; n != null; n = n.next) {
            if (!n.candy.isCollected()) {
                return false;
            }
        }
        return true;
    }

    /** How many candies are still in the list (including ones still popping). */
    int size() {
        return size;
    }

    boolean isEmpty() {
        return size == 0;
    }
}
