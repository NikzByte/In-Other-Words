package main;

import entity.NPC_Courtyard;

/**
 * Places NPCs across the game maps.
 */
public class AssetSetter {

    GamePanel gp;

    public AssetSetter(GamePanel gp) {
        this.gp = gp;
    }

    public void setNPC() {
        int mapNum = EventHandler.MAP_COURTYARD;

        // Place stationary NPCs side-by-side along the courtyard concrete path (row 9)
        placeCourtyardNPC(mapNum, 0, "jessaNPC", 13, 9);
        placeCourtyardNPC(mapNum, 1, "innaNPC",  11, 9);
        placeCourtyardNPC(mapNum, 2, "nikkaNPC", 12, 9);
    }

    private void placeCourtyardNPC(int mapNum, int index, String spriteName, int col, int row) {
        NPC_Courtyard npc = new NPC_Courtyard(gp, spriteName);
        npc.worldX = gp.tileSize * col;
        npc.worldY = gp.tileSize * row;
        gp.npc[mapNum][index] = npc;
    }
}
