package main;

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

public class KeyHandler implements KeyListener {

    GamePanel gp;
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    boolean checkDrawTime = false;

    public KeyHandler(GamePanel gp) {
        this.gp = gp;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode(); // returns the integer keyCode associated with the key in this event
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;

        // character movement (WASD + Arrow keys)
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) {
            upPressed = true;
        }
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) {
            downPressed = true;
        }
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) {
            leftPressed = true;
        }
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) {
            rightPressed = true;
        }

        // DEBUGGING
        if (code == KeyEvent.VK_T) {
            if (checkDrawTime == false) {
                checkDrawTime = true;
            } else if (checkDrawTime == true) {
                checkDrawTime = false;
            }
        }
        if (code == KeyEvent.VK_R) {
            switch (gp.currentMap) {
                case 0: gp.tileM.loadMap("/assets/maps/courtyard.txt", 0); break;
                case 1: gp.tileM.loadMap("/assets/maps/hallway.txt", 1); break;
                case 2: gp.tileM.loadMap("/assets/maps/classroom.txt", 2); break;
                case 3: gp.tileM.loadMap("/assets/maps/library.txt", 3); break;
                case 4: gp.tileM.loadMap("/assets/maps/garden.txt", 4); break;
                case 5: gp.tileM.loadMap("/assets/maps/hometown.txt", 5); break;
                case 6: gp.tileM.loadMap("/assets/maps/bedroom.txt", 6); break;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) {
            upPressed = false;
        }
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) {
            downPressed = false;
        }
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) {
            leftPressed = false;
        }
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) {
            rightPressed = false;
        }
    }
}
