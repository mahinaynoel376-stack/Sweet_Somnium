package sweetsomnium;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Entry point: opens the window and starts the game loop. */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Sweet Somnium");
            GamePanel panel = new GamePanel();

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            panel.requestFocusInWindow();
            panel.start();
        });
    }
}
