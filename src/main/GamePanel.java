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
import java.io.IOException;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JOptionPane;

import data.FileManager;
import data.GameProgress;
import data.VocabularyEntry;
import entity.Player;
import tile.TileManager;
import story.StoryManager;
import story.StoryScript;
import story.StoryView;

public class GamePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private enum Screen { TITLE, GAME, DICTIONARY, SETTINGS, SAVES }
    private Screen screen = Screen.TITLE;
    private Screen returnScreen = Screen.GAME;
    private final Color ink = new Color(0x2B, 0x19, 0x3D);
    private final Color cream = new Color(0xF6, 0xE9, 0xC7);
    private final Color pink = new Color(0xFF, 0xB2, 0xC2);
    private final Color raspberryPlum = new Color(0xBD, 0x2D, 0x87);
    private final Color softYellow = new Color(0xF2, 0xDE, 0xA5);
    private final GameProgress progress = new GameProgress();
    private final FileManager files;
    private StoryManager story;
    private StoryView storyView;
    private int dictionaryPage;
    private String selectedWord = "";
    private static final int WORDS_PER_PAGE = 5;
    private static final String[] LANGUAGE_FILTERS = {"All", "Cebuano", "Ilokano", "Central Bikol"};
    private static final int[] FILTER_WIDTHS = {74, 112, 112, 138};
    private boolean talking;
    private boolean returnTalking;
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
    private int pointerX = -1;
    private int pointerY = -1;
    private long animationFrame;
    
    private static final int ORIGINAL_TILE_SIZE = 32;
    private static final int TILE_SCALE = 4;

    public final int tileSize = ORIGINAL_TILE_SIZE * TILE_SCALE;
    public final int maxScreenCol = 9;
    public final int maxScreenRow = 6;
    public final int screenWidth = tileSize * maxScreenCol;
    public final int screenHeight = tileSize * maxScreenRow;

    public final int maxWorldCol = 20;
    public final int maxWorldRow = 20;
    public final int maxMap = 10;
    public int currentMap = 0;
    final TileManager tileM = new TileManager(this);
    private final KeyHandler keyH = new KeyHandler(this);
    private javax.swing.Timer gameTimer;
    public CollisionChecker cChecker = new CollisionChecker(this);
    public EventHandler eHandler = new EventHandler(this);
    public Player player = new Player(this, keyH);

    private static final int FPS = 60;

    public GamePanel() { this(java.nio.file.Path.of("saves")); }

    public GamePanel(java.nio.file.Path saveDirectory) {
        files = new FileManager(saveDirectory, progress);
        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(cream);
        this.setDoubleBuffered(true);
        this.addKeyListener(keyH);
        this.setFocusable(true);
        this.setLayout(null);
        try {
            story = new StoryManager(StoryScript.load(), progress);
            storyView = new StoryView(story, progress, this::handleStoryAction);
            storyView.setVisible(false);
            add(storyView);
            java.util.LinkedHashMap<String, VocabularyEntry> entries = new java.util.LinkedHashMap<>();
            for (StoryScript.Word word : story.script().words()) {
                entries.putIfAbsent(word.text(), new VocabularyEntry(word.text(), word.meaning(), word.dialect(), word.review()));
            }
            vocabulary = List.copyOf(entries.values());
        } catch (IOException error) {
            notice = "The storyline could not be loaded. Please check the story resource files.";
            noticeExpiresAt = Long.MAX_VALUE;
            error.printStackTrace();
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
                if(code==KeyEvent.VK_ESCAPE&&screen!=Screen.TITLE){if(screen==Screen.GAME&&talking)talking=false;else goBack();}
                else if(code==KeyEvent.VK_ENTER&&screen==Screen.TITLE)startNewStory();
                else if(code==KeyEvent.VK_E&&screen==Screen.GAME&&!talking)resumeStory();
                else if(e.isControlDown()&&code==KeyEvent.VK_S)saveGame();
                else if(e.isControlDown()&&code==KeyEvent.VK_L)loadGame();
                refreshStoryVisibility();
                repaint();
            }
            @Override public void keyTyped(KeyEvent e) {
                if (screen == Screen.DICTIONARY) {
                    char c=e.getKeyChar();
                    if (c=='\b' && !searchText.isEmpty()) searchText=searchText.substring(0,searchText.length()-1);
                    else if (!Character.isISOControl(c) && searchText.length()<40) searchText+=c;
                    dictionaryPage = 0;
                    repaint();
                }
            }
        });
    }

    /** Keep simulation and Swing input on the same event thread. */
    public void startGameThread() {
        if (gameTimer == null) gameTimer = new javax.swing.Timer(1000 / FPS, e -> { update(); repaint(); });
        gameTimer.start();
    }

    @Override public void removeNotify() {
        if (gameTimer != null) gameTimer.stop();
        super.removeNotify();
    }

    private void clearMovement() {
        keyH.upPressed = keyH.downPressed = keyH.leftPressed = keyH.rightPressed = false;
    }

    @Override public void doLayout() {
        if (storyView != null) storyView.setBounds(0, 0, getWidth(), getHeight());
    }

    private void refreshStoryVisibility() {
        if (storyView == null) return;
        storyView.setVisible(screen == Screen.GAME && talking && story.started());
        storyView.setHintsEnabled(showHints);
        storyView.refresh();
        if (!storyView.isVisible()) requestFocusInWindow();
    }

    private void startNewStory() {
        if (story == null) { showNotice("The storyline is unavailable."); return; }
        if (story.started() && !story.completed() && JOptionPane.showConfirmDialog(this,
                "Start a new story? Unsaved progress will be replaced.", "New Game",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        story.start(); storyView.resetPresentation(); player.setDefaultValues(); currentMap = 0; clearMovement();
        showAbout = false; screen = Screen.GAME; returnScreen = Screen.GAME; talking = true;
        refreshStoryVisibility();
    }

    private void resumeStory() {
        if (story == null || !story.started()) { startNewStory(); return; }
        screen = Screen.GAME; talking = true;
        keyH.upPressed = keyH.downPressed = keyH.leftPressed = keyH.rightPressed = false;
        refreshStoryVisibility();
    }

    private void handleStoryAction(String action) {
        switch (action) {
            case "Save" -> saveGame();
            case "Load" -> loadGame();
            case "Words" -> { dictionaryPage = 0; openScreen(Screen.DICTIONARY); }
            case "Options" -> openScreen(Screen.SETTINGS);
            case "Map" -> { talking = false; screen = Screen.GAME; clearMovement(); }
            case "Menu" -> { talking = false; screen = Screen.TITLE; clearMovement(); }
            case "Journal" -> openStoryJournal();
            case "Hint" -> JOptionPane.showMessageDialog(this, story.hint(), "Language hint", JOptionPane.INFORMATION_MESSAGE);
            default -> { }
        }
        refreshStoryVisibility(); repaint();
    }

    private void openStoryJournal() {
        if (story == null || !story.started()) { showNotice("Start the story to open your journal."); return; }
        String partner = progress.getSelectedPartner();
        String message = "Current scene: " + story.scene().title() + "\nChapter " + progress.getCurrentStage()
                + "\n" + (partner.isEmpty() ? "Meet your clubmates and choose a partner in Chapter 2."
                : "Project partner: " + partner + "\nOptional: Learn the language with " + partner)
                + "\nLearned vocabulary: " + progress.getLearnedWords().size()
                + "\n\nStudy Breaks use the questions written in your group's story.";
        int selected = JOptionPane.showOptionDialog(this, message, "Story journal", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new String[]{"Study Break", "Learn with partner", "Close"}, "Close");
        if (selected == 0 || selected == 1) {
            try { story.startPractice(selected == 1); screen = Screen.GAME; talking = true; }
            catch (IllegalStateException error) { showNotice(error.getMessage()); }
        }
    }

    public void update() {
        animationFrame++;
        if(!notice.isEmpty()&&System.currentTimeMillis()>=noticeExpiresAt)notice="";
        if (screen == Screen.GAME && !talking) {
            player.update();
            eHandler.checkEvent();
        }
    }
    public void paintComponent(Graphics g) {
        
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D)g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (screen == Screen.GAME) {
            long drawStart = keyH.checkDrawTime ? System.nanoTime() : 0;
            tileM.draw(g2);
            player.draw(g2);
            if (keyH.checkDrawTime) {
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.drawString("Draw time: " + (System.nanoTime() - drawStart) / 1_000_000.0 + " ms", 14, 86);
            }
            g2.scale(getWidth() / 960.0, getHeight() / 576.0);
            drawGameUi(g2);
        } else {
            g2.scale(getWidth() / 960.0, getHeight() / 576.0);
            g2.setColor(cream); g2.fillRect(0, 0, getWidth(), getHeight());
            switch (screen) {
                case TITLE -> drawTitle(g2);
                case DICTIONARY -> drawDictionary(g2);
                case SETTINGS -> drawSettings(g2);
                case SAVES -> drawSaves(g2);
                default -> { }
            }
        }

        g2.dispose();
    }

    @Override protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Graphics2D overlay = (Graphics2D) g.create();
        drawNotice(overlay);
        overlay.dispose();
    }

    private void drawTitle(Graphics2D g) {
        Graphics2D ui=(Graphics2D)g.create();ui.setColor(cream);ui.fillRect(0,0,960,576);
        panel(ui,58,38,340,162,pink,20);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.BOLD,38));ui.drawString("In Other Words",73,108);
        ui.setColor(raspberryPlum);ui.setFont(new Font("SansSerif",Font.BOLD,31));ui.drawString("Mahal Kita",74,151);
        ui.setColor(ink);ui.setFont(new Font("SansSerif",Font.PLAIN,17));ui.drawString("A language-learning story",74,181);
        int heartSize=20+(int)(2*Math.sin(animationFrame/8.0));ui.setColor(raspberryPlum);ui.setFont(new Font("SansSerif",Font.PLAIN,heartSize));ui.drawString("♥",365,75);
        String[] labels={"New Game","Load","Dictionary","Settings","About","Quit"};
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
        if (story != null && story.started() && !story.completed())
            button(ui,560,430,244,42,"Continue story",pink);
        ui.setFont(new Font("SansSerif",Font.PLAIN,14));
        drawCentered(ui,"Every language has a story. Sometimes, finding the right words is where yours begins.",480,551);
        ui.dispose();
    }

    private void drawGameUi(Graphics2D g) {
        int w=960, h=576;
        panel(g, 14, 12, 236, 54, cream, 12);
        g.setColor(ink); g.setFont(new Font("SansSerif",Font.BOLD,17)); g.drawString("Campus",28,35);
        g.setFont(new Font("SansSerif",Font.PLAIN,14)); g.drawString("Chapter "+progress.getCurrentStage()+"  •  Affection "+progress.getAffectionScore(),28,55);
        if(!talking) panel(g, 14, h-66, w-28, 54, cream, 12);
        if(talking) return;
        String[] labels={"Save","Load","Words","Journal","Options","Talk"};
        int x=24; int[] widths={92,92,106,112,112,100};
        for(int i=0;i<labels.length;i++){ button(g,x,h-57,widths[i],38,labels[i],labels[i].equals("Talk")?pink:cream); x+=widths[i]+8; }
        g.setColor(ink);g.setFont(new Font("SansSerif",Font.PLAIN,13));g.drawString("WASD move  •  E talk  •  Esc back",w-258,h-34);
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

    private List<VocabularyEntry> filteredVocabulary() {
        String query = searchText.toLowerCase(java.util.Locale.ROOT);
        return vocabulary.stream()
                .filter(entry -> story != null && story.knowsWord(entry.getWord()))
                .filter(entry -> dictionaryFilter.equals("All") || entry.getDialect().equals(dictionaryFilter))
                .filter(entry -> query.isBlank() || entry.getWord().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || entry.getMeaning().toLowerCase(java.util.Locale.ROOT).contains(query))
                .toList();
    }

    private int dictionaryPages() { return Math.max(1, (filteredVocabulary().size() + WORDS_PER_PAGE - 1) / WORDS_PER_PAGE); }

    private void drawDictionary(Graphics2D g) {
        pageFrame(g, "Vocabulary Log", 960, 576);
        int left = 198, listWidth = 420;
        drawDictionaryFilters(g, left);
        panel(g, left, 125, listWidth, 40, cream, 12);
        g.setColor(ink); g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        String search = "Search: " + (searchText.isEmpty() ? "type a word or meaning" : searchText);
        drawWrapped(g, search, left + 14, 151, listWidth - 28, 18, 1);
        List<VocabularyEntry> entries = filteredVocabulary();
        dictionaryPage = Math.min(dictionaryPage, dictionaryPages() - 1);
        int start = dictionaryPage * WORDS_PER_PAGE;
        if (entries.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            drawWrapped(g, story == null || story.knownWordCount() == 0
                    ? "Your notebook is waiting for its first word."
                    : "No words match this filter or search.", left + 10, 213, listWidth - 20, 24, 3);
        }
        for (int i = start; i < Math.min(entries.size(), start + WORDS_PER_PAGE); i++) {
            VocabularyEntry entry = entries.get(i);
            int y = 180 + (i - start) * 60;
            panel(g, left, y, listWidth, 53, selectedWord.equals(entry.getWord()) ? pink : cream, 10);
            g.setColor(ink); g.setFont(new Font("SansSerif", Font.BOLD, 16));
            while (g.getFontMetrics().stringWidth(entry.getWord()) > listWidth - 24 && g.getFont().getSize() > 11)
                g.setFont(g.getFont().deriveFont((float)g.getFont().getSize() - 1));
            g.drawString(entry.getWord(), left + 12, y + 22);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString(entry.getDialect() + "  /  " + (story.isLearnedWord(entry.getWord()) ? "Learned" : "Heard")
                    + "  /  " + entry.getDifficulty(), left + 12, y + 43);
        }
        button(g, left, 503, 112, 36, "Previous", cream);
        button(g, left + 124, 503, 112, 36, "Next", cream);
        g.setColor(ink); g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("Page " + (dictionaryPage + 1) + " / " + dictionaryPages(), left + 254, 528);
        panel(g, 638, 180, 292, 298, cream, 14);
        VocabularyEntry selected = entries.stream().filter(e -> e.getWord().equals(selectedWord)).findFirst()
                .orElse(entries.isEmpty() ? null : entries.get(start));
        g.setColor(ink); g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString(selected == null ? "Your progress" : "Word details", 652, 210);
        if (selected != null) {
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            drawWrapped(g, selected.getWord(), 652, 242, 260, 22, 3);
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            drawWrapped(g, selected.getMeaning(), 652, 316, 260, 22, 4);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString(selected.getDialect() + " / " + selected.getDifficulty(), 652, 423);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("Learned words: " + progress.getLearnedWords().size(), 652, 455);
    }

    private void drawDictionaryFilters(Graphics2D g, int left) {
        int x = left;
        for (int i = 0; i < LANGUAGE_FILTERS.length; i++) {
            String filter = LANGUAGE_FILTERS[i];
            button(g, x, 76, FILTER_WIDTHS[i], 38, filter, dictionaryFilter.equals(filter) ? softYellow : cream);
            x += FILTER_WIDTHS[i] + 8;
        }
    }

    private void drawWrapped(Graphics2D g, String text, int x, int y, int width, int spacing, int limit) {
        List<String> lines = StoryView.wrap(text, g.getFontMetrics(), width);
        for (int i = 0; i < Math.min(limit, lines.size()); i++) g.drawString(lines.get(i), x, y + i * spacing);
    }

    private void drawSettings(Graphics2D g) {
        int w=960,h=576;pageFrame(g,"Settings",w,h);
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
        pageFrame(g, "Save / Load", 960, 576);
        panel(g, 216, 130, 690, 290, cream, 16);
        g.setColor(ink); g.setFont(new Font("SansSerif", Font.BOLD, 24));
        g.drawString("Your story", 240, 175);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        if (story != null && story.started()) {
            drawWrapped(g, "Current scene: " + story.scene().title(), 240, 218, 635, 25, 3);
            g.drawString("Chapter " + progress.getCurrentStage() + " / Affection " + progress.getAffectionScore(), 240, 300);
            g.drawString("Partner: " + (progress.getSelectedPartner().isEmpty() ? "Not chosen yet" : progress.getSelectedPartner()), 240, 332);
        } else g.drawString("Start a new story or load your saved progress.", 240, 218);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("One save slot. Saving replaces your previous saved progress.", 240, 389);
        button(g, 290, 450, 240, 44, "Save progress", softYellow);
        button(g, 560, 450, 280, 44, "Load saved story", cream);
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

    private void handleClick(int x, int y) {
        x = x * 960 / Math.max(1, getWidth());
        y = y * 576 / Math.max(1, getHeight());
        int width = 960;
        int height = 576;

        if (screen != Screen.TITLE && screen != Screen.GAME && isBackButton(x, y, width)) {
            goBack();
        } else if (screen == Screen.TITLE) {
            handleTitleClick(x, y, width, height);
        } else if (screen == Screen.GAME) {
            handleGameClick(x, y, width, height);
        } else if (x < 176 && y >= 32 && y < 310) {
            handleSidebarClick(y);
        } else if (screen == Screen.DICTIONARY && y >= 76 && y < 116) {
            selectDictionaryFilter(x);
            dictionaryPage = 0;
        } else if (screen == Screen.DICTIONARY && y >= 503 && y <= 539) {
            if (x >= 198 && x <= 310) dictionaryPage = Math.max(0, dictionaryPage - 1);
            else if (x >= 322 && x <= 434) dictionaryPage = Math.min(dictionaryPages() - 1, dictionaryPage + 1);
        } else if (screen == Screen.DICTIONARY && x >= 198 && x <= 618 && y >= 180 && y < 480) {
            int index = dictionaryPage * WORDS_PER_PAGE + (y - 180) / 60;
            List<VocabularyEntry> entries = filteredVocabulary();
            if (index < entries.size()) selectedWord = entries.get(index).getWord();
        } else if (screen == Screen.SETTINGS) {
            handleSettingsClick(x, y, width);
        } else if (screen == Screen.SAVES) {
            handleSaveClick(x, y, width, height);
        }

        requestFocusInWindow();
        refreshStoryVisibility();
        repaint();
    }

    private boolean isBackButton(int x, int y, int width) {
        return x >= width - 150 && x <= width - 20 && y >= 20 && y <= 75;
    }

    private void handleTitleClick(int x, int y, int width, int height) {
        int uiX = x * 960 / Math.max(1, width);
        int uiY = y * 576 / Math.max(1, height);
        if (uiX >= 560 && uiX <= 804 && uiY >= 430 && uiY <= 472
                && story != null && story.started() && !story.completed()) { resumeStory(); return; }
        if (uiX < 78 || uiX > 339) {
            return;
        }
        if (uiY >= 214 && uiY < 256) {
            startNewStory();
        } else if (uiY >= 264 && uiY < 306) {
            loadGame();
            showAbout = false;
        } else if (uiY >= 314 && uiY < 356) {
            openScreen(Screen.DICTIONARY);
        } else if (uiY >= 364 && uiY < 406) {
            openScreen(Screen.SETTINGS);
        } else if (uiY >= 414 && uiY < 456) {
            showAbout = true;
        } else if (uiY >= 464 && uiY < 506) {
            if (JOptionPane.showConfirmDialog(this, "Exit the game? Save first to keep your progress.",
                    "Exit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                if (gameTimer != null) gameTimer.stop();
                java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (window != null) window.dispose();
            }
        }
    }

    private void handleGameClick(int x, int y, int width, int height) {
        if (talking) {
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
            openStoryJournal();
        } else if (x < 575) {
            openScreen(Screen.SETTINGS);
        } else if (x <= 678) {
            resumeStory();
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
            JOptionPane.showMessageDialog(this, "Story: Enter/Space continues, 1-3 chooses an answer.\nType replies and press Enter to send.\nEsc opens the map; E resumes the story.\nCtrl+S saves; Ctrl+L loads. Words opens your vocabulary log.", "Controls", JOptionPane.INFORMATION_MESSAGE);
        } else {
            openScreen(destination);
        }
    }

    private void selectDictionaryFilter(int x) {
        int left = 198;
        for (int i = 0; i < LANGUAGE_FILTERS.length; i++) {
            if (x >= left && x <= left + FILTER_WIDTHS[i]) { dictionaryFilter = LANGUAGE_FILTERS[i]; return; }
            left += FILTER_WIDTHS[i] + 8;
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
        if (y >= 450 && y <= 494) {
            if (x >= 290 && x <= 530) saveGame();
            else if (x >= 560 && x <= 840) loadGame();
        }
    }

    private void openScreen(Screen destination) {
        if (destination != screen) {
            if (screen == Screen.TITLE || screen == Screen.GAME) {
                returnScreen = screen;
                returnTalking = talking;
            }
            screen = destination;
            clearMovement();
        }
    }

    private void goBack() {
        if (screen == Screen.GAME) { talking = false; screen = Screen.TITLE; }
        else { screen = returnScreen; talking = returnTalking; }
        clearMovement();
    }

    private void saveGame() {
        if (story == null || !story.started()) { showNotice("Start a story before saving."); return; }
        try {
            progress.setStoryValue("map", Integer.toString(currentMap));
            progress.setStoryValue("worldX", Integer.toString(player.worldX));
            progress.setStoryValue("worldY", Integer.toString(player.worldY));
            files.saveProgress(progress);
            showNotice("Game saved.");
        } catch (IOException e) {
            showNotice("Could not save game.");
        }
    }

    private void loadGame() {
        if (story == null) { showNotice("The storyline is unavailable."); return; }
        try {
            GameProgress loaded = files.loadProgress();
            if (loaded.getStoryValue("node").isEmpty() && loaded.getStoryState().isEmpty()) {
                showNotice("No storyline save found. Choose New Game to begin."); return;
            }
            int[] location = readSavedLocation(loaded);
            story.load(loaded);
            player.setDefaultValues();
            currentMap = location[0];
            player.worldX = location[1];
            player.worldY = location[2];
            screen = Screen.GAME; returnScreen = Screen.GAME; talking = true;
            storyView.resetPresentation();
            clearMovement();
            refreshStoryVisibility();
            showNotice("Save loaded.");
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Could not load story", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private int[] readSavedLocation(GameProgress saved) {
        String mapValue = saved.getStoryValue("map");
        String xValue = saved.getStoryValue("worldX");
        String yValue = saved.getStoryValue("worldY");
        if (mapValue.isEmpty() && xValue.isEmpty() && yValue.isEmpty())
            return new int[]{0, tileSize * 10, tileSize * 10};
        try {
            int map = Integer.parseInt(mapValue);
            int x = Integer.parseInt(xValue);
            int y = Integer.parseInt(yValue);
            if (map < 0 || map > 1 || x < 0 || x >= maxWorldCol * tileSize
                    || y < 0 || y >= maxWorldRow * tileSize)
                throw new NumberFormatException("Out of bounds");
            return new int[]{map, x, y};
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("The saved map position is invalid.", error);
        }
    }
}
