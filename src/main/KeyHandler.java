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

        // character movement
        if (code == KeyEvent.VK_W) {
            upPressed = true;
        }
        if (code == KeyEvent.VK_S) {
            downPressed = true;
        }
        if (code == KeyEvent.VK_A) {
            leftPressed = true;
        }
        if (code == KeyEvent.VK_D) {
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
                case 0: gp.tileM.loadMap("/assets/maps/map01.txt", 0); break;
                case 1: gp.tileM.loadMap("/assets/maps/courtyard.txt", 1); break;
            }
        }
    }

    @Override 
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        
        if (code == KeyEvent.VK_W) {
            upPressed = false;
        }
        if (code == KeyEvent.VK_S) {
            downPressed = false;
        }
        if (code == KeyEvent.VK_A) {
            leftPressed = false;
        }
        if (code == KeyEvent.VK_D) {
            rightPressed = false;
        }       
    }
}
