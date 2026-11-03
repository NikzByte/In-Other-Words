package entity;

import java.awt.Graphics2D;
import java.awt.Color;

import main.GamePanel;
import main.KeyHandler;

public class Player extends Entity {

    GamePanel gp;
    KeyHandler keyH;

    public Player(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;

        setDefaultValues();
    }
    public void setDefaultValues() {
        x = 100;
        y = 100;
        speed = 4;

    }
    public void update() {
        if (keyH.upPressed == true) {
                    y -= speed; // UPPER LEFT CORNER: X=0, Y=0 (x values increases to the right; y values increases as they go down)
                } else if (keyH.downPressed == true) {
                    y += speed;
                } else if (keyH.leftPressed == true) {
                    x -= speed;
                } else if (keyH.rightPressed == true) {
                    x += speed;
                }
    }
    public void draw(Graphics2D g2) {
        g2.setColor(Color.white);
        g2.fillRect(x, y, gp.tileSize, gp.tileSize);

    }
    
}
