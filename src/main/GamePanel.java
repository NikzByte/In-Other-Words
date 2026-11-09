package main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import entity.Player;
import tile.TileManager;

public class GamePanel extends JPanel implements Runnable  {
    
    // SCREEN SETTINGS
    final int originalTileSize = 32; // 32x32 tile
    final int scale = 3;

    public final int tileSize = originalTileSize * scale; // 96x96
    public final int maxScreenCol = 10;
    public final int maxScreenRow = 6;
    public final int screenWidth = tileSize * maxScreenCol; // 864px
    public final int screenHeight = tileSize * maxScreenRow; // 576px

    // WORLD SETTINGS
    public final int maxWorldCol = 20;
    public final int maxWorldRow = 20;
    public final int worldWidth = tileSize * maxWorldCol;
    public final int worldHeight = tileSize * maxWorldRow;


    TileManager tileM = new TileManager(this);
    KeyHandler keyH = new KeyHandler();
    Thread gameThread; // keeps the program running until stopped
    public Player player = new Player(this, keyH);

    // set player's default position
    int playerX = 100;
    int playerY = 100;
    int playerSpeed = 4;
    int FPS = 60;

    public GamePanel() {
        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.black);
        this.setDoubleBuffered(true);
        this.addKeyListener(keyH);
        this.setFocusable(true); // GamePanel can be "focused" to receive keyboard input
    }

    public void startGameThread() {
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override 
    public void run() {

        double drawInterval = 1000000000/FPS;
        double nextDrawTime = System.nanoTime() + drawInterval;

        while(gameThread != null) { // while the  game is running ...
            update(); // update information such as character postion
            repaint();  // draw the screen with the updated information
            
            try {
            double remainingTime = nextDrawTime - System.nanoTime();
            remainingTime = remainingTime/1000000;
            

            if (remainingTime < 0) {
                remainingTime = 0;
            }
            Thread.sleep((long)remainingTime);

            nextDrawTime += drawInterval;

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public void update() {
        player.update();
    }
    public void paintComponent(Graphics g) { // Graphics - a class that has many functions to draw objects on the screen
        
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D)g;

        // by layer
        tileM.draw(g2);
        player.draw(g2);

        g2.dispose(); // dispose of this graphics context and release any system resources that it is using
    }
}
