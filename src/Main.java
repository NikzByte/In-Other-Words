import javax.swing.JFrame;

public class Main {
    public static void main(String[] args) {

        JFrame window = new JFrame ();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setTitle("In Other Words");

        GamePanel gamePanel = new GamePanel();
        window.add(gamePanel);

        window.pack(); // *pack() makes window sized to fit the pref size

        window.setLocationRelativeTo(null);
        window.setVisible(true);

    }
}
