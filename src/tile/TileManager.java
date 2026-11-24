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

        tile = new Tile[300];
        mapTileNum = new int[gp.maxMap][gp.maxWorldCol][gp.maxWorldRow];

        getTileImage();
        loadMap("/assets/maps/courtyard.txt", 0);
        loadMap("/assets/maps/hallway.txt", 1);
        loadMap("/assets/maps/classroom.txt", 2);
        loadMap("/assets/maps/library.txt", 3);
        loadMap("/assets/maps/garden.txt", 4);
        loadMap("/assets/maps/hometown.txt", 5);
        loadMap("/assets/maps/bedroom.txt", 6);
    }

    public void getTileImage() {

        // Void tile (0)
        setup(0, "voidTile", true);

        // Courtyard tiles (1-28)
        setup(1, "courtyardTile01", true);   // Tree
        setup(2, "courtyardTile02", true);   // Roof left
        setup(3, "courtyardTile03", true);   // Roof middle
        setup(4, "courtyardTile04", false);  // Concrete path
        setup(5, "courtyardTile05", false);  // Grass
        setup(6, "courtyardTile06", true);   // Roof bottom-left
        setup(7, "courtyardTile07", true);   // Roof bottom-middle
        setup(8, "courtyardTile08", true);   // Wall left
        setup(9, "courtyardTile09", true);   // Wall window
        setup(10, "courtyardTile10", true);  // Wall window
        setup(11, "courtyardTile11", true);  // Wall window
        setup(12, "courtyardTile12", true);  // Wall window
        setup(13, "courtyardTile13", true);  // Wall left
        setup(14, "courtyardTile14", true);  // Wall window
        setup(15, "courtyardTile15", true);  // Wall window
        setup(16, "courtyardTile16", true);  // Wall window
        setup(17, "courtyardTile17", true);  // Wall window
        setup(18, "courtyardTile18", true);  // Wall bottom-left
        setup(19, "courtyardTile19", true);  // Wall door left
        setup(20, "courtyardTile20", true);  // Wall door right
        setup(21, "courtyardTile21", true);  // Wall bottom
        setup(22, "courtyardTile22", true);  // Wall bottom-right
        setup(23, "courtyardTile23", true);  // Bulletin board bottom-left
        setup(24, "courtyardTile24", true);  // Bulletin board bottom-right
        setup(25, "courtyardTile25", true);  // Bulletin board top-left
        setup(26, "courtyardTile26", true);  // Bulletin board top-right
        setup(27, "courtyardTile27", true);  // Bench left
        setup(28, "courtyardTile28", true);  // Bench right

        // School Hallway tiles (29-61)
        setup(29, "schoolHallwayTile01", true);  // Wall top-left
        setup(30, "schoolHallwayTile02", true);  // Wall window top
        setup(31, "schoolHallwayTile03", true);  // Wall notice board
        setup(32, "schoolHallwayTile04", true);  // Wall plain
        setup(33, "schoolHallwayTile05", true);  // Wall door top-left
        setup(34, "schoolHallwayTile06", true);  // Wall door top-right
        setup(35, "schoolHallwayTile07", true);  // Wall room sign (CS 2101)
        setup(36, "schoolHallwayTile08", true);  // Wall top-right
        setup(37, "schoolHallwayTile09", true);  // Wall base left
        setup(38, "schoolHallwayTile10", false); // Wall window bottom
        setup(39, "schoolHallwayTile11", false); // Wall base middle
        setup(40, "schoolHallwayTile12", false); // Wall door bottom-left
        setup(41, "schoolHallwayTile13", false); // Wall door bottom-right
        setup(42, "schoolHallwayTile14", false); // Wall base middle
        setup(43, "schoolHallwayTile15", false); // Wall window bottom
        setup(44, "schoolHallwayTile16", true);  // Wall base right
        setup(45, "schoolHallwayTile17", true);  // Floor left edge
        setup(46, "schoolHallwayTile18", false); // Floor plain
        setup(47, "schoolHallwayTile19", true);  // Floor right edge
        setup(48, "schoolHallwayTile20", true);  // Bottom wall top-left
        setup(49, "schoolHallwayTile21", true);  // Bottom wall top
        setup(50, "schoolHallwayTile22", true);  // Bottom wall inner-left
        setup(51, "schoolHallwayTile23", false); // Floor plain
        setup(52, "schoolHallwayTile24", false); // Floor plain
        setup(53, "schoolHallwayTile25", true);  // Bottom wall inner-right
        setup(54, "schoolHallwayTile26", true);  // Bottom wall top-right
        setup(55, "schoolHallwayTile27", true);  // Bottom wall plain
        setup(56, "schoolHallwayTile28", true);  // Bottom wall plain
        setup(57, "schoolHallwayTile29", true);  // Bottom wall right edge
        setup(58, "schoolHallwayTile30", false); // Floor bottom door-left
        setup(59, "schoolHallwayTile31", false); // Floor bottom door-right
        setup(60, "schoolHallwayTile32", true);  // Bottom wall right edge
        setup(61, "schoolHallwayTile33", true);  // Bottom wall corner-right

        // Classroom tiles (62-95)
        setup(62, "classroomMap01", true);  // Wall corner
        setup(63, "classroomMap02", true);  // Wall horizontal
        setup(64, "classroomMap03", true);  // Wall corner
        setup(65, "classroomMap04", true);  // Wall vertical
        setup(66, "classroomMap05", true);  // Wall section
        setup(67, "classroomMap06", true);  // Green board
        setup(68, "classroomMap07", true);  // Green board with writing
        setup(69, "classroomMap08", true);  // Green board side
        setup(70, "classroomMap09", true);  // Bulletin board
        setup(71, "classroomMap10", true);  // Window
        setup(72, "classroomMap11", true);  // Wall window side
        setup(73, "classroomMap12", true);  // Wall door section
        setup(74, "classroomMap13", true);  // Wall upper section
        setup(75, "classroomMap14", true);  // Wall upper section
        setup(76, "classroomMap15", true);  // Wall upper section
        setup(77, "classroomMap16", true);  // Wall upper section
        setup(78, "classroomMap17", true);  // Wall upper section
        setup(79, "classroomMap18", true);  // Wall lower section
        setup(80, "classroomMap19", true);  // Wall door section
        setup(81, "classroomMap20", true);  // Wall vertical section
        setup(82, "classroomMap21", false); // Plain floor
        setup(83, "classroomMap22", true);  // Furniture side
        setup(84, "classroomMap23", true);  // Furniture front
        setup(85, "classroomMap24", true);  // Furniture side
        setup(86, "classroomMap25", true);  // Wall vertical section
        setup(87, "classroomMap26", true);  // Teacher's table
        setup(88, "classroomMap27", true);  // Student chair
        setup(89, "classroomMap28", true);  // Student table
        setup(90, "classroomMap29", true);  // Student chair
        setup(91, "classroomMap30", true);  // Bottom-left wall corner
        setup(92, "classroomMap31", false); // Bottom doorway / floor transition
        setup(93, "classroomMap32", true);  // Bottom wall horizontal
        setup(94, "classroomMap33", true);  // Bottom wall horizontal
        setup(95, "classroomMap34", true);  // Bottom-right wall corner

        // Library tiles (96-161)
        setup(96, "libraryTileMap01", true);   // Top-left wall corner
        setup(97, "libraryTileMap02", true);   // Top wall horizontal
        setup(98, "libraryTileMap03", true);   // Top wall horizontal
        setup(99, "libraryTileMap04", true);   // Top wall horizontal
        setup(100, "libraryTileMap05", true);  // Top wall horizontal
        setup(101, "libraryTileMap06", true);  // Top wall horizontal
        setup(102, "libraryTileMap07", true);  // Top wall horizontal
        setup(103, "libraryTileMap08", true);  // Top-right wall corner
        setup(104, "libraryTileMap09", true);  // Left wall vertical
        setup(105, "libraryTileMap10", true);  // Bookshelf upper
        setup(106, "libraryTileMap11", true);  // Cabinet upper
        setup(107, "libraryTileMap12", true);  // Bookshelf upper
        setup(108, "libraryTileMap13", true);  // Wall plain
        setup(109, "libraryTileMap14", true);  // Bookshelf upper
        setup(110, "libraryTileMap15", true);  // Cabinet upper
        setup(111, "libraryTileMap16", true);  // Bookshelf upper
        setup(112, "libraryTileMap17", true);  // Right wall vertical
        setup(113, "libraryTileMap18", true);  // Left wall vertical
        setup(114, "libraryTileMap19", true);  // Bookshelf middle
        setup(115, "libraryTileMap20", true);  // Cabinet middle
        setup(116, "libraryTileMap21", true);  // Bookshelf middle
        setup(117, "libraryTileMap22", false); // Wall base / floor transition
        setup(118, "libraryTileMap23", true);  // Bookshelf middle
        setup(119, "libraryTileMap24", true);  // Cabinet middle
        setup(120, "libraryTileMap25", true);  // Bookshelf middle
        setup(121, "libraryTileMap26", true);  // Right wall vertical
        setup(122, "libraryTileMap27", true);  // Left wall vertical
        setup(123, "libraryTileMap28", true);  // Chair / small desk
        setup(124, "libraryTileMap29", true);  // Chair / small desk
        setup(125, "libraryTileMap30", false); // Floor with wall trim
        setup(126, "libraryTileMap31", false); // Plain floor
        setup(127, "libraryTileMap32", true);  // Chair / small desk
        setup(128, "libraryTileMap33", true);  // Chair / small desk
        setup(129, "libraryTileMap34", true);  // Right wall vertical
        setup(130, "libraryTileMap35", true);  // Counter / bench left
        setup(131, "libraryTileMap36", true);  // Counter / bench right
        setup(132, "libraryTileMap37", true);  // Left wall vertical
        setup(133, "libraryTileMap38", true);  // Chair / small desk
        setup(134, "libraryTileMap39", true);  // Chair / small desk
        setup(135, "libraryTileMap40", true);  // Chair / small desk
        setup(136, "libraryTileMap41", true);  // Right wall vertical
        setup(137, "libraryTileMap42", true);  // Bookshelf lower
        setup(138, "libraryTileMap43", true);  // Left wall with shelf
        setup(139, "libraryTileMap44", true);  // Bookshelf lower
        setup(140, "libraryTileMap45", true);  // Cabinet lower
        setup(141, "libraryTileMap46", true);  // Bookshelf lower
        setup(142, "libraryTileMap47", true);  // Right wall vertical
        setup(143, "libraryTileMap48", true);  // Left wall vertical
        setup(144, "libraryTileMap49", true);  // Bookshelf base
        setup(145, "libraryTileMap50", false); // Floor with left trim
        setup(146, "libraryTileMap51", true);  // Bookshelf base
        setup(147, "libraryTileMap52", true);  // Cabinet base
        setup(148, "libraryTileMap53", true);  // Bookshelf base
        setup(149, "libraryTileMap54", true);  // Right wall vertical
        setup(150, "libraryTileMap55", true);  // Left wall vertical
        setup(151, "libraryTileMap56", false); // Floor with bottom trim
        setup(152, "libraryTileMap57", false); // Floor with top-left trim
        setup(153, "libraryTileMap58", false); // Floor with top trim
        setup(154, "libraryTileMap59", false); // Floor with doorway trim
        setup(155, "libraryTileMap60", false); // Floor with bottom-right trim
        setup(156, "libraryTileMap61", true);  // Right wall vertical
        setup(157, "libraryTileMap62", true);  // Bottom-left wall corner
        setup(158, "libraryTileMap63", true);  // Bottom wall horizontal
        setup(159, "libraryTileMap64", true);  // Bottom wall horizontal
        setup(160, "libraryTileMap65", true);  // Bottom wall horizontal
        setup(161, "libraryTileMap66", true);  // Bottom-right wall corner

        // Garden tiles (162-186)
        setup(162, "gardenMap01", true);   // Tree
        setup(163, "gardenMap02", false);  // Concrete path
        setup(164, "gardenMap03", false);  // Plain grass
        setup(165, "gardenMap04", false);  // Grass border top-left
        setup(166, "gardenMap05", false);  // Grass border top
        setup(167, "gardenMap06", false);  // Grass border top
        setup(168, "gardenMap07", false);  // Grass border top-right
        setup(169, "gardenMap08", false);  // Grass border bottom-left
        setup(170, "gardenMap09", false);  // Grass with flowers top-right
        setup(171, "gardenMap10", false);  // Grass with flowers top-left
        setup(172, "gardenMap11", false);  // Grass with flowers top-right
        setup(173, "gardenMap12", false);  // Grass border bottom-left
        setup(174, "gardenMap13", false);  // Grass with flowers top-left
        setup(175, "gardenMap14", true);   // Bench left
        setup(176, "gardenMap15", true);   // Bench middle
        setup(177, "gardenMap16", true);   // Bench right
        setup(178, "gardenMap17", false);  // Grass with flowers top-right
        setup(179, "gardenMap18", false);  // Grass border left
        setup(180, "gardenMap19", false);  // Grass with flowers left
        setup(181, "gardenMap20", false);  // Grass with scattered flowers
        setup(182, "gardenMap21", false);  // Grass border right
        setup(183, "gardenMap22", false);  // Grass border top-right
        setup(184, "gardenMap23", false);  // Grass border top
        setup(185, "gardenMap24", false);  // Grass border top
        setup(186, "gardenMap25", false);  // Grass border top-right

        // Hometown tiles (187-249)
        setup(187, "hometownMap01", true);  // Tricycle / vehicle part on grass
        setup(188, "hometownMap02", true);  // Tricycle / vehicle part on grass
        setup(189, "hometownMap03", true);  // Tricycle / vehicle part on grass
        setup(190, "hometownMap04", true);  // Tree
        setup(191, "hometownMap05", true);  // Building / stall corner
        setup(192, "hometownMap06", true);  // Fence / gate on grass
        setup(193, "hometownMap07", true);  // Fence / wall corner on grass
        setup(194, "hometownMap08", true);  // Wooden bench / counter
        setup(195, "hometownMap09", true);  // Wooden bench / gate gap
        setup(196, "hometownMap10", true);  // Wooden bench / counter
        setup(197, "hometownMap11", true);  // Fence / wall edge on grass
        setup(198, "hometownMap12", true);  // Roof / wall corner
        setup(199, "hometownMap13", true);  // Roof / wall section
        setup(200, "hometownMap14", true);  // Roof / wall corner
        setup(201, "hometownMap15", true);  // Roof / wall section
        setup(202, "hometownMap16", true);  // Plain roof / wall
        setup(203, "hometownMap17", true);  // Plain roof / wall
        setup(204, "hometownMap18", false); // Concrete / sidewalk tile
        setup(205, "hometownMap19", true);  // Green roof corner
        setup(206, "hometownMap20", true);  // Green roof section
        setup(207, "hometownMap21", true);  // Green roof edge
        setup(208, "hometownMap22", true);  // Green roof corner
        setup(209, "hometownMap23", true);  // Red roof section
        setup(210, "hometownMap24", true);  // Red roof section
        setup(211, "hometownMap25", true);  // House wall with window
        setup(212, "hometownMap26", true);  // House wall with window
        setup(213, "hometownMap27", true);  // House wall section
        setup(214, "hometownMap28", true);  // House wall with door/vent
        setup(215, "hometownMap29", true);  // Sari-sari store sign left
        setup(216, "hometownMap30", true);  // Sari-sari store sign right
        setup(217, "hometownMap31", false); // Crosswalk / striped pavement
        setup(218, "hometownMap32", false); // Crosswalk / striped pavement
        setup(219, "hometownMap33", true);  // Gray building wall
        setup(220, "hometownMap34", true);  // Building wall with blue window
        setup(221, "hometownMap35", true);  // Green wall section
        setup(222, "hometownMap36", true);  // Green wall section
        setup(223, "hometownMap37", true);  // Gray wall edge
        setup(224, "hometownMap38", true);  // Tree
        setup(225, "hometownMap39", true);  // Brick / patterned wall
        setup(226, "hometownMap40", true);  // Brick / patterned wall
        setup(227, "hometownMap41", true);  // Metal gate / bars
        setup(228, "hometownMap42", true);  // Brick / patterned wall
        setup(229, "hometownMap43", true);  // Sari-sari store counter left
        setup(230, "hometownMap44", true);  // Sari-sari store counter right
        setup(231, "hometownMap45", false); // Crosswalk / striped pavement
        setup(232, "hometownMap46", false); // Crosswalk / striped pavement
        setup(233, "hometownMap47", true);  // White building wall
        setup(234, "hometownMap48", true);  // Building window / signage
        setup(235, "hometownMap49", true);  // Storefront / shutter wall
        setup(236, "hometownMap50", true);  // Storefront / shutter wall
        setup(237, "hometownMap51", true);  // White wall / doorway trim
        setup(238, "hometownMap52", false); // Dark asphalt road
        setup(239, "hometownMap53", false); // Road edge / curb
        setup(240, "hometownMap54", false); // Road edge / curb
        setup(241, "hometownMap55", false); // Road edge / curb
        setup(242, "hometownMap56", false); // Road edge / curb
        setup(243, "hometownMap57", false); // Road edge / curb
        setup(244, "hometownMap58", false); // Road edge / curb
        setup(245, "hometownMap59", false); // Road edge / curb
        setup(246, "hometownMap60", false); // Road edge / curb
        setup(247, "hometownMap61", false); // Road edge / curb
        setup(248, "hometownMap62", false); // Road edge / curb
        setup(249, "hometownMap63", false); // Road edge / curb

        // Bedroom tiles (250-277)
        setup(250, "bedroomMap01", true);  // Top-left wall corner
        setup(251, "bedroomMap02", true);  // Top wall plain
        setup(252, "bedroomMap03", true);  // Top wall window upper
        setup(253, "bedroomMap04", true);  // Top-right wall corner
        setup(254, "bedroomMap05", true);  // Left wall vertical
        setup(255, "bedroomMap06", true);  // Right wall vertical
        setup(256, "bedroomMap07", true);  // Wall with bed headboard
        setup(257, "bedroomMap08", true);  // Wall window lower
        setup(258, "bedroomMap09", true);  // Wall with wardrobe upper-left
        setup(259, "bedroomMap10", true);  // Wall with wardrobe upper-right
        setup(260, "bedroomMap11", true);  // Wall with desk upper
        setup(261, "bedroomMap12", true);  // Left wall vertical
        setup(262, "bedroomMap13", true);  // Bed middle / pillow
        setup(263, "bedroomMap14", false); // Plain floor
        setup(264, "bedroomMap15", true);  // Wardrobe lower-left
        setup(265, "bedroomMap16", true);  // Wardrobe lower-right
        setup(266, "bedroomMap17", true);  // Desk / chair lower
        setup(267, "bedroomMap18", true);  // Bed foot
        setup(268, "bedroomMap19", true);  // Small stool / nightstand
        setup(269, "bedroomMap20", false); // Rug left
        setup(270, "bedroomMap21", false); // Rug middle
        setup(271, "bedroomMap22", false); // Rug right
        setup(272, "bedroomMap23", true);  // Bottom-left wall corner
        setup(273, "bedroomMap24", true);  // Bottom wall horizontal
        setup(274, "bedroomMap25", false); // Bottom wall doorway
        setup(275, "bedroomMap26", true);  // Bottom wall horizontal
        setup(276, "bedroomMap27", true);  // Bottom wall horizontal
        setup(277, "bedroomMap28", true);  // Bottom-right wall corner
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