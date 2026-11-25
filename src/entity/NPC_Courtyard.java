package entity;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import main.GamePanel;

/**
 * Stationary NPC placed along the courtyard path.
 */
public class NPC_Courtyard extends Entity {

    private static final String DEFAULT_SPRITE = "jessaNPC";
    private static final Map<String, BufferedImage> SPRITE_CACHE = new HashMap<>();

    private final String spriteName;

    public NPC_Courtyard(GamePanel gp) {
        this(gp, DEFAULT_SPRITE);
    }

    public NPC_Courtyard(GamePanel gp, String spriteName) {
        super(gp);
        this.spriteName = spriteName;

        direction = "down";
        speed = 0;

        solidArea = new Rectangle(32, 64, 64, 56);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        getImage();
    }

    public String getSpriteName() {
        return spriteName;
    }

    public void getImage() {
        String cacheKey = spriteName + ":" + gp.tileSize;
        BufferedImage sprite = SPRITE_CACHE.get(cacheKey);
        if (sprite == null) {
            sprite = setupImage("/assets/npc/" + spriteName);
            if (sprite != null) {
                SPRITE_CACHE.put(cacheKey, sprite);
            }
        }

        up1 = sprite;
        up2 = sprite;
        down1 = sprite;
        down2 = sprite;
        left1 = sprite;
        left2 = sprite;
        right1 = sprite;
        right2 = sprite;
    }

    public void speak() {
        // Dialogue will be implemented by groupmate.
    }
}
