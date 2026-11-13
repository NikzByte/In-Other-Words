package main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JPanel;

import data.FileManager;
import data.GameProgress;
import data.VocabularyEntry;
import entity.Player;
import tile.TileManager;

public class GamePanel extends JPanel implements Runnable {

    private enum Screen { TITLE, GAME, DICTIONARY, SETTINGS, SAVES }
    private Screen screen = Screen.TITLE;
    private Screen returnScreen = Screen.GAME;
    private final Color ink = new Color(0x2B, 0x19, 0x3D);
    private final Color cream = new Color(0xF6, 0xE9, 0xC7);
    private final Color pink = new Color(0xFF, 0xB2, 0xC2);
    private final Color raspberryPlum = new Color(0xBD, 0x2D, 0x87);
    private final Color babyBlueIce = new Color(0x9A, 0xC4, 0xF8);
    private final Color softYellow = new Color(0xF2, 0xDE, 0xA5);
    private final GameProgress progress = new GameProgress();
    private final FileManager files = new FileManager(progress);
    private boolean talking;
    private boolean showAbout;
    private String notice = "";
    private long noticeExpiresAt;
    private List<VocabularyEntry> vocabulary = List.of();
    private String dictionaryFilter = "All";
    private String searchText = "";
    private int textSpeed = 50;
    private int autoForward = 30;
    private int musicVolume = 70;
    private int soundVolume = 80;
    private boolean showHints = true;
    private boolean skipUnseen = true;
    private boolean fullscreen;
    private boolean skipAfterChoices;
    private boolean muteAll;
    private String typedReply = "";
    private boolean hideOverworld;
    private BufferedImage dialogueBackground;
    private int pointerX = -1;
    private int pointerY = -1;
    private long animationFrame;
    private double displayedAffection;
    
    private static final int ORIGINAL_TILE_SIZE = 32;
    private static final int TILE_SCALE = 3;

    public final int tileSize = ORIGINAL_TILE_SIZE * TILE_SCALE;
    public final int maxScreenCol = 10;
    public final int maxScreenRow = 6;
    public final int screenWidth = tileSize * maxScreenCol;
    public final int screenHeight = tileSize * maxScreenRow;

    public final int maxWorldCol = 20;
    public final int maxWorldRow = 20;
    final TileManager tileM = new TileManager(this);
    private final KeyHandler keyH = new KeyHandler();
    private Thread gameThread;
    public CollisionChecker cChecker = new CollisionChecker(this);
    public Player player = new Player(this, keyH);

    private static final int FPS = 60;

