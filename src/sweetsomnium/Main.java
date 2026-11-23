package sweetsomnium;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Entry point: opens the window and starts the game loop. */
public class Main {

    public static void main(String[] args) {
        // Swing windows must be created on Swing's own thread, so hand it this job
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("Sweet Somnium");
                GamePanel panel = new GamePanel();

                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.add(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);

                panel.requestFocusInWindow();
                panel.start();
            }
        });
    }
}
