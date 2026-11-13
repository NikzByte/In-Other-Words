package entity;

import java.awt.Graphics2D;
import java.awt.Rectangle;

// import java.awt.Color;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.awt.image.BufferedImage;

import main.GamePanel;
import main.KeyHandler;

public class Player extends Entity {

    GamePanel gp;
    KeyHandler keyH;

    public final int screenX;
    public final int screenY;

    public Player(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;

        screenX = gp.screenWidth/2 - (gp.tileSize/2);
        screenY = gp.screenHeight/2 - (gp.tileSize/2);

        solidArea = new Rectangle(24, 32, 48, 48);

        setDefaultValues();
        getPlayerImage();
    }

    public void setDefaultValues() {
        worldX = gp.tileSize * 10;
        worldY = gp.tileSize * 10;
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
            
            // 1. Set the direction based on key pressed (DO NOT change worldX/worldY yet)
            if (keyH.upPressed == true) {
                direction = "up";
            } else if (keyH.downPressed == true) {
                direction = "down";
            } else if (keyH.leftPressed == true) {
                direction = "left";
            } else if (keyH.rightPressed == true) {
                direction = "right";
            }

            // 2. Check for tile collision
            collisionOn = false;
            gp.cChecker.checkTile(this); 

            // 3. If collision is false, player can move
            if (collisionOn == false) {
                switch(direction) {
                    case "up":
                        worldY -= speed;
                        break;
                    case "down":
                        worldY += speed;
                        break;
                    case "left":
                        worldX -= speed;
                        break;
                    case "right":
                        worldX += speed;
                        break;
                }
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
        g2.drawImage(image, screenX, screenY, gp.tileSize, gp.tileSize, null);
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

