package main;

public class EventHandler {

    GamePanel gp;
    boolean canTouchEvent = true;

    public EventHandler(GamePanel gp) {
        this.gp = gp;
    }

    public void checkEvent() {
        // Map 0 (map01) exit tile
        boolean onMap0RightExit = isOnTile(0, 19, 7);

        // Map 1 (courtyard) right path end (col 19, rows 9-10)
        boolean onCourtyardRightExit = isOnTile(1, 19, 9) || isOnTile(1, 19, 10);

        // Map 1 (courtyard) bottom path end (row 15, cols 7-9)
        boolean onCourtyardBottomExit = isOnTile(1, 7, 15) || isOnTile(1, 8, 15) || isOnTile(1, 9, 15);

        // Re-enable teleporting once the player leaves all exit tiles
        if (!onMap0RightExit && !onCourtyardRightExit && !onCourtyardBottomExit) {
            canTouchEvent = true;
        }

        if (canTouchEvent) {
            // Map 0 (map01) right path -> Courtyard right path (spawns 1 tile inward at col 18, row 9)
            if (onMap0RightExit && gp.player.direction.equals("right")) {
                teleport(1, 18, 9);
            }
            // Courtyard right path -> Map 0 (spawns 1 tile inward at col 18, row 7)
            else if (onCourtyardRightExit && gp.player.direction.equals("right")) {
                teleport(0, 18, 7);
            }
            // Courtyard bottom path -> Map 0 (spawns at col 18, row 7)
            else if (onCourtyardBottomExit && gp.player.direction.equals("down")) {
                teleport(0, 18, 7);
            }
        }
    }

    public boolean isOnTile(int map, int col, int row) {
        if (gp.currentMap != map) {
            return false;
        }

        int playerCol = (gp.player.worldX + gp.player.solidArea.x + gp.player.solidArea.width / 2) / gp.tileSize;
        int playerRow = (gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height / 2) / gp.tileSize;

        return (playerCol == col && playerRow == row);
    }

    public void teleport(int map, int col, int row) {
        gp.currentMap = map;
        gp.player.worldX = gp.tileSize * col;
        gp.player.worldY = gp.tileSize * row;
        canTouchEvent = false;
    }
}