    public GamePanel() {
        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(cream);
        this.setDoubleBuffered(true);
        this.addKeyListener(keyH);
        this.setFocusable(true);
        try {
            vocabulary = files.loadVocabulary("vocabulary.tsv");
        } catch (IOException ignored) {
            // The dictionary can still show words learned during play.
        }
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                pointerX = e.getX() * 960 / Math.max(1, getWidth());
                pointerY = e.getY() * 576 / Math.max(1, getHeight());
                repaint();
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int code=e.getKeyCode();
                if(code==KeyEvent.VK_ESCAPE&&screen!=Screen.TITLE){if(screen==Screen.GAME&&talking)talking=false;else screen=returnScreen;}
                else if(code==KeyEvent.VK_ENTER&&screen==Screen.TITLE)screen=Screen.GAME;
                else if(code==KeyEvent.VK_E&&screen==Screen.GAME&&!talking)talking=true;
                else if(e.isControlDown()&&code==KeyEvent.VK_S)saveGame();
                else if(e.isControlDown()&&code==KeyEvent.VK_L)loadGame();
                else if(talking&&progress.getCurrentStage()<=2&&code>=KeyEvent.VK_1&&code<=KeyEvent.VK_3)submitDialogueChoice(code-KeyEvent.VK_1);
                else if(talking&&progress.getCurrentStage()>=3&&code==KeyEvent.VK_ENTER)submitTypedReply();
                repaint();
            }
            @Override public void keyTyped(KeyEvent e) {
                if (screen == Screen.DICTIONARY) {
                    char c=e.getKeyChar();
                    if (c=='\b' && !searchText.isEmpty()) searchText=searchText.substring(0,searchText.length()-1);
                    else if (!Character.isISOControl(c) && searchText.length()<40) searchText+=c;
                    repaint();
                } else if (screen == Screen.GAME && talking && progress.getCurrentStage() >= 3) {
                    char c=e.getKeyChar();
                    if(c=='\b'&&!typedReply.isEmpty())typedReply=typedReply.substring(0,typedReply.length()-1);
                    else if(!Character.isISOControl(c)&&typedReply.length()<90)typedReply+=c;
                    repaint();
                }
            }
        });
    }

    public void startGameThread() {
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override 
    public void run() {

        double drawInterval = 1000000000/FPS;
        double nextDrawTime = System.nanoTime() + drawInterval;

        while(gameThread != null) {
            update();
            repaint();
            
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
        animationFrame++;
        if(!notice.isEmpty()&&System.currentTimeMillis()>=noticeExpiresAt)notice="";
        displayedAffection+=(progress.getAffectionScore()-displayedAffection)*0.12;
        if(Math.abs(progress.getAffectionScore()-displayedAffection)<0.02)displayedAffection=progress.getAffectionScore();
        if (screen == Screen.GAME && !talking) player.update();
    }
    public void paintComponent(Graphics g) {
        
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D)g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (screen == Screen.GAME) {
            tileM.draw(g2);
            player.draw(g2);
            drawGameUi(g2);
        } else {
            g2.setColor(cream); g2.fillRect(0, 0, getWidth(), getHeight());
            switch (screen) {
                case TITLE -> drawTitle(g2);
                case DICTIONARY -> drawDictionary(g2);
                case SETTINGS -> drawSettings(g2);
                case SAVES -> drawSaves(g2);
                default -> { }
            }
        }
        drawNotice(g2);

        g2.dispose();
    }

    private void drawTitle(Graphics2D g) {
        int w=getWidth(),h=getHeight();double sx=w/960.0,sy=h/576.0;
        Graphics2D ui=(Graphics2D)g.create();ui.scale(sx,sy);ui.setColor(cream);ui.fillRect(0,0,960,576);
        panel(ui,58,38,525,162,pink,20);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.BOLD,48));ui.drawString("In Other Words",73,108);
        ui.setColor(raspberryPlum);ui.setFont(new Font("SansSerif",Font.BOLD,31));ui.drawString("Mahal Kita",74,151);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,17));ui.drawString("A language-learning story",74,181);
        int heartSize=20+(int)(2*Math.sin(animationFrame/8.0));ui.setColor(raspberryPlum);ui.setFont(new Font("SansSerif",Font.PLAIN,heartSize));ui.drawString("♥",545,75);
        String[] labels={"Start","Load","Dictionary","Settings","About","Quit"};
        for(int i=0;i<labels.length;i++)button(ui,78,214+i*50,261,42,labels[i],i==0?pink:(i<4?softYellow:cream));
        dashedPanel(ui,432,38,500,468,cream,20);
        if(showAbout){
            ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.BOLD,25));drawCentered(ui,"About the game",682,219);
            ui.setFont(new Font("SansSerif", Font.PLAIN, 18));
            drawCentered(ui, "In Other Words blends romance,", 682, 258);
            drawCentered(ui, "campus exploration, and language", 682, 286);
            drawCentered(ui, "learning through an interactive story.", 682, 314);
            ui.setFont(new Font("SansSerif",Font.PLAIN,16));drawCentered(ui,"Meet classmates who speak Bisaya,",682,360);drawCentered(ui,"Ilokano, and Bicolano.",682,386);
        }else{
            ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.BOLD,23));drawCentered(ui,"Your story starts on campus",682,213);
            ui.setFont(new Font("SansSerif", Font.PLAIN, 19));
            drawCentered(ui, "Meet three classmates, learn their", 682, 258);
            drawCentered(ui, "languages, and let your choices", 682, 286);
            drawCentered(ui, "shape the story you share.", 682, 314);
            ui.setFont(new Font("SansSerif",Font.PLAIN,16));drawCentered(ui,"Bisaya  •  Ilokano  •  Bicolano",682,361);
        }
        ui.dispose();
    }

    private void drawGameUi(Graphics2D g) {
        int w=getWidth(), h=getHeight();
        panel(g, 14, 12, 236, 54, cream, 12);
        g.setColor(ink); g.setFont(new Font("SansSerif",Font.BOLD,17)); g.drawString("Campus",28,35);
        g.setFont(new Font("SansSerif",Font.PLAIN,14)); g.drawString("Chapter "+progress.getCurrentStage()+"  •  Affection "+progress.getAffectionScore(),28,55);
        if(!talking) panel(g, 14, h-66, w-28, 54, cream, 12);
        if(talking){drawDialogue(g,w,h);return;}
        String[] labels={"Save","Load","Words","Journal","Options","Talk"};
        int x=24; int[] widths={92,92,106,112,112,100};
        for(int i=0;i<labels.length;i++){ button(g,x,h-57,widths[i],38,labels[i],labels[i].equals("Talk")?pink:cream); x+=widths[i]+8; }
        g.setColor(ink);g.setFont(new Font("SansSerif",Font.PLAIN,13));g.drawString("WASD move  •  E talk  •  Esc back",w-258,h-34);
    }

    private void drawDialogue(Graphics2D g,int w,int h) {
        double sx=w/960.0, sy=h/576.0;
        Graphics2D ui=(Graphics2D)g.create();ui.scale(sx,sy);
        if(hideOverworld){
            if(dialogueBackground!=null)ui.drawImage(dialogueBackground,0,0,960,576,null);
            else{ui.setColor(cream);ui.fillRect(0,0,960,576);}
        }

        panel(ui,15,13,264,67,cream,18);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,20));ui.drawString("Street by School",23,43);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,16));ui.drawString("4:00 PM  |  Chapter "+progress.getCurrentStage(),23,66);

        button(ui,287,20,102,40,hideOverworld?"Show map":"Hide map",cream);
        button(ui,397,20,112,40,"Import BG",ink);
        panel(ui,720,8,220,67,cream,18);
        int affection=(int)Math.round(displayedAffection);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,15));ui.drawString("Affection  "+(affection>0?"+":"")+affection,728,29);
        panel(ui,736,45,188,16,cream,9);
        int center=830, amount=Math.min(88,Math.abs(affection)*16);
        ui.setColor(affection<0?babyBlueIce:pink);
        ui.fillRoundRect(affection>=0?center:center-amount,49,amount,8,8,8);
        ui.setColor(ink);ui.setStroke(new java.awt.BasicStroke(2));ui.drawLine(center,45,center,61);

        dashedPanel(ui,40,91,340,337,cream,16);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,18));
        drawCentered(ui,"[CHARACTER SPRITE]",210,251);
        panel(ui,24,435,225,48,pink,16);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.BOLD,26));drawCentered(ui,"[NAME]",136,468);

        String[] actions={"Save","Load","Words","Journal","Options","Quit"};
        int[] ax={24,92,160,238,330,424}, aw={62,62,72,86,86,64};
        for(int i=0;i<actions.length;i++)button(ui,ax[i],490,aw[i],42,actions[i],cream);

        panel(ui,469,87,470,105,cream,20);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,22));
        ui.drawString("[Love interest speaks a line in their dialect,",483,128);
        ui.drawString("with [new word]]",483,154);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,15));
        ui.drawString("Tap a highlighted word to add it to your Dictionary",483,176);
        ui.setColor(cream);
        int[] speechPointerX = {505, 524, 515};
        int[] speechPointerY = {191, 191, 210};
        ui.fillPolygon(speechPointerX, speechPointerY, 3);
        ui.setColor(ink);
        ui.setStroke(new java.awt.BasicStroke(3));
        ui.drawLine(505, 191, 515, 210);
        ui.drawLine(515, 210, 524, 191);

        if(progress.getCurrentStage()<=2){
            String[] choices={"[Choice 1]","[Choice 2]","[Choice 3]"};Color[] badges={softYellow,raspberryPlum,pink};
            for (int i = 0; i < choices.length; i++) {
                int y = 229 + i * 68;
                boolean hovered = pointerX >= 518 && pointerX < 938
                        && pointerY >= y && pointerY < y + 54;
                int lift = hovered ? 2 : 0;
                panel(ui, 518, y - lift, 420, 54, hovered ? pink : cream, 22);
                ui.setColor(ink);
                ui.setFont(new Font("SansSerif", Font.PLAIN, 22));
                ui.drawString(choices[i], 530, y + 35 - lift);
                ui.setColor(ink);
                ui.fillOval(504, y - 5 - lift, 38, 38);
                ui.setColor(badges[i]);
                ui.fillOval(507, y - 2 - lift, 32, 32);
                ui.setColor(ink);
                ui.setFont(new Font("SansSerif", Font.BOLD, 17));
                drawCentered(ui, Integer.toString(i + 1), 523, y + 22 - lift);
            }
        } else {
            dashedPanel(ui,518,444,420,82,cream,16);
            ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,15));ui.drawString("Stages 3-4: typed reply replaces the choices",531,462);
            panel(ui, 531, 472, 294, 42, cream, 10);
            ui.setColor(ink);
            ui.setFont(new Font("SansSerif", Font.PLAIN, 15));
            ui.drawString(typedReply.isEmpty() ? "type your reply here..." : typedReply, 537, 499);
            button(ui,836,468,91,46,"Send",pink);
        }
        ui.dispose();
    }

    private void dashedPanel(Graphics2D g, int x, int y, int width, int height, Color fill, int arc) {
        g.setColor(fill);
        g.fillRoundRect(x, y, width, height, arc, arc);
        g.setColor(ink);
        g.setStroke(new java.awt.BasicStroke(
                2.5f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_ROUND,
                10, new float[] {7, 7}, 0));
        g.drawRoundRect(x, y, width, height, arc, arc);
        g.setStroke(new java.awt.BasicStroke(3));
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baseline) {
        java.awt.FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baseline);
    }

    private void drawDictionary(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();
        pageFrame(g, "Dictionary", width, height);

        int left = 198;
        int contentWidth = width - left - 30;
        int listWidth = Math.max(250, (int) (contentWidth * 0.57));
        drawDictionaryFilters(g, left);
        drawDictionarySearch(g, left, listWidth);
        int shown = drawVocabularyRows(g, left, listWidth);
        if (shown == 0) {
            g.setColor(ink);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.drawString("Words you learn during conversations will appear here.", left + 8, 220);
        }

        int detailX = left + listWidth + 18;
        int detailWidth = width - detailX - 30;
        int detailHeight = Math.min(250, height - 270);
        panel(g, detailX, 180, detailWidth, detailHeight, cream, 14);
        g.setColor(ink);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("Your progress", detailX + 16, 210);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("Learned words: " + progress.getLearnedWords().size(), detailX + 16, 244);
    }

    private void drawDictionaryFilters(Graphics2D g, int left) {
        String[] filters = {"All", "Bisaya", "Ilokano", "Bicolano"};
        int x = left;
        for (String filter : filters) {
            int width = filter.equals("All") ? 74 : 112;
            Color fill = dictionaryFilter.equals(filter) ? softYellow : cream;
            button(g, x, 76, width, 38, filter, fill);
            x += width + 8;
        }
    }

    private void drawDictionarySearch(Graphics2D g, int left, int listWidth) {
        panel(g, left, 125, listWidth, 40, cream, 12);
        g.setColor(ink);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        String value = searchText.isEmpty() ? "type a word (keyboard)" : searchText;
        g.drawString("Search: " + value, left + 14, 151);
    }

    private int drawVocabularyRows(Graphics2D g, int left, int listWidth) {
        int y = 180;
        int shown = 0;
        String query = searchText.toLowerCase();
        for (VocabularyEntry entry : vocabulary) {
            if (!dictionaryFilter.equals("All")
                    && !entry.getDialect().equalsIgnoreCase(dictionaryFilter)) {
                continue;
            }
            if (!query.isBlank() && !entry.getWord().toLowerCase().contains(query)
                    && !entry.getMeaning().toLowerCase().contains(query)) {
                continue;
            }

            panel(g, left, y, listWidth, 42, cream, 10);
            g.setColor(ink);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.drawString(entry.getWord(), left + 12, y + 27);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            String details = entry.getMeaning() + "  •  " + entry.getDialect();
            g.drawString(details, left + Math.min(150, listWidth / 2), y + 27);
            y += 49;
            shown++;
            if (shown == 6) {
                break;
            }
        }
        return shown;
    }

    private void drawSettings(Graphics2D g) {
        int w=getWidth(),h=getHeight();pageFrame(g,"Settings",w,h);
        int left=198, right=w-24, gap=34, colW=(right-left-gap)/2, rightX=left+colW+gap;
        g.setColor(ink);g.setFont(new Font("SansSerif",Font.PLAIN,20));g.drawString("Display",left,112);g.drawString("Skip",rightX,112);
        button(g,left,124,Math.min(180,colW),38,(fullscreen?"✓":"□")+" Window",fullscreen?cream:softYellow);
        button(g,left,168,Math.min(180,colW),38,(fullscreen?"□":"✓")+" Fullscreen",fullscreen?softYellow:cream);
        button(g,rightX,124,Math.min(205,colW),38,(skipUnseen?"✓":"□")+" Unseen text",skipUnseen?softYellow:cream);
        button(g,rightX,168,Math.min(205,colW),38,(skipAfterChoices?"✓":"□")+" After choices",skipAfterChoices?softYellow:cream);
        drawSlider(g,left,230,colW,"Text speed",textSpeed);drawSlider(g,rightX,230,colW,"Auto-forward time",autoForward);
        drawSlider(g,left,340,colW,"Music volume",musicVolume);drawSlider(g,rightX,340,colW,"Sound volume",soundVolume);
        g.setColor(ink);g.setFont(new Font("SansSerif",Font.PLAIN,20));g.drawString("Learning",left,445);
        button(g,left,458,Math.min(220,colW),38,(showHints?"✓":"□")+" Show English hints",showHints?softYellow:cream);
        button(g,rightX,458,Math.min(180,colW),38,(muteAll?"✓":"□")+" Mute all",muteAll?softYellow:cream);
        g.setColor(ink);g.setFont(new Font("SansSerif",Font.PLAIN,12));g.drawString("Click sliders to adjust. Preferences apply to the dialogue UI.",left,h-22);
    }

    private void drawSlider(Graphics2D g, int x, int y, int width, String label, int value) {
        g.setColor(ink);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        g.drawString(label, x, y);
        panel(g, x, y + 12, width, 18, softYellow, 9);

        int knobX = x + 8 + (width - 24) * value / 100;
        g.setColor(pink);
        g.fillOval(knobX - 10, y + 7, 28, 28);
        g.setColor(ink);
        g.drawOval(knobX - 10, y + 7, 28, 28);
    }

    private void drawSaves(Graphics2D g) {
        int w=getWidth(),h=getHeight();pageFrame(g,"Save / Load",w,h);
        int contentLeft=198, contentRight=w-30, contentW=contentRight-contentLeft;
        int controlX=(176+w)/2-105;
        button(g,controlX,82,98,40,"Save",softYellow);button(g,controlX+113,82,98,40,"Load",cream);
        int gap=12, cardW=(contentW-gap*2)/3;
        for (int i = 0; i < 3; i++) {
            int x = contentLeft + i * (cardW + gap);
            panel(g, x, 150, cardW, h - 220, cream, 14);
            g.setColor(ink);
            g.setFont(new Font("SansSerif", Font.BOLD, 19));
            g.drawString(i == 0 ? "Auto-save" : "Slot " + (i + 1), x + 14, 184);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.drawString("Chapter " + progress.getCurrentStage(), x + 14, 214);
            g.drawString("Affection " + progress.getAffectionScore(), x + 14, 238);
            button(g, x + 12, h - 112, cardW - 24, 40,
                    i == 0 ? "Save here" : "Empty slot", i == 0 ? softYellow : cream);
        }
    }

    private void pageFrame(Graphics2D g, String title, int width, int height) {
        g.setColor(cream);
        g.fillRoundRect(176, 16, width - 192, height - 32, 24, 24);
        g.setColor(ink);
        g.setStroke(new java.awt.BasicStroke(4));
        g.drawRoundRect(176, 16, width - 192, height - 32, 24, 24);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        g.drawString(title, 198, 57);
        button(g, width - 142, 25, 112, 42, "Back", pink);

        String[] navigation = {"Save/Load", "Words", "Settings", "Help"};
        for (int i = 0; i < navigation.length; i++) {
            int x = 32;
            int y = 40 + i * 68;
            boolean active = (i == 0 && screen == Screen.SAVES)
                    || (i == 1 && screen == Screen.DICTIONARY)
                    || (i == 2 && screen == Screen.SETTINGS);
            panel(g, x, y, 140, 48, active ? pink : ink, 14);
            g.setColor(active ? ink : cream);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            java.awt.FontMetrics metrics = g.getFontMetrics();
            int textX = x + (140 - metrics.stringWidth(navigation[i])) / 2;
            int textY = y + (48 - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(navigation[i], textX, textY);
        }
    }

    private void panel(Graphics2D g, int x, int y, int width, int height, Color fill, int arc) {
        g.setColor(ink);
        g.fillRoundRect(x + 4, y + 5, width, height, arc, arc);
        g.setColor(fill);
        g.fillRoundRect(x, y, width, height, arc, arc);
        g.setColor(ink);
        g.setStroke(new java.awt.BasicStroke(3));
        g.drawRoundRect(x, y, width, height, arc, arc);
    }

    private void button(Graphics2D g, int x, int y, int width, int height, String text, Color fill) {
        boolean hovered = pointerX >= x && pointerX < x + width
                && pointerY >= y && pointerY < y + height;
        int lift = hovered ? 2 : 0;
        Color buttonColor = hovered
                ? new Color(Math.min(255, fill.getRed() + 10),
                        Math.min(255, fill.getGreen() + 10), Math.min(255, fill.getBlue() + 10))
                : fill;
        panel(g, x, y - lift, width, height, buttonColor, height);

        g.setColor(fill.equals(ink) ? cream : ink);
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.min(18, height / 2)));
        java.awt.FontMetrics metrics = g.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y - lift + (height - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(text, textX, textY);
    }

    private void showNotice(String message) {
        notice = message;
        noticeExpiresAt = System.currentTimeMillis() + 2600;
    }

    private void drawNotice(Graphics2D g) {
        if (notice.isEmpty()) {
            return;
        }
        int width = Math.min(500, Math.max(230, g.getFontMetrics().stringWidth(notice) + 42));
        int x = (getWidth() - width) / 2;
        int y = getHeight() - 38;
        Composite previousComposite = g.getComposite();
        long remaining = Math.max(0, noticeExpiresAt - System.currentTimeMillis());
        float alpha = remaining < 350 ? remaining / 350f : 1f;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        panel(g, x, y, width, 29, ink, 12);
        g.setColor(cream);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        drawCentered(g, notice, getWidth() / 2, y + 20);
        g.setComposite(previousComposite);
    }

    private void submitDialogueChoice(int choice) {
        if (choice < 0 || choice > 2) {
            return;
        }
        progress.setAffectionScore(progress.getAffectionScore() + (choice == 0 ? 1 : 0));
        talking = false;
        showNotice("Conversation choice recorded.");
    }

    private void submitTypedReply() {
        if (typedReply.isBlank()) {
            showNotice("Type a reply before sending.");
            return;
        }
        progress.setAffectionScore(progress.getAffectionScore() + 1);
        typedReply = "";
        talking = false;
        showNotice("Reply sent.");
    }

    private void handleClick(int x, int y) {
        int width = getWidth();
        int height = getHeight();

        if (screen != Screen.TITLE && isBackButton(x, y, width)) {
            goBack();
        } else if (screen == Screen.TITLE) {
            handleTitleClick(x, y, width, height);
        } else if (screen == Screen.GAME) {
            handleGameClick(x, y, width, height);
        } else if (x < 176 && y >= 32 && y < 310) {
            handleSidebarClick(y);
        } else if (screen == Screen.DICTIONARY && y >= 76 && y < 116) {
            selectDictionaryFilter(x);
        } else if (screen == Screen.SETTINGS) {
            handleSettingsClick(x, y, width);
        } else if (screen == Screen.SAVES) {
            handleSaveClick(x, y, width, height);
        }

        requestFocusInWindow();
        repaint();
    }

    private boolean isBackButton(int x, int y, int width) {
        return x >= width - 150 && x <= width - 20 && y >= 20 && y <= 75;
    }

    private void handleTitleClick(int x, int y, int width, int height) {
        int uiX = x * 960 / Math.max(1, width);
        int uiY = y * 576 / Math.max(1, height);
        if (uiX < 78 || uiX > 339) {
            return;
        }
        if (uiY >= 214 && uiY < 256) {
            showAbout = false;
            screen = Screen.GAME;
        } else if (uiY >= 264 && uiY < 306) {
            loadGame();
            showAbout = false;
            screen = Screen.GAME;
        } else if (uiY >= 314 && uiY < 356) {
            openScreen(Screen.DICTIONARY);
        } else if (uiY >= 364 && uiY < 406) {
            openScreen(Screen.SETTINGS);
        } else if (uiY >= 414 && uiY < 456) {
            showAbout = true;
        } else if (uiY >= 464 && uiY < 506) {
            gameThread = null;
            System.exit(0);
        }
    }

    private void handleGameClick(int x, int y, int width, int height) {
        if (talking) {
            handleDialogueClick(x * 960 / Math.max(1, width), y * 576 / Math.max(1, height));
            return;
        }
        if (y < height - 66) {
            return;
        }
        if (x < 120) {
            saveGame();
        } else if (x < 220) {
            loadGame();
        } else if (x < 335) {
            openScreen(Screen.DICTIONARY);
        } else if (x < 455) {
            showNotice("Your story journal is ready.");
        } else if (x < 575) {
            openScreen(Screen.SETTINGS);
        } else {
            talking = true;
        }
    }

    private void handleDialogueClick(int x, int y) {
        if (y >= 20 && y <= 64 && x >= 287 && x < 389) {
            hideOverworld = !hideOverworld;
        } else if (y >= 20 && y <= 64 && x >= 397 && x < 509) {
            importDialogueBackground();
        } else if (y >= 490 && y <= 536) {
            handleDialogueAction(x);
        } else if (progress.getCurrentStage() <= 2 && x >= 500 && y >= 224 && y < 418) {
            submitDialogueChoice((y - 224) / 68);
        } else if (progress.getCurrentStage() >= 3 && x >= 830 && y >= 462 && y < 520) {
            submitTypedReply();
        }
    }

    private void handleDialogueAction(int x) {
        if (x >= 24 && x < 88) {
            saveGame();
        } else if (x >= 92 && x < 156) {
            loadGame();
        } else if (x >= 160 && x < 234) {
            talking = false;
            openScreen(Screen.DICTIONARY);
        } else if (x >= 238 && x < 326) {
            showNotice("Your story journal is ready.");
        } else if (x >= 330 && x < 420) {
            talking = false;
            openScreen(Screen.SETTINGS);
        } else if (x >= 424 && x < 490) {
            talking = false;
            screen = Screen.TITLE;
        }
    }

    private void handleSidebarClick(int y) {
        int item = (y - 40) / 68;
        Screen destination = switch (item) {
            case 0 -> Screen.SAVES;
            case 1 -> Screen.DICTIONARY;
            case 2 -> Screen.SETTINGS;
            default -> screen;
        };
        if (item == 3) {
            showNotice("Help: move with WASD and choose Talk to start a conversation.");
        } else {
            openScreen(destination);
        }
    }

    private void selectDictionaryFilter(int x) {
        if (x < 110) {
            dictionaryFilter = "All";
        } else if (x < 235) {
            dictionaryFilter = "Bisaya";
        } else if (x < 360) {
            dictionaryFilter = "Ilokano";
        } else {
            dictionaryFilter = "Bicolano";
        }
    }

    private void handleSettingsClick(int x, int y, int width) {
        int left = 198;
        int rightX = left + (width - 24 - left - 34) / 2 + 34;
        if (y >= 124 && y < 162) {
            if (x < rightX) fullscreen = false;
            else skipUnseen = !skipUnseen;
        } else if (y >= 168 && y < 206) {
            if (x < rightX) fullscreen = true;
            else skipAfterChoices = !skipAfterChoices;
        } else if (y >= 450 && y < 505) {
            if (x < rightX) showHints = !showHints;
            else muteAll = !muteAll;
        } else if ((y >= 235 && y < 280) || (y >= 345 && y < 390)) {
            updateSlider(x, y, left, rightX, width);
        }
    }

    private void updateSlider(int x, int y, int left, int rightX, int width) {
        int columnWidth = (width - 24 - left - 34) / 2;
        boolean rightColumn = x >= rightX;
        int columnX = rightColumn ? rightX : left;
        int value = Math.max(0, Math.min(100, (x - columnX) * 100 / Math.max(1, columnWidth)));
        boolean lowerRow = y >= 345;
        if (lowerRow && rightColumn) soundVolume = value;
        else if (lowerRow) musicVolume = value;
        else if (rightColumn) autoForward = value;
        else textSpeed = value;
    }

    private void handleSaveClick(int x, int y, int width, int height) {
        if (y >= 80 && y < 130) {
            if (x < width / 2) saveGame();
            else loadGame();
        } else if (y >= height - 125) {
            if (x < width / 3) saveGame();
            else loadGame();
        }
    }

    private void openScreen(Screen destination) {
        if (destination != screen) {
            returnScreen = screen;
            screen = destination;
        }
    }

    private void goBack() {
        Screen destination = returnScreen;
        if (destination == screen) {
            destination = Screen.GAME;
        }
        screen = destination;
        talking = false;
    }

    private void saveGame() {
        try {
            files.saveProgress(progress);
            showNotice("Game saved.");
        } catch (IOException e) {
            showNotice("Could not save game.");
        }
    }

    private void importDialogueBackground() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a dialogue background image");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            BufferedImage selected = ImageIO.read(chooser.getSelectedFile());
            if (selected == null) {
                showNotice("That file is not a supported image.");
                return;
            }
            dialogueBackground = selected;
            hideOverworld = true;
            showNotice("Dialogue background loaded.");
        } catch (IOException e) {
            showNotice("Could not load that image.");
        }
    }

    private void loadGame() {
        try {
            GameProgress loaded = files.loadProgress();
            progress.resetProgress();
            progress.setCurrentStage(loaded.getCurrentStage());
            progress.setSelectedPartner(loaded.getSelectedPartner());
            progress.setAffectionScore(loaded.getAffectionScore());
            for (String word : loaded.getLearnedWords()) {
                progress.addLearnedWord(word);
            }
            showNotice("Save loaded.");
        } catch (IOException e) {
            showNotice("No save found yet.");
        }
    }
}
