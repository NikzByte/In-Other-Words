package main;

import entity.Entity;

public class CollisionChecker {

    GamePanel gp;

    public CollisionChecker(GamePanel gp) {
        this.gp = gp;
    }

    public void checkTile(Entity entity) {

        int entityLeftWorldX = entity.worldX + entity.solidArea.x;
        int entityRightWorldX = entity.worldX + entity.solidArea.x + entity.solidArea.width;
        int entityTopWorldY = entity.worldY + entity.solidArea.y;
        int entityBottomWorldY = entity.worldY + entity.solidArea.y + entity.solidArea.height;

        int entityLeftCol = Math.floorDiv(entityLeftWorldX, gp.tileSize);
        int entityRightCol = Math.floorDiv(entityRightWorldX, gp.tileSize);
        int entityTopRow = Math.floorDiv(entityTopWorldY, gp.tileSize);
        int entityBottomRow = Math.floorDiv(entityBottomWorldY, gp.tileSize);

        switch (entity.direction) {
            case "up":
                entityTopRow = Math.floorDiv(entityTopWorldY - entity.speed, gp.tileSize);
                if (isTileSolid(entityLeftCol, entityTopRow) || isTileSolid(entityRightCol, entityTopRow)) {
                    entity.collisionOn = true;
                }
                break;

            case "down":
                entityBottomRow = Math.floorDiv(entityBottomWorldY + entity.speed, gp.tileSize);
                if (isTileSolid(entityLeftCol, entityBottomRow) || isTileSolid(entityRightCol, entityBottomRow)) {
                    entity.collisionOn = true;
                }
                break;

            case "left":
                entityLeftCol = Math.floorDiv(entityLeftWorldX - entity.speed, gp.tileSize);
                if (isTileSolid(entityLeftCol, entityTopRow) || isTileSolid(entityLeftCol, entityBottomRow)) {
                    entity.collisionOn = true;
                }
                break;

            case "right":
                entityRightCol = Math.floorDiv(entityRightWorldX + entity.speed, gp.tileSize);
                if (isTileSolid(entityRightCol, entityTopRow) || isTileSolid(entityRightCol, entityBottomRow)) {
                    entity.collisionOn = true;
                }
                break;
        }
    }

    private boolean isTileSolid(int col, int row) {
        if (col < 0 || col >= gp.maxWorldCol || row < 0 || row >= gp.maxWorldRow) {
            return true;
        }
        int tileNum = gp.tileM.mapTileNum[gp.currentMap][col][row];
        return gp.tileM.tile[tileNum] == null || gp.tileM.tile[tileNum].collision;
    }

    public int checkEntity(Entity entity, Entity[][] target) {
        int index = 999;

        for (int i = 0; i < target[gp.currentMap].length; i++) {
            Entity targetEntity = target[gp.currentMap][i];
            if (targetEntity != null && targetEntity != entity) {
                // Convert solid areas to world coordinates for intersection check
                entity.solidArea.x = entity.worldX + entity.solidArea.x;
                entity.solidArea.y = entity.worldY + entity.solidArea.y;

                targetEntity.solidArea.x = targetEntity.worldX + targetEntity.solidArea.x;
                targetEntity.solidArea.y = targetEntity.worldY + targetEntity.solidArea.y;

                switch (entity.direction) {
                    case "up":    entity.solidArea.y -= entity.speed; break;
                    case "down":  entity.solidArea.y += entity.speed; break;
                    case "left":  entity.solidArea.x -= entity.speed; break;
                    case "right": entity.solidArea.x += entity.speed; break;
                }

                if (entity.solidArea.intersects(targetEntity.solidArea)) {
                    entity.collisionOn = true;
                    index = i;
                }

                entity.solidArea.x = entity.solidAreaDefaultX;
                entity.solidArea.y = entity.solidAreaDefaultY;
                targetEntity.solidArea.x = targetEntity.solidAreaDefaultX;
                targetEntity.solidArea.y = targetEntity.solidAreaDefaultY;
            }
        }

        return index;
    }
}