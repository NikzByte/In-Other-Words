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
        // Map 0: Courtyard / Campus
        placeNPC(EventHandler.MAP_COURTYARD, 0, "jessaNPC", 13, 9);
        placeNPC(EventHandler.MAP_COURTYARD, 1, "innaNPC",  11, 9);
        placeNPC(EventHandler.MAP_COURTYARD, 2, "nikkaNPC", 12, 9);

        // Map 2: Classroom (Room 401) - Club meetings & presentations
        placeNPC(EventHandler.MAP_CLASSROOM, 0, "jessaNPC", 6, 6);
        placeNPC(EventHandler.MAP_CLASSROOM, 1, "innaNPC",  8, 6);
        placeNPC(EventHandler.MAP_CLASSROOM, 2, "nikkaNPC", 10, 6);

        // Map 3: Library - Study breaks, partner tutoring & quiet scenes
        placeNPC(EventHandler.MAP_LIBRARY, 0, "jessaNPC", 6, 7);
        placeNPC(EventHandler.MAP_LIBRARY, 1, "innaNPC",  8, 7);
        placeNPC(EventHandler.MAP_LIBRARY, 2, "nikkaNPC", 10, 7);

        // Map 4: Garden / Balcony - Confession & evening scenes
        placeNPC(EventHandler.MAP_GARDEN, 0, "jessaNPC", 7, 9);
        placeNPC(EventHandler.MAP_GARDEN, 1, "innaNPC",  9, 9);
        placeNPC(EventHandler.MAP_GARDEN, 2, "nikkaNPC", 11, 9);

        // Map 5: Hometown (Cebu / Vigan / Naga streets & stalls)
        placeNPC(EventHandler.MAP_HOMETOWN, 0, "jessaNPC", 8, 8);
        placeNPC(EventHandler.MAP_HOMETOWN, 1, "innaNPC",  10, 8);
        placeNPC(EventHandler.MAP_HOMETOWN, 2, "nikkaNPC", 12, 8);

        // Map 6: Bedroom / Home (Cebu / Vigan / Naga house scenes)
        placeNPC(EventHandler.MAP_BEDROOM, 0, "jessaNPC", 6, 7);
        placeNPC(EventHandler.MAP_BEDROOM, 1, "innaNPC",  7, 7);
        placeNPC(EventHandler.MAP_BEDROOM, 2, "nikkaNPC", 9, 7);
    }

    private void placeNPC(int mapNum, int index, String spriteName, int col, int row) {
        NPC_Courtyard npc = new NPC_Courtyard(gp, spriteName);
        npc.worldX = gp.tileSize * col;
        npc.worldY = gp.tileSize * row;
        gp.npc[mapNum][index] = npc;
    }
}
