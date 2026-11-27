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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JPanel;
import javax.swing.JOptionPane;

import ai.TutorService;
import data.FileManager;
import data.GameProgress;
import data.VocabularyEntry;
import entity.Entity;
import entity.Player;
import tile.TileManager;
import story.StoryManager;
import story.StoryScript;
import story.StoryView;

public class GamePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private enum Screen { INTRO, TITLE, GAME, DICTIONARY, SETTINGS, SAVES }
    private Screen screen = Screen.INTRO;
    private Screen returnScreen = Screen.GAME;
    private static final int INTRO_FADE_IN_FRAMES = 45;
    private static final int INTRO_HOLD_FRAMES = 105;
    private static final int INTRO_FADE_OUT_FRAMES = 45;
    private static final int INTRO_END_PAUSE_FRAMES = 15;
    private static final int INTRO_TOTAL_FRAMES =
            INTRO_FADE_IN_FRAMES + INTRO_HOLD_FRAMES + INTRO_FADE_OUT_FRAMES + INTRO_END_PAUSE_FRAMES;
    private int introFrame;
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
    /** AI tutor for typed answers: Gemini when GEMINI_API_KEY is set, otherwise offline. */
    private final TutorService tutor = TutorService.fromEnvironment();
    private boolean skipUnseen = true;
    private boolean fullscreen;
    private static final String[] WINDOW_PRESET_LABELS = {
        "3:2 (1152x768)", "16:9 (1280x720)", "16:9 (1600x900)", "4:3 (1024x768)", "1:1 (720x720)"
    };
    private static final int[][] WINDOW_PRESET_SIZES = {
        {1152, 768}, {1280, 720}, {1600, 900}, {1024, 768}, {720, 720}
    };
    private int windowPresetIndex = 0;
    private boolean customWindowSize;
    private int windowWidth = 1152;
    private int windowHeight = 768;
    private boolean skipAfterChoices;
    private boolean muteAll;
    private Clip menuMusicClip;
    private Clip gameplayMusicClip;
    private final java.util.Map<String, Clip> sfxClips = new java.util.HashMap<>();
    private String lastHoveredButton = "";
    private int activeSlider = -1;
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
    public AssetSetter aSetter = new AssetSetter(this);

    // ENTITIES & OBJECTS
    public Player player = new Player(this, keyH);
    public Entity npc[][] = new Entity[maxMap][10];
    ArrayList<Entity> entityList = new ArrayList<>();

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
        setupGame();
        initMenuMusic();
        initGameplayMusic();
        initSfx();
        try {
            story = new StoryManager(StoryScript.load(), progress);
            storyView = new StoryView(story, progress, this::handleStoryAction);
            storyView.setTutor(tutor);
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
                spawnClickHearts(toUiX(e.getX()), toUiY(e.getY()));
                handleClick(e.getX(), e.getY());
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                activeSlider = -1;
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                pointerX = toUiX(e.getX());
                pointerY = toUiY(e.getY());
                repaint();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                pointerX = toUiX(e.getX());
                pointerY = toUiY(e.getY());
                if (screen == Screen.SETTINGS && activeSlider != -1) {
                    applySliderDrag(pointerX);
                    repaint();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (screen == Screen.INTRO) {
                    if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ESCAPE) {
                        screen = Screen.TITLE;
                        startMenuMusic();
                        repaint();
                    }
                    return;
                }
                if (code == KeyEvent.VK_ESCAPE && screen != Screen.TITLE) {
                    if (screen == Screen.GAME && talking) talking = false;
                    else goBack();
                }
                else if (code == KeyEvent.VK_ENTER && screen == Screen.TITLE) startNewStory();
                else if (code == KeyEvent.VK_E && screen == Screen.GAME && !talking) {
                    playSfx("interact");
                    interactWithNearbyNPC();
                }
                else if (e.isControlDown() && code == KeyEvent.VK_S) saveGame();
                else if (e.isControlDown() && code == KeyEvent.VK_L) loadGame();
                refreshStoryVisibility();
                repaint();
            }
            @Override public void keyTyped(KeyEvent e) {
                if (screen == Screen.DICTIONARY) {
                    char c = e.getKeyChar();
                    if (c == '\b' && !searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
                    else if (!Character.isISOControl(c) && searchText.length() < 40) searchText += c;
                    dictionaryPage = 0;
                    repaint();
                }
            }
        });
    }

    public void setupGame() {
        aSetter.setNPC();
    }

    // ── Music ───────────────────────────────────────────────────────────
    private Clip loadMusicClip(String resourcePath) {
        try {
            InputStream raw = getClass().getResourceAsStream(resourcePath);
            if (raw == null) { System.err.println("Music file not found: " + resourcePath); return null; }
            AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            ais.close();
            return clip;
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Could not load music (" + resourcePath + "): " + e.getMessage());
            return null;
        }
    }

    private void initMenuMusic() {
        menuMusicClip = loadMusicClip("/assets/audio/menuMusic.wav");
    }

    private void initGameplayMusic() {
        gameplayMusicClip = loadMusicClip("/assets/audio/gameplayMusic.wav");
    }

    private void startMenuMusic() {
        if (menuMusicClip == null) return;
        applyClipVolume(menuMusicClip, musicVolume);
        menuMusicClip.setFramePosition(0);
        menuMusicClip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    private void stopMenuMusic() {
        if (menuMusicClip != null && menuMusicClip.isRunning()) menuMusicClip.stop();
    }

    private void startGameplayMusic(boolean restart) {
        if (gameplayMusicClip == null) return;
        applyClipVolume(gameplayMusicClip, musicVolume);
        if (restart) {
            gameplayMusicClip.stop();
            gameplayMusicClip.setFramePosition(0);
            gameplayMusicClip.loop(Clip.LOOP_CONTINUOUSLY);
        } else if (!gameplayMusicClip.isRunning()) {
            gameplayMusicClip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    private void pauseGameplayMusic() {
        if (gameplayMusicClip != null && gameplayMusicClip.isRunning()) gameplayMusicClip.stop();
    }

    private void applyMusicVolume() {
        applyClipVolume(menuMusicClip, musicVolume);
        applyClipVolume(gameplayMusicClip, musicVolume);
    }

    private void initSfx() {
        String[] names = {
            "affection_down", "affection_up", "correct_answer",
            "enter_exit_game", "hover_button", "incorrect_answer",
            "interact", "selected_button"
        };
        for (String name : names) {
            Clip clip = loadMusicClip("/assets/audio/sfx/" + name + ".wav");
            if (clip != null) sfxClips.put(name, clip);
        }
    }

    public void playSfx(String name) {
        Clip clip = sfxClips.get(name);
        if (clip == null || !clip.isOpen()) return;
        applyClipVolume(clip, soundVolume);
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    private void applyClipVolume(Clip clip, int volume) {
        if (clip == null || !clip.isOpen()) return;
        boolean muted = muteAll || volume == 0;
        if (clip.isControlSupported(BooleanControl.Type.MUTE)) {
            ((BooleanControl) clip.getControl(BooleanControl.Type.MUTE)).setValue(muted);
        }
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            if (muted) {
                gain.setValue(gain.getMinimum());
            } else {
                // Map 1-100 → -40 dB … 0 dB (logarithmic)
                float dB = 20f * (float) Math.log10(volume / 100.0);
                dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
                gain.setValue(dB);
            }
        }
    }

    public void startGameThread() {
        if (gameTimer == null) gameTimer = new javax.swing.Timer(1000 / FPS, e -> { update(); repaint(); });
        gameTimer.start();
    }

    @Override public void removeNotify() {
        stopMenuMusic();
        pauseGameplayMusic();
        if (menuMusicClip != null) { menuMusicClip.close(); menuMusicClip = null; }
        if (gameplayMusicClip != null) { gameplayMusicClip.close(); gameplayMusicClip = null; }
        if (gameTimer != null) gameTimer.stop();
        super.removeNotify();
    }

    private void clearMovement() {
        keyH.upPressed = keyH.downPressed = keyH.leftPressed = keyH.rightPressed = false;
    }

    private double uiScale() {
        return Math.min(Math.max(1, getWidth()) / 960.0, Math.max(1, getHeight()) / 576.0);
    }

    private int uiDrawWidth() {
        return (int) Math.round(960 * uiScale());
    }

    private int uiDrawHeight() {
        return (int) Math.round(576 * uiScale());
    }

    private int uiOffsetX() {
        return (getWidth() - uiDrawWidth()) / 2;
    }

    private int uiOffsetY() {
        return (getHeight() - uiDrawHeight()) / 2;
    }

    private int toUiX(int panelX) {
        return (int) Math.floor((panelX - uiOffsetX()) / uiScale());
    }

    private int toUiY(int panelY) {
        return (int) Math.floor((panelY - uiOffsetY()) / uiScale());
    }

    @Override public void doLayout() {
        if (storyView != null) storyView.setBounds(uiOffsetX(), uiOffsetY(), uiDrawWidth(), uiDrawHeight());
    }

    private void refreshStoryVisibility() {
        if (storyView == null) return;
        storyView.setLocationName(switch (currentMap) {
            case EventHandler.MAP_COURTYARD -> "Courtyard";
            case EventHandler.MAP_HALLWAY   -> "Hallway";
            case EventHandler.MAP_CLASSROOM -> "Classroom";
            case EventHandler.MAP_LIBRARY   -> "Library";
            case EventHandler.MAP_GARDEN    -> "Garden";
            case EventHandler.MAP_HOMETOWN  -> "Hometown";
            case EventHandler.MAP_BEDROOM   -> "Bedroom";
            default -> "Campus";
        });
        storyView.setVisible(screen == Screen.GAME && talking && story.started());
        storyView.setHintsEnabled(showHints);
        storyView.setTextSpeed(textSpeed);
        storyView.refresh();
        if (!storyView.isVisible()) requestFocusInWindow();
    }

    private void startNewStory() {
        if (story == null) { showNotice("The storyline is unavailable."); return; }
        if (story.started() && !story.completed() && JOptionPane.showConfirmDialog(this,
                "Start a new story? Unsaved progress will be replaced.", "New Game",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        stopMenuMusic();
        playSfx("enter_exit_game");
        startGameplayMusic(true);
        story.start(); storyView.resetPresentation(); player.setDefaultValues(); currentMap = 0; clearMovement();
        showAbout = false; screen = Screen.GAME; returnScreen = Screen.GAME; talking = true;
        refreshStoryVisibility();
    }

    private Entity findNearbyNPC() {
        double maxDist = tileSize * 1.6;
        for (int i = 0; i < npc[currentMap].length; i++) {
            Entity candidate = npc[currentMap][i];
            if (candidate == null) continue;
            double dx = player.worldX - candidate.worldX;
            double dy = player.worldY - candidate.worldY;
            if (Math.hypot(dx, dy) <= maxDist) return candidate;
        }
        return null;
    }

    private void interactWithNearbyNPC() {
        int expectedMap = (story != null && story.started()) ? expectedMapForScene(story.current().scene) : -1;
        boolean hasNpcOnMap = false;
        for (int i = 0; i < npc[currentMap].length; i++) {
            if (npc[currentMap][i] != null) { hasNpcOnMap = true; break; }
        }
        if (!hasNpcOnMap && expectedMap != -1 && currentMap == expectedMap) {
            resumeStory();
            return;
        }
        Entity nearby = findNearbyNPC();
        if (nearby == null) {
            showNotice("Walk up to an NPC and press E to talk.");
            return;
        }
        if (expectedMap != -1 && currentMap != expectedMap) {
            showNotice("Head to " + mapDisplayName(expectedMap) + " to continue this scene.");
            return;
        }
        if (story != null && story.started()) {
            String requiredSprite = requiredNpcSpriteForScene(story.current().scene);
            if (requiredSprite != null && nearby instanceof entity.NPC_Courtyard npcObj
                    && !requiredSprite.equals(npcObj.getSpriteName())) {
                String who = switch (requiredSprite) {
                    case "jessaNPC" -> "Jessa";
                    case "innaNPC"  -> "Inna";
                    case "nikkaNPC" -> "Nikka";
                    default -> "the right character";
                };
                showNotice("Talk to " + who + " to continue this scene.");
                return;
            }
        }
        resumeStory();
    }

    private int expectedMapForScene(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) return -1;
        return switch (sceneId) {
            case "M01" -> EventHandler.MAP_COURTYARD;
            case "M02", "S1-01", "S1-02A", "S1-02B", "S1-03", "S2-01",
                 "J2-02", "J2-05", "J3-00", "J4-00", "J4-01", "J-F",
                 "I2-02", "I2-05", "I3-00", "I4-00", "I4-01", "I-F",
                 "N2-02", "N2-05", "N3-00", "N4-00", "N4-01", "N-F" -> EventHandler.MAP_CLASSROOM;
            case "S1-04",
                 "J2-01", "J2-03", "J2-04", "J2-06", "J2-L", "J3-S",
                 "I2-01", "I2-03", "I2-04", "I2-06", "I2-L", "I3-S", "I4-02", "I-R", "I-N",
                 "N2-01", "N2-03", "N2-04", "N2-06", "N2-L", "N3-S" -> EventHandler.MAP_LIBRARY;
            case "J3-01", "I3-01", "N3-01", "N3-03" -> EventHandler.MAP_HOMETOWN;
            case "J3-02", "J3-03", "I3-02", "I3-03", "N3-02" -> EventHandler.MAP_BEDROOM;
            case "J4-02", "J-R", "J-N", "N4-02", "N-R", "N-N" -> EventHandler.MAP_GARDEN;
            default -> -1;
        };
    }

    private String requiredNpcSpriteForScene(String sceneId) {
        if (sceneId == null) return null;
        return switch (sceneId) {
            case "S1-02A", "J2-01", "J2-04", "J2-05", "J2-L", "J3-01", "J3-02", "J3-03",
                 "J4-01", "J4-02", "J-R", "J-F", "J-N" -> "jessaNPC";
            case "S1-02B", "I2-01", "I2-02", "I2-04", "I2-05", "I2-L", "I3-01", "I3-02",
                 "I3-03", "I4-00", "I4-01", "I4-02", "I-R", "I-F", "I-N" -> "innaNPC";
            case "S1-03", "N2-01", "N2-02", "N2-04", "N2-L", "N3-01", "N3-02", "N3-03",
                 "N4-01", "N4-02", "N-R", "N-F", "N-N" -> "nikkaNPC";
            default -> null;
        };
    }

    private String questGoalText() {
        if (story == null || !story.started()) return "";
        String sceneId = story.current().scene;
        int map = expectedMapForScene(sceneId);
        String loc = map == -1 ? "Campus" : mapDisplayName(map);
        String who = switch (sceneId) {
            case "M02", "S1-01", "S2-01", "J2-02", "J2-06", "J3-00", "J4-00",
                 "I2-06", "I3-00", "N2-05", "N2-06", "N3-00", "N4-00" -> "Talk to the club (E)";
            case "S1-02A", "J2-01", "J2-04", "J2-05", "J2-L", "J3-01", "J3-02", "J3-03",
                 "J4-01", "J4-02", "J-R", "J-F", "J-N" -> "Talk to Jessa (E)";
            case "S1-02B", "I2-01", "I2-02", "I2-04", "I2-05", "I2-L", "I3-01", "I3-02",
                 "I3-03", "I4-00", "I4-01", "I4-02", "I-R", "I-F", "I-N" -> "Talk to Inna (E)";
            case "S1-03", "N2-01", "N2-02", "N2-04", "N2-L", "N3-01", "N3-02", "N3-03",
                 "N4-01", "N4-02", "N-R", "N-F", "N-N" -> "Talk to Nikka (E)";
            case "S1-04", "J2-03", "J3-S", "I2-03", "I3-S", "N2-03", "N3-S" -> "Study Break (Press E)";
            default -> "Press E to continue";
        };
        return loc + " — " + who;
    }

    /** Draws a small directional arrow around the player pointing toward the current quest goal. */
    private void drawQuestArrow(Graphics2D g) {
        int targetMap = expectedMapForScene(story.current().scene);
        if (targetMap < 0) return;

        int targetWorldX, targetWorldY;
        if (currentMap == targetMap) {
            entity.Entity targetNpc = findTargetNpc(currentMap, story.current().scene);
            if (targetNpc == null) return;
            targetWorldX = targetNpc.worldX + tileSize / 2;
            targetWorldY = targetNpc.worldY + tileSize / 2;
        } else {
            int[] exit = eHandler.getNextExitTowards(currentMap, targetMap);
            if (exit == null) return;
            targetWorldX = exit[0] * tileSize + tileSize / 2;
            targetWorldY = exit[1] * tileSize + tileSize / 2;
        }

        int playerCenterX = player.worldX + tileSize / 2;
        int playerCenterY = player.worldY + tileSize / 2;
        double dx = targetWorldX - playerCenterX;
        double dy = targetWorldY - playerCenterY;
        double dist = Math.hypot(dx, dy);
        if (dist < tileSize * 1.1) return; // Already right next to the goal

        double angle = Math.atan2(dy, dx);
        // Player's screen center in virtual 960x576 coordinates
        double screenCenterX = (player.screenX + tileSize / 2.0) * 960.0 / screenWidth;
        double screenCenterY = (player.screenY + tileSize / 2.0) * 576.0 / screenHeight;
        double bob = Math.sin(animationFrame * 0.14) * 2.5;
        double orbitRadius = 46.0 + bob * 0.6;
        double ax = screenCenterX + Math.cos(angle) * orbitRadius;
        double ay = screenCenterY + Math.sin(angle) * orbitRadius;

        Graphics2D arrowG = (Graphics2D) g.create();
        arrowG.translate(ax, ay);
        arrowG.rotate(angle);
        int[] xs = { 9, -6, -3, -6 };
        int[] ys = { 0, -7, 0, 7 };
        arrowG.setColor(softYellow);
        arrowG.fillPolygon(xs, ys, 4);
        arrowG.setColor(ink);
        arrowG.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        arrowG.drawPolygon(xs, ys, 4);
        arrowG.dispose();
    }

    /** Draws a small bobbing ▼ marker above the quest-target NPC's head when on the same map. */
    private void drawTargetNpcMarker(Graphics2D g) {
        int targetMap = expectedMapForScene(story.current().scene);
        if (targetMap < 0 || currentMap != targetMap) return;
        entity.Entity targetNpc = findTargetNpc(currentMap, story.current().scene);
        if (targetNpc == null) return;

        int worldCenterX = targetNpc.worldX + tileSize / 2;
        int worldTopY = targetNpc.worldY - tileSize / 6;
        double sx = (worldCenterX - player.worldX + player.screenX) * 960.0 / screenWidth;
        double sy = (worldTopY - player.worldY + player.screenY) * 576.0 / screenHeight;
        if (sx < 10 || sx > 950 || sy < 10 || sy > 566) return;

        double bob = Math.sin(animationFrame * 0.14) * 3.0;
        int cx = (int) Math.round(sx);
        int cy = (int) Math.round(sy + bob);
        int[] xs = { cx - 7, cx + 7, cx };
        int[] ys = { cy - 9, cy - 9, cy + 2 };
        g.setColor(pink);
        g.fillPolygon(xs, ys, 3);
        g.setColor(ink);
        g.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.drawPolygon(xs, ys, 3);
    }

    private entity.Entity findTargetNpc(int map, String sceneId) {
        String wanted = switch (sceneId) {
            case "S1-02A", "J2-01", "J2-04", "J2-05", "J2-L", "J3-01", "J3-02", "J3-03",
                 "J4-01", "J4-02", "J-R", "J-F", "J-N" -> "jessaNPC";
            case "S1-02B", "I2-01", "I2-02", "I2-04", "I2-05", "I2-L", "I3-01", "I3-02",
                 "I3-03", "I4-00", "I4-01", "I4-02", "I-R", "I-F", "I-N" -> "innaNPC";
            case "S1-03", "N2-01", "N2-02", "N2-04", "N2-L", "N3-01", "N3-02", "N3-03",
                 "N4-01", "N4-02", "N-R", "N-F", "N-N" -> "nikkaNPC";
            default -> null;
        };
        entity.Entity fallback = null;
        for (entity.Entity e : npc[map]) {
            if (e == null) continue;
            if (fallback == null) fallback = e;
            if (wanted != null && e instanceof entity.NPC_Courtyard c && wanted.equals(c.getSpriteName())) {
                return e;
            }
        }
        return fallback;
    }

    private String mapDisplayName(int map) {
        return switch (map) {
            case EventHandler.MAP_COURTYARD -> "the Courtyard";
            case EventHandler.MAP_HALLWAY   -> "the Hallway";
            case EventHandler.MAP_CLASSROOM -> "Room 401 (Classroom)";
            case EventHandler.MAP_LIBRARY   -> "the Library";
            case EventHandler.MAP_GARDEN    -> "the Garden / Balcony";
            case EventHandler.MAP_HOMETOWN  -> "the Hometown";
            case EventHandler.MAP_BEDROOM   -> "Home";
            default -> "the next area";
        };
    }

    private void resumeStory() {
        if (story == null || !story.started()) { startNewStory(); return; }
        stopMenuMusic();
        playSfx("enter_exit_game");
        startGameplayMusic(false);
        screen = Screen.GAME; talking = true;
        keyH.upPressed = keyH.downPressed = keyH.leftPressed = keyH.rightPressed = false;
        refreshStoryVisibility();
    }

    private void handleStoryAction(String action) {
        if (action.startsWith("Sfx:")) {
            playSfx(action.substring(4));
            return;
        }
        if (action.startsWith("ClickHearts:")) {
            String[] parts = action.substring("ClickHearts:".length()).split(",");
            if (parts.length == 2) {
                try {
                    spawnClickHearts(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) { }
            }
            return;
        }
        switch (action) {
            case "Save" -> saveGame();
            case "Load" -> loadGame();
            case "Words" -> { dictionaryPage = 0; openScreen(Screen.DICTIONARY); }
            case "Options" -> openScreen(Screen.SETTINGS);
            case "Map" -> { talking = false; screen = Screen.GAME; clearMovement(); }
            case "SceneCut" -> {
                talking = false; screen = Screen.GAME; clearMovement();
                aSetter.updateNPCsForScene(story.current().scene);
                int nextMap = expectedMapForScene(story.current().scene);
                if (nextMap != -1) {
                    showNotice("Head to " + mapDisplayName(nextMap) + " and press E on an NPC to continue.");
                }
            }
            case "Menu" -> { talking = false; screen = Screen.TITLE; clearMovement(); pauseGameplayMusic(); startMenuMusic(); }
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
        if (screen == Screen.INTRO) {
            introFrame++;
            if (introFrame >= INTRO_TOTAL_FRAMES) {
                screen = Screen.TITLE;
                startMenuMusic();
            }
        }
        if (!notice.isEmpty() && System.currentTimeMillis() >= noticeExpiresAt) notice = "";
        if (screen == Screen.GAME && !talking) {
            player.update();
            for (int i = 0; i < npc[currentMap].length; i++) {
                if (npc[currentMap][i] != null) {
                    npc[currentMap][i].update();
                }
            }
            eHandler.checkEvent();
        }
        updateClickHearts();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (screen == Screen.GAME) {
            long drawStart = keyH.checkDrawTime ? System.nanoTime() : 0;

            g2.setColor(ink);
            g2.fillRect(0, 0, getWidth(), getHeight());

            double worldScale = Math.min(Math.max(1, getWidth()) / (double) screenWidth,
                    Math.max(1, getHeight()) / (double) screenHeight);
            int worldDrawW = (int) Math.round(screenWidth * worldScale);
            int worldDrawH = (int) Math.round(screenHeight * worldScale);
            int worldOffX = (getWidth() - worldDrawW) / 2;
            int worldOffY = (getHeight() - worldDrawH) / 2;

            Graphics2D worldG = (Graphics2D) g2.create();
            worldG.setClip(worldOffX, worldOffY, worldDrawW, worldDrawH);
            worldG.translate(worldOffX, worldOffY);
            worldG.scale(worldScale, worldScale);

            tileM.draw(worldG);

            entityList.add(player);
            for (int i = 0; i < npc[currentMap].length; i++) {
                if (npc[currentMap][i] != null) {
                    entityList.add(npc[currentMap][i]);
                }
            }
            Collections.sort(entityList, Comparator.comparingInt(e -> e.worldY));
            for (int i = 0; i < entityList.size(); i++) {
                entityList.get(i).draw(worldG);
            }
            entityList.clear();
            worldG.dispose();

            if (keyH.checkDrawTime) {
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.drawString("Draw time: " + (System.nanoTime() - drawStart) / 1_000_000.0 + " ms", 14, 86);
            }
            g2.translate(uiOffsetX(), uiOffsetY());
            g2.scale(uiScale(), uiScale());
            drawGameUi(g2);
        } else {
            g2.setColor(cream);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.translate(uiOffsetX(), uiOffsetY());
            g2.scale(uiScale(), uiScale());
            switch (screen) {
                case INTRO -> drawIntro(g2);
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
        overlay.setColor(new Color(255, 160, 180, 22));
        overlay.fillRect(0, 0, getWidth(), getHeight());
        drawNotice(overlay);
        overlay.translate(uiOffsetX(), uiOffsetY());
        overlay.scale(uiScale(), uiScale());
        drawClickHearts(overlay);
        overlay.dispose();
    }

    private void drawIntro(Graphics2D g) {
        Graphics2D ui = (Graphics2D) g.create();
        ui.setColor(cream);
        ui.fillRect(0, 0, 960, 576);
        drawTitlePlaid(ui);

        float alpha;
        float fadeInProgress = Math.min(1f, introFrame / (float) INTRO_FADE_IN_FRAMES);
        int fadeOutStart = INTRO_FADE_IN_FRAMES + INTRO_HOLD_FRAMES;
        if (introFrame < INTRO_FADE_IN_FRAMES) {
            alpha = smoothstep(fadeInProgress);
        } else if (introFrame < fadeOutStart) {
            alpha = 1f;
        } else if (introFrame < fadeOutStart + INTRO_FADE_OUT_FRAMES) {
            float t = (introFrame - fadeOutStart) / (float) INTRO_FADE_OUT_FRAMES;
            alpha = 1f - smoothstep(t);
        } else {
            alpha = 0f;
        }

        if (alpha > 0f) {
            int driftY = (int) Math.round((1f - smoothstep(fadeInProgress)) * 10.0);
            ui.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            // Subtle pill accent behind the studio name matching the UI palette
            int badgeW = 430;
            int badgeH = 152;
            int badgeX = (960 - badgeW) / 2;
            int badgeY = 206 + driftY;
            ui.setColor(softYellow);
            ui.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 24, 24);
            ui.setColor(pink);
            ui.setStroke(new java.awt.BasicStroke(3f));
            ui.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 24, 24);

            ui.setColor(ink);
            ui.setFont(new Font("SansSerif", Font.BOLD, 17));
            drawCentered(ui, "AN ORIGINAL PROJECT BY", 480, 250 + driftY);

            ui.setColor(pink);
            ui.fillRoundRect(410, 264 + driftY, 140, 3, 3, 3);

            ui.setColor(raspberryPlum);
            ui.setFont(new Font("SansSerif", Font.BOLD, 30));
            drawCentered(ui, "POWERPUFFGIRLS", 480, 301 + driftY);

            ui.setColor(ink);
            ui.setFont(new Font("SansSerif", Font.PLAIN, 14));
            drawCentered(ui, "GROUP 3 - CS2103", 480, 331 + driftY);
        }
        ui.dispose();
    }

    private static float smoothstep(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return clamped * clamped * (3f - 2f * clamped);
    }

    private void drawTitle(Graphics2D g) {
        Graphics2D ui=(Graphics2D)g.create();ui.setColor(cream);ui.fillRect(0,0,960,576);
        drawTitlePlaid(ui);
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
        // Show partner name with affectionScore to match StoryView's affection bar
        String hudPartner = progress.getSelectedPartner();
        char hudInitial = data.GameProgress.partnerInitial(hudPartner);
        String hudAffText = (hudInitial != '\0')
                ? data.GameProgress.partnerName(hudPartner) + " ♥ " + progress.getAffectionScore()
                : "Affection " + progress.getAffectionScore();
        String hudAffLine = "Chapter " + progress.getCurrentStage() + "  •  " + hudAffText;
        g.setFont(new Font("SansSerif", Font.PLAIN, 14)); g.drawString(hudAffLine, 28, 55);
        if (!talking && story != null && story.started() && !story.completed()) {
            drawQuestArrow(g);
            drawTargetNpcMarker(g);
            String goal = questGoalText();
            if (!goal.isEmpty()) {
                panel(g, 14, 72, 310, 44, softYellow, 12);
                g.setColor(ink);
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.drawString("Goal: " + goal, 26, 99);
            }
        }
        if(!talking) panel(g, 14, h-66, w-28, 54, cream, 12);
        if(talking) return;
        if (findNearbyNPC() != null) {
            int promptBob = (int) Math.round(Math.sin(animationFrame * 0.12) * 2.5);
            panel(g, 415, h - 106 + promptBob, 130, 32, softYellow, 16);
            g.setColor(ink);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            drawCentered(g, "[E] Talk", 480, h - 85 + promptBob);
        }
        String[] labels={"Save","Load","Words","Journal","Options"};
        int x=24; int[] widths={92,92,106,112,112};
        for(int i=0;i<labels.length;i++){ button(g,x,h-57,widths[i],38,labels[i],cream); x+=widths[i]+8; }
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

    private String currentWindowSizeLabel() {
        if (customWindowSize) {
            return "Custom (" + windowWidth + "x" + windowHeight + ")";
        }
        return WINDOW_PRESET_LABELS[windowPresetIndex];
    }

    private void drawSettings(Graphics2D g) {
        int w=960,h=576;pageFrame(g,"Settings",w,h);
        int left=198, right=w-24, gap=34, colW=(right-left-gap)/2, rightX=left+colW+gap;
        g.setColor(ink);g.setFont(new Font("SansSerif",Font.PLAIN,20));g.drawString("Display",left,112);g.drawString("Skip",rightX,112);
        int halfBtn = (colW - 8) / 2;
        button(g,left,124,halfBtn,38,(fullscreen?"□":"✓")+" Window",fullscreen?cream:softYellow);
        button(g,left+halfBtn+8,124,colW-halfBtn-8,38,(fullscreen?"✓":"□")+" Fullscreen",fullscreen?softYellow:cream);
        int presetW = colW - 114;
        button(g,left,168,presetW,38,currentWindowSizeLabel(),fullscreen?cream:softYellow);
        button(g,left+presetW+8,168,106,38,"Custom...",customWindowSize&&!fullscreen?softYellow:cream);
        button(g,rightX,124,Math.min(205,colW),38,(skipUnseen?"✓":"□")+" Unseen text",skipUnseen?softYellow:cream);
        button(g,rightX,168,Math.min(205,colW),38,(skipAfterChoices?"✓":"□")+" After choices",skipAfterChoices?softYellow:cream);
        drawSlider(g,left,230,colW,"Text speed",textSpeed);drawSlider(g,rightX,230,colW,"Auto-forward time",autoForward);
        drawSlider(g,left,340,colW,"Music volume",musicVolume);drawSlider(g,rightX,340,colW,"Sound volume",soundVolume);
        g.setColor(ink);g.setFont(new Font("SansSerif",Font.PLAIN,20));g.drawString("Learning",left,445);
        button(g,left,458,Math.min(220,colW),38,(showHints?"✓":"□")+" Show English hints",showHints?softYellow:cream);
        boolean aiOn = tutor.isEnabled() && tutor.isAvailable();
        button(g,left+228,458,Math.min(124,colW-228),38,(aiOn?"✓":"□")+" AI tutor",aiOn?softYellow:cream);
        button(g,rightX,458,Math.min(180,colW),38,(muteAll?"✓":"□")+" Mute all",muteAll?softYellow:cream);
        g.setColor(ink);g.setFont(new Font("SansSerif",Font.PLAIN,12));
        g.drawString(tutor.isAvailable() ? "Click sliders to adjust. Preferences apply to the dialogue UI."
                : "Click sliders to adjust. The AI tutor needs a GEMINI_API_KEY on this computer.",left,h-22);
    }

    private void drawSlider(Graphics2D g, int x, int y, int width, String label, int value) {
        g.setColor(ink);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        g.drawString(label, x, y);
        String pct = value + "%";
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        int pctW = g.getFontMetrics().stringWidth(pct);
        g.drawString(pct, x + width - pctW, y);
        panel(g, x, y + 12, width, 18, softYellow, 9);

        int knobCenterX = x + 12 + (width - 24) * value / 100;
        g.setColor(pink);
        g.fillOval(knobCenterX - 14, y + 7, 28, 28);
        g.setColor(ink);
        g.drawOval(knobCenterX - 14, y + 7, 28, 28);
    }

    private void drawSaves(Graphics2D g) {
        pageFrame(g, "Save / Load", 960, 576);
        // ── Main card ──────────────────────────────────────────────────────
        panel(g, 216, 108, 690, 320, cream, 16);
        g.setColor(ink); g.setFont(new Font("SansSerif", Font.BOLD, 24));
        g.drawString("Your story so far", 240, 150);

        if (story != null && story.started()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            drawWrapped(g, "Scene: " + story.scene().title(), 240, 186, 635, 22, 2);

            // Chapter
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.drawString("Chapter " + progress.getCurrentStage(), 240, 222);

            // Words learned
            int wordCount = progress.getLearnedWords().size();
            g.drawString("Words learned: " + wordCount, 500, 222);

            // Partner + per-character rapport
            String partner = progress.getSelectedPartner();
            char initial = data.GameProgress.partnerInitial(partner);
            String partnerDisplay = partner.isEmpty() ? "Not chosen yet" : data.GameProgress.partnerName(partner);
            g.drawString("Partner: " + partnerDisplay, 240, 254);

            // Per-character rapport mini-display (selected partner reflects affectionScore)
            int rapJ = (initial == 'J') ? progress.getAffectionScore() : progress.getAffectionFor('J');
            int rapI = (initial == 'I') ? progress.getAffectionScore() : progress.getAffectionFor('I');
            int rapN = (initial == 'N') ? progress.getAffectionScore() : progress.getAffectionFor('N');
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            // Jessa – red tint label
            g.setColor(new java.awt.Color(200, 60, 60));   drawAffectionBar(g, "Jessa",  rapJ, 240, 284);
            // Inna – blue tint label
            g.setColor(new java.awt.Color(60,  110, 210)); drawAffectionBar(g, "Inna",   rapI, 420, 284);
            // Nikka – yellow/gold tint label
            g.setColor(new java.awt.Color(170, 130, 10));  drawAffectionBar(g, "Nikka",  rapN, 600, 284);
            g.setColor(ink);

            // Global affection (quiz score)
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.drawString("Quiz score: " + progress.getAffectionScore(), 240, 320);
        } else {
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.drawString("Start a new story or load your saved progress.", 240, 200);
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(new java.awt.Color(120, 120, 120));
        g.drawString("One save slot — saving replaces your previous progress.", 240, 403);
        g.setColor(ink);
        button(g, 290, 442, 240, 44, "Save progress", softYellow);
        button(g, 560, 442, 280, 44, "Load saved story", cream);
    }

    /** Draws a small labelled affection bar. maxRapport ≈ 10 based on script. */
    private void drawAffectionBar(Graphics2D g, String name, int value, int x, int y) {
        // Name label
        g.drawString(name + ": " + value, x, y);
        // Mini pip bar (5 pips, each pip = 2 rapport)
        int pips = 5;
        int filled = Math.min(pips, Math.max(0, value));
        int pw = 12, ph = 8, gap = 3;
        int barX = x, barY = y + 4;
        Color base = g.getColor();
        for (int p = 0; p < pips; p++) {
            g.setColor(p < filled ? base : new java.awt.Color(210, 210, 210));
            g.fillRoundRect(barX + p * (pw + gap), barY, pw, ph, 4, 4);
            g.setColor(ink);
            g.setStroke(new java.awt.BasicStroke(1f));
            g.drawRoundRect(barX + p * (pw + gap), barY, pw, ph, 4, 4);
            g.setStroke(new java.awt.BasicStroke(3));
        }
        g.setColor(base);
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
        if (hovered && !text.equals(lastHoveredButton)) {
            lastHoveredButton = text;
            playSfx("hover_button");
        }
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
        x = toUiX(x);
        y = toUiY(y);
        int width = 960;
        int height = 576;

        if (screen == Screen.INTRO) {
            screen = Screen.TITLE;
            startMenuMusic();
        } else if (screen != Screen.TITLE && screen != Screen.GAME && isBackButton(x, y, width)) {
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
        int colW = (width - 24 - left - 34) / 2;
        int rightX = left + colW + 34;
        int halfBtn = (colW - 8) / 2;
        int presetW = colW - 114;
        if (y >= 124 && y < 162) {
            if (x >= left && x < left + halfBtn) {
                applyDisplayMode(false);
            } else if (x >= left + halfBtn + 8 && x < rightX) {
                applyDisplayMode(true);
            } else if (x >= rightX) {
                skipUnseen = !skipUnseen;
            }
        } else if (y >= 168 && y < 206) {
            if (x >= left && x < left + presetW) {
                windowPresetIndex = (windowPresetIndex + 1) % WINDOW_PRESET_SIZES.length;
                customWindowSize = false;
                windowWidth = WINDOW_PRESET_SIZES[windowPresetIndex][0];
                windowHeight = WINDOW_PRESET_SIZES[windowPresetIndex][1];
                applyDisplayMode(false);
            } else if (x >= left + presetW + 8 && x < rightX) {
                promptCustomWindowSize();
            } else if (x >= rightX) {
                skipAfterChoices = !skipAfterChoices;
            }
        } else if (y >= 450 && y < 505) {
            if (x < left + 224) showHints = !showHints;
            else if (x < rightX) {
                if (tutor.isAvailable()) tutor.setEnabled(!tutor.isEnabled());
                else showNotice("The AI tutor needs a GEMINI_API_KEY on this computer.");
            }
            else muteAll = !muteAll;
            applyMusicVolume();
        } else if ((y >= 235 && y < 280) || (y >= 345 && y < 390)) {
            boolean rightColumn = x >= rightX - 16;
            boolean lowerRow = y >= 345;
            activeSlider = (lowerRow ? 2 : 0) + (rightColumn ? 1 : 0);
            applySliderDrag(x);
        }
    }

    private void promptCustomWindowSize() {
        javax.swing.JTextField wField = new javax.swing.JTextField(Integer.toString(windowWidth), 6);
        javax.swing.JTextField hField = new javax.swing.JTextField(Integer.toString(windowHeight), 6);
        javax.swing.JPanel form = new javax.swing.JPanel(new java.awt.GridLayout(2, 2, 8, 6));
        form.add(new javax.swing.JLabel("Width (480 - 3840):"));
        form.add(wField);
        form.add(new javax.swing.JLabel("Height (360 - 2160):"));
        form.add(hField);
        int choice = JOptionPane.showConfirmDialog(this, form, "Custom Window Size",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        try {
            int w = Integer.parseInt(wField.getText().trim());
            int h = Integer.parseInt(hField.getText().trim());
            if (w < 480 || w > 3840 || h < 360 || h > 2160) {
                showNotice("Size must be 480-3840 x 360-2160.");
                return;
            }
            windowWidth = w;
            windowHeight = h;
            customWindowSize = true;
            applyDisplayMode(false);
        } catch (NumberFormatException ex) {
            showNotice("Please enter valid whole numbers for width and height.");
        }
    }

    private void applyDisplayMode(boolean useFullscreen) {
        fullscreen = useFullscreen;
        java.awt.Window ancestor = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (!(ancestor instanceof javax.swing.JFrame frame)) {
            return;
        }
        java.awt.GraphicsDevice gd = frame.getGraphicsConfiguration().getDevice();
        if (useFullscreen) {
            if (!frame.isUndecorated()) {
                frame.dispose();
                frame.setUndecorated(true);
            }
            frame.setBounds(gd.getDefaultConfiguration().getBounds());
            frame.setVisible(true);
        } else {
            if (frame.isUndecorated()) {
                frame.dispose();
                frame.setUndecorated(false);
            }
            frame.setExtendedState(javax.swing.JFrame.NORMAL);
            setPreferredSize(new Dimension(windowWidth, windowHeight));
            frame.setSize(windowWidth, windowHeight);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
        revalidate();
        doLayout();
        repaint();
        requestFocusInWindow();
        showNotice(useFullscreen ? "Switched to Fullscreen." : "Window size: " + windowWidth + "x" + windowHeight);
    }

    private void applySliderDrag(int x) {
        int left = 198;
        int colW = (960 - 24 - left - 34) / 2;
        int rightX = left + colW + 34;
        boolean rightColumn = (activeSlider == 1 || activeSlider == 3);
        int columnX = rightColumn ? rightX : left;
        int value = Math.max(0, Math.min(100,
                (int) Math.round((x - (columnX + 12)) * 100.0 / Math.max(1, colW - 24))));
        switch (activeSlider) {
            case 0 -> textSpeed = value;
            case 1 -> autoForward = value;
            case 2 -> {
                musicVolume = value;
                applyMusicVolume();
            }
            case 3 -> soundVolume = value;
            default -> { }
        }
    }

    private void handleSaveClick(int x, int y, int width, int height) {
        if (y >= 442 && y <= 486) {
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
        if (screen == Screen.GAME) { talking = false; screen = Screen.TITLE; pauseGameplayMusic(); startMenuMusic(); }
        else { screen = returnScreen; talking = returnTalking; }
        clearMovement();
    }

    private void saveGame() {
        if (story == null || !story.started()) { showNotice("Start a story before saving."); return; }
        try {
            progress.setStoryValue("map", Integer.toString(currentMap));
            progress.setStoryValue("worldX", Integer.toString(player.worldX));
            progress.setStoryValue("worldY", Integer.toString(player.worldY));
            progress.setStoryValue("talking", Boolean.toString(talking));
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
            boolean savedTalking = "true".equals(loaded.getStoryValue("talking"));
            story.load(loaded);
            story.consumeSceneTransition();
            aSetter.updateNPCsForScene(story.current().scene);
            player.setDefaultValues();
            currentMap = location[0];
            player.worldX = location[1];
            player.worldY = location[2];
            stopMenuMusic();
            startGameplayMusic(true);
            screen = Screen.GAME; returnScreen = Screen.GAME; talking = savedTalking;
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
            if (map < 0 || map >= maxMap || x < 0 || x >= maxWorldCol * tileSize
                    || y < 0 || y >= maxWorldRow * tileSize)
                throw new NumberFormatException("Out of bounds");
            return new int[]{map, x, y};
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("The saved map position is invalid.", error);
        }
    }

    private static final class HeartParticle {
        double x, y, vx, vy;
        int size, life, maxLife;
        Color color;
        HeartParticle(double x, double y, double vx, double vy, int size, int maxLife, Color color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.size = size; this.life = maxLife; this.maxLife = maxLife; this.color = color;
        }
    }

    private final java.util.List<HeartParticle> clickHearts = new java.util.ArrayList<>();

    private void spawnClickHearts(int uiX, int uiY) {
        Color[] palette = {
            new Color(255, 115, 145),
            new Color(235, 75, 110),
            new Color(255, 165, 185),
            new Color(245, 200, 95)
        };
        for (int i = 0; i < 5; i++) {
            double angle = -Math.PI / 2 + (Math.random() - 0.5) * 1.6;
            double speed = 1.2 + Math.random() * 1.8;
            int size = 10 + (int) (Math.random() * 6);
            int life = 26 + (int) (Math.random() * 12);
            clickHearts.add(new HeartParticle(
                uiX + (Math.random() - 0.5) * 10,
                uiY + (Math.random() - 0.5) * 8,
                Math.cos(angle) * speed,
                Math.sin(angle) * speed - 0.6,
                size, life, palette[i % palette.length]
            ));
        }
    }

    private void updateClickHearts() {
        for (int i = clickHearts.size() - 1; i >= 0; i--) {
            HeartParticle p = clickHearts.get(i);
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.04;
            p.life--;
            if (p.life <= 0) clickHearts.remove(i);
        }
    }

    private void drawClickHearts(Graphics2D g2) {
        if (clickHearts.isEmpty()) return;
        Graphics2D g = (Graphics2D) g2.create();
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        for (HeartParticle p : clickHearts) {
            float alpha = Math.max(0f, Math.min(1f, p.life / (float) p.maxLife));
            g.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) (alpha * 235)));
            g.setFont(new Font("SansSerif", Font.BOLD, p.size));
            g.drawString("♥", (int) Math.round(p.x), (int) Math.round(p.y));
        }
        g.dispose();
    }

    private void drawTitlePlaid(Graphics2D ui) {
        Graphics2D g = (Graphics2D) ui.create();
        int step = 64;
        double drift = animationFrame * 0.35;

        // Layer 1: wider soft plaid (slower parallax + continuous rightward drift)
        double ox1 = (pointerX - 480) * 0.10 + drift;
        double oy1 = (pointerY - 288) * 0.10;
        double tx1 = ((ox1 % step) + step) % step - step;
        double ty1 = ((oy1 % step) + step) % step - step;

        Graphics2D l1 = (Graphics2D) g.create();
        l1.translate(tx1, ty1);
        l1.setStroke(new java.awt.BasicStroke(14f));
        l1.setColor(new Color(255, 178, 194, 48));
        for (int x = -800; x < 1800; x += step) {
            l1.drawLine(x, -128, x + 768, 704);
        }
        l1.setColor(new Color(242, 222, 165, 55));
        for (int x = -800; x < 1800; x += step) {
            l1.drawLine(x + 768, -128, x, 704);
        }
        l1.dispose();

        // Layer 2: thin accent grid (stronger parallax in all directions + faster drift)
        double ox2 = (pointerX - 480) * 0.18 + drift * 1.4;
        double oy2 = (pointerY - 288) * 0.18;
        double tx2 = ((ox2 % step) + step) % step - step;
        double ty2 = ((oy2 % step) + step) % step - step;

        Graphics2D l2 = (Graphics2D) g.create();
        l2.translate(tx2, ty2);
        l2.setStroke(new java.awt.BasicStroke(2f));
        l2.setColor(new Color(43, 25, 61, 20));
        for (int x = -800; x < 1800; x += step) {
            l2.drawLine(x, -128, x + 768, 704);
            l2.drawLine(x + 768, -128, x, 704);
        }
        l2.dispose();

        drawCornerHearts(g);
        g.dispose();
    }

    /** Litters the four corners of the window with slightly translucent red, pink, and yellow hearts. */
    private void drawCornerHearts(Graphics2D g) {
        Color[] palette = {
            new Color(230, 75, 105, 115),  // translucent red
            new Color(255, 145, 175, 120), // translucent pink
            new Color(245, 205, 95, 125)   // translucent yellow
        };
        // {baseX, baseY, size, colorIdx, phase}
        int[][] corners = {
            // Top-left
            {18, 32, 18, 0, 0}, {46, 22, 13, 1, 1}, {24, 58, 12, 2, 2}, {68, 38, 15, 0, 3}, {12, 82, 10, 1, 4},
            // Top-right
            {926, 30, 19, 1, 1}, {896, 22, 13, 2, 2}, {934, 58, 12, 0, 3}, {876, 40, 15, 1, 4}, {912, 80, 11, 2, 0},
            // Bottom-left
            {20, 552, 19, 2, 2}, {50, 560, 13, 0, 3}, {26, 524, 12, 1, 4}, {74, 544, 15, 2, 0}, {14, 502, 10, 0, 1},
            // Bottom-right
            {924, 554, 18, 0, 3}, {894, 562, 13, 1, 4}, {934, 526, 12, 2, 0}, {872, 546, 15, 0, 1}, {914, 504, 11, 1, 2}
        };
        double px = (pointerX - 480) * 0.03;
        double py = (pointerY - 288) * 0.03;
        for (int[] h : corners) {
            double bob = Math.sin(animationFrame * 0.05 + h[4]) * 2.5;
            g.setColor(palette[h[3]]);
            g.setFont(new Font("SansSerif", Font.BOLD, h[2]));
            g.drawString("♥", (int) Math.round(h[0] + px), (int) Math.round(h[1] + py + bob));
        }
    }
}
