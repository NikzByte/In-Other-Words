package tile;

import main.GamePanel;
import main.UtilityTool;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TileManager {

    GamePanel gp;
    public Tile[] tile;
    public int mapTileNum[][][]; // [mapNum][mapCol][mapRow]

    public TileManager(GamePanel gp) {
        this.gp = gp;

        tile = new Tile[50];
        mapTileNum = new int[gp.maxMap][gp.maxWorldCol][gp.maxWorldRow];

        getTileImage();
        loadMap("/assets/maps/map01.txt", 0); // Pass file path and map index
        loadMap("/assets/maps/courtyard.txt", 1);


    }

    public void getTileImage() {
        
        // Original map01 tiles (0-3)
        setup(0, "grassTile", false);
        setup(1, "dirtPathTile", false);
        setup(2, "waterTile", true);
        setup(3, "treeTile", true);

        // Courtyard tiles (10-37)
        setup(10, "courtyardTile01", true);  // Tree
        setup(11, "courtyardTile02", true);  // Roof left
        setup(12, "courtyardTile03", true);  // Roof middle
        setup(13, "courtyardTile04", false); // Concrete path
        setup(14, "courtyardTile05", false); // Grass
        setup(15, "courtyardTile06", true);  // Roof bottom-left
        setup(16, "courtyardTile07", true);  // Roof bottom-middle
        setup(17, "courtyardTile08", true);  // Wall left
        setup(18, "courtyardTile09", true);  // Wall window
        setup(19, "courtyardTile10", true);  // Wall window
        setup(20, "courtyardTile11", true);  // Wall window
        setup(21, "courtyardTile12", true);  // Wall window
        setup(22, "courtyardTile13", true);  // Wall left
        setup(23, "courtyardTile14", true);  // Wall window
        setup(24, "courtyardTile15", true);  // Wall window
        setup(25, "courtyardTile16", true);  // Wall window
        setup(26, "courtyardTile17", true);  // Wall window
        setup(27, "courtyardTile18", true);  // Wall bottom-left
        setup(28, "courtyardTile19", true);  // Wall door left
        setup(29, "courtyardTile20", true);  // Wall door right
        setup(30, "courtyardTile21", true);  // Wall bottom
        setup(31, "courtyardTile22", true);  // Wall bottom-right
        setup(32, "courtyardTile23", true);  // Bulletin board bottom-left
        setup(33, "courtyardTile24", true);  // Bulletin board bottom-right
        setup(34, "courtyardTile25", true);  // Bulletin board top-left
        setup(35, "courtyardTile26", true);  // Bulletin board top-right
        setup(36, "courtyardTile27", true);  // Bench left
        setup(37, "courtyardTile28", true);  // Bench right
    }
        

    public void setup(int index, String imagePath, boolean collision) {
        UtilityTool uTool = new UtilityTool();

        try {
            tile[index] = new Tile();
            tile[index].image = ImageIO.read(getClass().getResourceAsStream("/assets/tiles/temp/" + imagePath + ".png"));
            tile[index].image = uTool.scaleImage(tile[index].image, gp.tileSize, gp.tileSize);
            tile[index].collision = collision;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // MAP PARSING
    public void loadMap(String filePath, int map) {
        try {
            InputStream is = getClass().getResourceAsStream(filePath);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            int col = 0;
            int row = 0;

            while (col < gp.maxWorldCol && row < gp.maxWorldRow) {
                String line = br.readLine();
                if (line == null) break;

                String numbers[] = line.split(" ");

                while (col < gp.maxWorldCol) {
                    int num = Integer.parseInt(numbers[col]);
                    mapTileNum[map][col][row] = num;
                    col++;
                }

                if (col == gp.maxWorldCol) {
                    col = 0;
                    row++;
                }
            }
            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // RENDERING
    public void draw(Graphics2D g2) {
        int worldCol = 0;
        int worldRow = 0;

        while (worldCol < gp.maxWorldCol && worldRow < gp.maxWorldRow) {

            int tileNum = mapTileNum[gp.currentMap][worldCol][worldRow];

            int worldX = worldCol * gp.tileSize;
            int worldY = worldRow * gp.tileSize;
            int screenX = worldX - gp.player.worldX + gp.player.screenX;
            int screenY = worldY - gp.player.worldY + gp.player.screenY;

            // Render optimization: Only draw tiles within player screen boundaries
            if (worldX + gp.tileSize > gp.player.worldX - gp.player.screenX &&
                worldX - gp.tileSize < gp.player.worldX + gp.player.screenX &&
                worldY + gp.tileSize > gp.player.worldY - gp.player.screenY &&
                worldY - gp.tileSize < gp.player.worldY + gp.player.screenY) {

                g2.drawImage(tile[tileNum].image, screenX, screenY, null);
            }

            worldCol++;

            if (worldCol == gp.maxWorldCol) {
                worldCol = 0;
                worldRow++;
            }
        }
    }
}