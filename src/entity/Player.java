package entity;

import java.awt.Graphics2D;
// import java.awt.Color;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.awt.image.BufferedImage;

import main.GamePanel;
import main.KeyHandler;

public class Player extends Entity {

    GamePanel gp;
    KeyHandler keyH;

    public Player(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;

        setDefaultValues();
        getPlayerImage();
    }
    public void setDefaultValues() {
        x = 100;
        y = 100;
        speed = 4;
        direction = "down";

    }

    public void getPlayerImage() {
        try {
            up1 = formatTransparency(ImageIO.read(getClass().getResourceAsStream("/assets/player/playerSprite01.png")));
            up2 = formatTransparency(ImageIO.read(getClass().getResourceAsStream("/assets/player/playerSprite02.png")));
            left1 = formatTransparency(ImageIO.read(getClass().getResourceAsStream("/assets/player/playerSprite03.png")));
            left2 = formatTransparency(ImageIO.read(getClass().getResourceAsStream("/assets/player/playerSprite04.png")));
            right1 = formatTransparency(ImageIO.read(getClass().getResourceAsStream("/assets/player/playerSprite05.png")));
            right2 = formatTransparency(ImageIO.read(getClass().getResourceAsStream("/assets/player/playerSprite06.png")));
            down1 = formatTransparency(ImageIO.read(getClass().getResourceAsStream("/assets/player/playerSprite07.png")));
            down2 = formatTransparency(ImageIO.read(getClass().getResourceAsStream("/assets/player/playerSprite08.png")));

        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    public void update() {

        if (keyH.upPressed == true || keyH.downPressed == true || keyH.leftPressed == true || keyH.rightPressed == true) {
            if (keyH.upPressed == true) {
                    direction = "up";
                    y -= speed; // UPPER LEFT CORNER: X=0, Y=0 (x values increases to the right; y values increases as they go down)
                } else if (keyH.downPressed == true) {
                    direction = "down";
                    y += speed;
                } else if (keyH.leftPressed == true) {
                    direction = "left";
                    x -= speed;
                } else if (keyH.rightPressed == true) {
                    direction = "right";
                    x += speed;
                }

                spriteCounter++;
                if (spriteCounter > 10) {
                    if (spriteNum == 1) {
                        spriteNum = 2;
                    }
                    else if (spriteNum == 2) {
                        spriteNum = 1;
                    }
                    spriteCounter = 0;
                }
        }
    }
    public void draw(Graphics2D g2) {
//        g2.setColor(Color.white);
//        g2.fillRect(x, y, gp.tileSize, gp.tileSize);


        BufferedImage image = null;

        switch(direction) {
            case "up":
                if (spriteNum == 1) {
                    image = up1;
                }
                if (spriteNum == 2) {
                    image = up2;
                }
                break;
            case "down":
                if (spriteNum == 1) {
                    image = down1;
                }
                if (spriteNum == 2) {
                    image = down2;
                }
                break;
            case "left":
                if (spriteNum == 1) {
                    image = left1;
                }
                if (spriteNum == 2) {
                    image = left2;
                }
                break;
            case "right":
                if (spriteNum == 1) {
                    image = right1;
                }
                if (spriteNum == 2) {
                    image = right2;
                }
                break;
        }
        g2.drawImage(image, x, y, gp.tileSize, gp.tileSize, null);
    }

    public BufferedImage formatTransparency(BufferedImage original) {
    if (original == null) return null;
    BufferedImage formatted = new BufferedImage(
        original.getWidth(), 
        original.getHeight(), 
        BufferedImage.TYPE_INT_ARGB
    );
    Graphics2D g2 = formatted.createGraphics();
    g2.drawImage(original, 0, 0, null);
    g2.dispose();
    return formatted;
    
}
}
