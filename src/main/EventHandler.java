package main;

public class EventHandler {

    GamePanel gp;
    boolean canTouchEvent = true;

    // Matched to TileManager.java loadMap() order
    public static final int MAP_COURTYARD = 0;
    public static final int MAP_HALLWAY   = 1;
    public static final int MAP_CLASSROOM = 2;
    public static final int MAP_LIBRARY   = 3;
    public static final int MAP_GARDEN    = 4;
    public static final int MAP_HOMETOWN  = 5;
    public static final int MAP_BEDROOM   = 6;

    public EventHandler(GamePanel gp) {
        this.gp = gp;
    }

    public void checkEvent() {
        int pCol = getPlayerCol();
        int pRow = getPlayerRow();

        // Prevent ArrayIndexOutOfBoundsException freeze when touching map edges
        if (pCol < 0 || pCol >= gp.maxWorldCol || pRow < 0 || pRow >= gp.maxWorldRow) {
            return;
        }

        int currentTile = gp.tileM.mapTileNum[gp.currentMap][pCol][pRow];

        // --- 1. DEFINE ALL EXIT / DOOR TILES ---

        // Courtyard (Map 0) - Dynamically finds leftmost path & bottommost path
        int[] courtyardLeftPath    = findLeftPathTile(MAP_COURTYARD);
        int courtyardBottomPathRow = findBottommostTileRow(MAP_COURTYARD, 4);

        boolean onCourtyardSchoolDoor = isOnTile(MAP_COURTYARD, 16, 8) || isOnTile(MAP_COURTYARD, 17, 8);
        boolean onCourtyardLeftPath   = (gp.currentMap == MAP_COURTYARD
                && pCol == courtyardLeftPath[0]
                && (pRow == courtyardLeftPath[1] || pRow == courtyardLeftPath[1] + 1)
                && currentTile == 4);
        boolean onCourtyardBottomPath = (gp.currentMap == MAP_COURTYARD
                && pRow == courtyardBottomPathRow
                && currentTile == 4);

        // Hallway (Map 1) - Brown doors ONLY (mat tiles 38 & 43 directly below the brown doors)
        int[] hallLeftBrownDoor  = findBrownDoorMat(MAP_HALLWAY, true);   // Left brown door -> Library
        int[] hallRightBrownDoor = findBrownDoorMat(MAP_HALLWAY, false);  // Right brown door -> Classroom

        boolean onHallwayBottomExit    = isOnTile(MAP_HALLWAY, 8, 11) || isOnTile(MAP_HALLWAY, 9, 11);
        boolean onHallwayLibraryDoor   = isOnTile(MAP_HALLWAY, hallLeftBrownDoor[0], hallLeftBrownDoor[1]);
        boolean onHallwayClassroomDoor = isOnTile(MAP_HALLWAY, hallRightBrownDoor[0], hallRightBrownDoor[1]);

        // Classroom (Map 2) - Exit door threshold tile 92 at (6, 14)
        boolean onClassroomExit = isOnTile(MAP_CLASSROOM, 6, 14);

        // Library (Map 3) - Doorway trim tile 152 at (8, 13)
        boolean onLibraryExit = isOnTile(MAP_LIBRARY, 8, 13);

        // Garden (Map 4) - Top concrete path (cols 8-10, row 6)
        boolean onGardenTopPath = isOnRowRange(MAP_GARDEN, 6, 8, 10);

        // Hometown (Map 5) - Sidewalk tile directly below metal gate (tile 225) & upper-right sidewalk edge
        int[] hometownGate = findSingleTile(MAP_HOMETOWN, 225);
        boolean onHometownHouseDoor = isOnTile(MAP_HOMETOWN, hometownGate[0], hometownGate[1] + 1);
        boolean onHometownRightPath = isOnTile(MAP_HOMETOWN, 16, 7) || isOnTile(MAP_HOMETOWN, 16, 8);

        // Bedroom (Map 6) - Exact floor tiles in front of doorway (row 10, cols 7-8)
        boolean onBedroomExit = isOnTile(MAP_BEDROOM, 7, 10) || isOnTile(MAP_BEDROOM, 8, 10);

        // Re-enable teleporting once player steps off all trigger tiles
        boolean onAnyTrigger = onCourtyardSchoolDoor || onCourtyardLeftPath || onCourtyardBottomPath
                || onHallwayBottomExit || onHallwayLibraryDoor || onHallwayClassroomDoor
                || onClassroomExit || onLibraryExit
                || onGardenTopPath || onHometownHouseDoor || onHometownRightPath || onBedroomExit;

        if (!onAnyTrigger) {
            canTouchEvent = true;
        }

        // --- 2. HANDLE TRANSITIONS ---
        if (canTouchEvent) {

            // COURTYARD <-> HALLWAY (Cols 16-17 <-> Cols 8-9)
            if (onCourtyardSchoolDoor && gp.player.direction.equals("up")) {
                int targetCol = isOnTile(MAP_COURTYARD, 16, 8) ? 8 : 9;
                teleport(MAP_HALLWAY, targetCol, 10, "up");
            } 
            else if (onHallwayBottomExit && gp.player.direction.equals("down")) {
                int targetCol = isOnTile(MAP_HALLWAY, 8, 11) ? 16 : 17;
                teleport(MAP_COURTYARD, targetCol, 9, "down");
            }

            // HALLWAY <-> LIBRARY (Left Brown Door)
            else if (onHallwayLibraryDoor && gp.player.direction.equals("up")) {
                teleport(MAP_LIBRARY, 8, 12, "up");
            } 
            else if (onLibraryExit && gp.player.direction.equals("down")) {
                teleport(MAP_HALLWAY, hallLeftBrownDoor[0], hallLeftBrownDoor[1] + 1, "down");
            }

            // HALLWAY <-> CLASSROOM (Right Brown Door)
            else if (onHallwayClassroomDoor && gp.player.direction.equals("up")) {
                teleport(MAP_CLASSROOM, 6, 13, "up");
            } 
            else if (onClassroomExit && gp.player.direction.equals("down")) {
                teleport(MAP_HALLWAY, hallRightBrownDoor[0], hallRightBrownDoor[1] + 1, "down");
            }

            // COURTYARD <-> HOMETOWN (Left Path)
            else if (onCourtyardLeftPath && gp.player.direction.equals("left")) {
                teleport(MAP_HOMETOWN, 15, 8, "left");
            } 
            else if (onHometownRightPath && gp.player.direction.equals("right")) {
                teleport(MAP_COURTYARD, courtyardLeftPath[0] + 1, courtyardLeftPath[1], "right");
            }

            // COURTYARD <-> GARDEN (Bottom Path - 1-to-1 Concrete Lane Alignment)
            else if (onCourtyardBottomPath && gp.player.direction.equals("down")) {
                int leftConcreteCol = findFirstTileColOnRow(MAP_COURTYARD, courtyardBottomPathRow, 4);
                int targetCol = 8 + (pCol - leftConcreteCol);
                teleport(MAP_GARDEN, targetCol, 7, "down");
            } 
            else if (onGardenTopPath && gp.player.direction.equals("up")) {
                int leftConcreteCol = findFirstTileColOnRow(MAP_COURTYARD, courtyardBottomPathRow, 4);
                int spawnCol = leftConcreteCol + (pCol - 8);
                teleport(MAP_COURTYARD, spawnCol, courtyardBottomPathRow - 1, "up");
            }

            // HOMETOWN <-> BEDROOM (House Gate)
            else if (onHometownHouseDoor && gp.player.direction.equals("up")) {
                teleport(MAP_BEDROOM, 7, 9, "up");
            } 
            else if (onBedroomExit && gp.player.direction.equals("down")) {
                teleport(MAP_HOMETOWN, hometownGate[0], hometownGate[1] + 2, "down");
            }
        }
    }

