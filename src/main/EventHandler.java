package main;

public class EventHandler {

    GamePanel gp;
    boolean canTouchEvent = true;

    public EventHandler(GamePanel gp) {
        this.gp = gp;
    }

    public void checkEvent() {
        // Check if player is currently standing on ANY teleport tile
        boolean onMap0Exit = isOnTile(0, 19, 7);
        boolean onMap1Exit = isOnTile(1, 0, 7);

        // Only re-enable teleporting once the player has completely left both exit tiles
        if (!onMap0Exit && !onMap1Exit) {
            canTouchEvent = true;
        }

        if (canTouchEvent) {
            // Map 0 (map01) right dirt path -> spawns 1 tile inward on Map 1 (courtyard)
            if (onMap0Exit && gp.player.direction.equals("right")) {
                teleport(1, 1, 7);
            }
            // Map 1 (courtyard) left dirt path -> spawns 1 tile inward on Map 0 (map01)
            else if (onMap1Exit && gp.player.direction.equals("left")) {
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