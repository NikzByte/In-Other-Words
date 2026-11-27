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
        updateNPCsForScene("M02");
    }

    /**
     * Places Jessa, Inna, and Nikka in their exact scene-specific map and
     * coordinates from the NPC Sprite Location Assignment document.
     * Clears previous placements so NPCs only move when a scene finishes.
     */
    public void updateNPCsForScene(String sceneId) {
        for (int m = 0; m < gp.maxMap; m++) {
            for (int i = 0; i < gp.npc[m].length; i++) {
                gp.npc[m][i] = null;
            }
        }
        if (sceneId == null || sceneId.isBlank() || sceneId.equals("M00") || sceneId.equals("M01")) {
            return;
        }
        switch (sceneId) {
            // Shared Story Scenes
            case "M02"    -> placeScene(EventHandler.MAP_CLASSROOM, 100,260, 340,260, 220,360);
            case "S1-01"  -> placeScene(EventHandler.MAP_CLASSROOM, 100,220, 340,220, 220,360);
            case "S1-02A" -> placeScene(EventHandler.MAP_CLASSROOM, 220,360, 360,140, 360,220);
            case "S1-02B" -> placeScene(EventHandler.MAP_CLASSROOM, 100,300, 220,300, 360,220);
            case "S1-03"  -> placeScene(EventHandler.MAP_CLASSROOM, 100,180, 100,300, 340,300);
            case "S2-01"  -> placeScene(EventHandler.MAP_CLASSROOM, 100,220, 220,220, 340,220);

            // Jessa's Route
            case "J2-01", "J2-L" -> placeScene(EventHandler.MAP_LIBRARY,   200,280, -1,-1,   -1,-1);
            case "J2-02"         -> placeScene(EventHandler.MAP_CLASSROOM, 220,360, 100,180, 340,180);
            case "J2-04"         -> placeScene(EventHandler.MAP_LIBRARY,   220,300, -1,-1,   -1,-1);
            case "J2-05", "J-F"  -> placeScene(EventHandler.MAP_CLASSROOM, 220,300, -1,-1,   -1,-1);
            case "J2-06"         -> placeScene(EventHandler.MAP_LIBRARY,   180,280, 340,380, 360,180);
            case "J3-00"         -> placeScene(EventHandler.MAP_CLASSROOM, 340,360, 220,220, 220,360);
            case "J3-01"         -> placeScene(EventHandler.MAP_HOMETOWN,  420,280, -1,-1,   -1,-1);
            case "J3-02"         -> placeScene(EventHandler.MAP_BEDROOM,   220,280, -1,-1,   -1,-1);
            case "J3-03"         -> placeScene(EventHandler.MAP_BEDROOM,   180,300, -1,-1,   -1,-1);
            case "J4-00"         -> placeScene(EventHandler.MAP_CLASSROOM, 220,400, 340,300, 100,300);
            case "J4-01"         -> placeScene(EventHandler.MAP_CLASSROOM, 220,180, -1,-1,   -1,-1);
            case "J4-02", "J-R"  -> placeScene(EventHandler.MAP_COURTYARD, 440,300, -1,-1,   -1,-1);
            case "J-N"           -> placeScene(EventHandler.MAP_COURTYARD, 380,300, -1,-1,   -1,-1);

            // Inna's Route
            case "I2-01", "I2-L", "I4-02", "I-R" -> placeScene(EventHandler.MAP_LIBRARY, -1,-1, 340,300, -1,-1);
            case "I2-02"         -> placeScene(EventHandler.MAP_CLASSROOM, 340,300, 220,300, -1,-1);
            case "I2-04"         -> placeScene(EventHandler.MAP_LIBRARY,   -1,-1,   220,300, -1,-1);
            case "I2-05", "I4-00", "I-F" -> placeScene(EventHandler.MAP_CLASSROOM, -1,-1, 220,300, -1,-1);
            case "I2-06"         -> placeScene(EventHandler.MAP_LIBRARY,   100,280, 340,300, 380,280);
            case "I3-00"         -> placeScene(EventHandler.MAP_CLASSROOM, 100,360, 220,220, 340,220);
            case "I3-01"         -> placeScene(EventHandler.MAP_HOMETOWN,  -1,-1,   420,280, -1,-1);
            case "I3-02"         -> placeScene(EventHandler.MAP_BEDROOM,   -1,-1,   220,280, -1,-1);
            case "I3-03"         -> placeScene(EventHandler.MAP_BEDROOM,   -1,-1,   180,300, -1,-1);
            case "I4-01"         -> placeScene(EventHandler.MAP_CLASSROOM, -1,-1,   220,180, -1,-1);
            case "I-N"           -> placeScene(EventHandler.MAP_LIBRARY,   -1,-1,   380,300, -1,-1);

            // Nikka's Route
            case "N2-01", "N2-L" -> placeScene(EventHandler.MAP_LIBRARY,   -1,-1,   -1,-1,   340,300);
            case "N2-02", "N-F"  -> placeScene(EventHandler.MAP_CLASSROOM, -1,-1,   -1,-1,   220,300);
            case "N2-04"         -> placeScene(EventHandler.MAP_LIBRARY,   -1,-1,   -1,-1,   220,300);
            case "N2-05"         -> placeScene(EventHandler.MAP_CLASSROOM, 100,180, 340,180, 220,300);
            case "N2-06"         -> placeScene(EventHandler.MAP_LIBRARY,   100,300, 380,180, 220,300);
            case "N3-00"         -> placeScene(EventHandler.MAP_CLASSROOM, 100,360, 220,220, 340,220);
            case "N3-01"         -> placeScene(EventHandler.MAP_HOMETOWN,  -1,-1,   -1,-1,   420,280);
            case "N3-02"         -> placeScene(EventHandler.MAP_BEDROOM,   -1,-1,   -1,-1,   220,280);
            case "N3-03"         -> placeScene(EventHandler.MAP_HOMETOWN,  -1,-1,   -1,-1,   540,300);
            case "N4-00"         -> placeScene(EventHandler.MAP_CLASSROOM, 100,300, 340,300, 220,300);
            case "N4-01"         -> placeScene(EventHandler.MAP_CLASSROOM, -1,-1,   -1,-1,   220,180);
            case "N4-02", "N-R"  -> placeScene(EventHandler.MAP_GARDEN,    -1,-1,   -1,-1,   220,300);
            case "N-N"           -> placeScene(EventHandler.MAP_GARDEN,    -1,-1,   -1,-1,   340,300);
            default -> { }
        }
    }

    private void placeScene(int mapNum, int jx, int jy, int ix, int iy, int nx, int ny) {
        int idx = 0;
        if (jx >= 0 && jy >= 0) placeAtDocCoord(mapNum, idx++, "jessaNPC", jx, jy);
        if (ix >= 0 && iy >= 0) placeAtDocCoord(mapNum, idx++, "innaNPC",  ix, iy);
        if (nx >= 0 && ny >= 0) placeAtDocCoord(mapNum, idx++, "nikkaNPC", nx, ny);
    }

    private void placeAtDocCoord(int mapNum, int index, String spriteName, int docX, int docY) {
        int col = Math.max(1, Math.min(gp.maxWorldCol - 2, Math.round(docX / 30.0f)));
        int row = Math.max(1, Math.min(gp.maxWorldRow - 2, Math.round(docY / 28.0f)));
        // Snap to nearest walkable, unoccupied tile so NPCs never stack on each other
        int tid = gp.tileM.mapTileNum[mapNum][col][row];
        boolean blocked = tid == 0 || (gp.tileM.tile[tid] != null && gp.tileM.tile[tid].collision);
        if (blocked || isTileOccupied(mapNum, col, row, index)) {
            int bestCol = col, bestRow = row;
            int bestDist = Integer.MAX_VALUE;
            for (int r = 1; r < gp.maxWorldRow - 1; r++) {
                for (int c = 1; c < gp.maxWorldCol - 1; c++) {
                    int t = gp.tileM.mapTileNum[mapNum][c][r];
                    if (t != 0 && gp.tileM.tile[t] != null && !gp.tileM.tile[t].collision
                            && !isTileOccupied(mapNum, c, r, index)) {
                        int d = Math.abs(c - col) + Math.abs(r - row);
                        if (d < bestDist) { bestDist = d; bestCol = c; bestRow = r; }
                    }
                }
            }
            col = bestCol; row = bestRow;
        }
        NPC_Courtyard npc = new NPC_Courtyard(gp, spriteName);
        npc.worldX = gp.tileSize * col;
        npc.worldY = gp.tileSize * row;
        gp.npc[mapNum][index] = npc;
    }

    private boolean isTileOccupied(int mapNum, int col, int row, int count) {
        int targetX = gp.tileSize * col;
        int targetY = gp.tileSize * row;
        for (int i = 0; i < count; i++) {
            if (gp.npc[mapNum][i] != null
                    && gp.npc[mapNum][i].worldX == targetX
                    && gp.npc[mapNum][i].worldY == targetY) {
                return true;
            }
        }
        return false;
    }
}