    // --- HELPER METHODS ---

    private int getPlayerCol() {
        return (gp.player.worldX + gp.player.solidArea.x + gp.player.solidArea.width / 2) / gp.tileSize;
    }

    private int getPlayerRow() {
        return (gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height / 2) / gp.tileSize;
    }

    public boolean isOnTile(int map, int col, int row) {
        if (gp.currentMap != map) return false;
        return (getPlayerCol() == col && getPlayerRow() == row);
    }

    public boolean isOnRowRange(int map, int row, int startCol, int endCol) {
        if (gp.currentMap != map) return false;
        int col = getPlayerCol();
        return (getPlayerRow() == row && col >= startCol && col <= endCol);
    }

    // Finds the first occurrence of a specific tile ID (e.g., metal gate 225)
    private int[] findSingleTile(int map, int tileId) {
        for (int row = 0; row < gp.maxWorldRow; row++) {
            for (int col = 0; col < gp.maxWorldCol; col++) {
                if (gp.tileM.mapTileNum[map][col][row] == tileId) {
                    return new int[]{col, row};
                }
            }
        }
        return new int[]{5, 10};
    }

    // Finds the leftmost (firstMatch = true) or rightmost (firstMatch = false) brown door mat (tile 38 or 43)
    private int[] findBrownDoorMat(int map, boolean firstMatch) {
        int[] result = {0, 0};
        for (int row = 0; row < gp.maxWorldRow; row++) {
            for (int col = 0; col < gp.maxWorldCol; col++) {
                int id = gp.tileM.mapTileNum[map][col][row];
                if (id == 38 || id == 43) {
                    result[0] = col;
                    result[1] = row;
                    if (firstMatch) return result;
                }
            }
        }
        return result;
    }

    // Finds the leftmost concrete path tile (tile 4) in the middle rows (rows 6-14)
    private int[] findLeftPathTile(int map) {
        for (int col = 0; col < gp.maxWorldCol; col++) {
            for (int row = 6; row <= 14; row++) {
                if (gp.tileM.mapTileNum[map][col][row] == 4) {
                    return new int[]{col, row};
                }
            }
        }
        return new int[]{4, 10};
    }

    // Finds the lowest (highest row index) occurrence of a tile ID (e.g., concrete path 4)
    private int findBottommostTileRow(int map, int tileId) {
        for (int row = gp.maxWorldRow - 1; row >= 0; row--) {
            for (int col = 0; col < gp.maxWorldCol; col++) {
                if (gp.tileM.mapTileNum[map][col][row] == tileId) {
                    return row;
                }
            }
        }
        return gp.maxWorldRow - 1;
    }

    // Finds the first column of a specific tile ID (e.g., concrete path 4) on a given row
    private int findFirstTileColOnRow(int map, int row, int tileId) {
        for (int col = 0; col < gp.maxWorldCol; col++) {
            if (gp.tileM.mapTileNum[map][col][row] == tileId) {
                return col;
            }
        }
        return 10;
    }

    public void teleport(int map, int col, int row, String direction) {
        gp.currentMap = map;
        gp.player.worldX = gp.tileSize * col;
        gp.player.worldY = gp.tileSize * row;
        gp.player.direction = direction;
        canTouchEvent = false;
    }
}