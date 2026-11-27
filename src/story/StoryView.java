package story;

import ai.AiVerdict;
import ai.AnswerCheckRequest;
import ai.OfflineTutor;
import ai.TutorService;
import data.GameProgress;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

/** The existing pink/cream dialogue style, with real story text and paged choices. */
public final class StoryView extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color INK = new Color(0x2B193D), CREAM = new Color(0xF6E9C7),
            PINK = new Color(0xFFB2C2), YELLOW = new Color(0xF2DEA5);
    // Semi-transparent versions for the see-through dialogue overlay effect
    // Alpha 100/255 ≈ 39% opaque on the full backdrop (60-70% transparent)
    private static final Color CREAM_OVERLAY = new Color(0xF6, 0xE9, 0xC7, 100);
    // Panel cards are more opaque so text is readable (~82% opaque)
    private static final Color CREAM_PANEL  = new Color(0xF6, 0xE9, 0xC7, 210);
    private static final Color PINK_PANEL   = new Color(0xFF, 0xB2, 0xC2, 215);
    private static final Color YELLOW_PANEL = new Color(0xF2, 0xDE, 0xA5, 210);
    private final StoryManager story;
    private final GameProgress progress;
    private final Consumer<String> action;
    private final DialectHighlighter highlighter;
    private final StorySprites sprites = new StorySprites();
    private static final long SPRITE_ANIM_NANOS = 200_000_000L;
    private long spriteAnimStartNanos;
    private javax.swing.Timer spriteAnimTimer;
    private final JTextField reply = new JTextField();
    private final List<Hit> hits = new ArrayList<>();
    private record Hit(Rectangle box, Runnable action) {}
    private String displayedNode = "";
    private int page;
    private boolean hintsEnabled = true;
    private TutorService tutor = new TutorService(new OfflineTutor());
    private boolean checking;
    private int textSpeed = 50;
    private int revealedChars = Integer.MAX_VALUE;
    private int totalPageChars;
    private javax.swing.Timer typewriterTimer;

    public void setHintsEnabled(boolean enabled) { hintsEnabled = enabled; }
    public void setTextSpeed(int speed) { textSpeed = Math.max(0, Math.min(100, speed)); }
    /** The AI tutor used for typed answers; without one, only exact matches are accepted. */
    public void setTutor(TutorService tutor) { this.tutor = Objects.requireNonNull(tutor, "Tutor must not be null."); }
    public void resetPresentation() { displayedNode = ""; page = 0; reply.setText(""); revealedChars = Integer.MAX_VALUE; }

    public StoryView(StoryManager story, GameProgress progress, Consumer<String> action) {
        this.story = story; this.progress = progress; this.action = action;
        this.highlighter = new DialectHighlighter(story.script());
        setLayout(null); setFocusable(true); setBackground(new Color(0,0,0,0)); setOpaque(false);
        reply.setFont(new Font("SansSerif", Font.PLAIN, 20));
        reply.setForeground(INK); reply.setBackground(Color.WHITE);
        reply.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(INK, 2),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        reply.addActionListener(e -> submit());
        add(reply);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                int x = event.getX() * 960 / Math.max(1, getWidth());
                int y = event.getY() * 576 / Math.max(1, getHeight());
                action.accept("ClickHearts:" + x + "," + y);
                for (Hit hit : List.copyOf(hits)) if (hit.box.contains(x, y)) {
                    hit.action.run(); return;
                }
                if (!reply.isVisible()) {
                    requestFocusInWindow();
                    if (!story.acceptsText() && (story.answered() || !List.of("CHOICE", "QUIZ").contains(story.current().kind))) {
                        next();
                    }
                }
            }
        });
        bind("ENTER", "advance", this::next, WHEN_FOCUSED);
        bind("SPACE", "space", this::next, WHEN_FOCUSED);
        for (int i = 0; i < 3; i++) {
            final int choice = i;
            bind(Integer.toString(i + 1), "choice" + i, () -> choose(choice), WHEN_FOCUSED);
        }
        bind("control S", "save", () -> action.accept("Save"), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        bind("control L", "load", () -> action.accept("Load"), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        bind("ESCAPE", "map", () -> action.accept("Map"), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }
    private void bind(String key, String name, Runnable run, int scope) {
        getInputMap(scope).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { if (isVisible()) run.run(); }
        });
    }
    @Override public void doLayout() {
        reply.setBounds(390 * getWidth() / 960, 357 * getHeight() / 576,
                530 * getWidth() / 960, 46 * getHeight() / 576);
        float fontSize = Math.max(12f, 20f * getHeight() / 576f);
        reply.setFont(reply.getFont().deriveFont(fontSize));
    }
    public void refresh() {
        if (!story.started()) { reply.setVisible(false); return; }
        if (story.consumeSceneTransition()) {
            action.accept("SceneCut");
            return;
        }
        if (!displayedNode.equals(story.current().id)) {
            displayedNode = story.current().id; page = 0; reply.setText("");
            startSpritePopAnimation();
            startTypewriter();
        }
        int pages = textPages().size();
        page = Math.max(0, Math.min(page, pages - 1));
        totalPageChars = pageCharCount(textPages().get(page));
        if (revealedChars > totalPageChars) revealedChars = totalPageChars;
        reply.setVisible(isVisible() && story.acceptsText() && page == pages - 1);
        doLayout();
        if (isShowing()) {
            if (reply.isVisible()) reply.requestFocusInWindow();
            else requestFocusInWindow();
        }
        repaint();
    }
    private void startTypewriter() {
        if (textSpeed >= 98) {
            revealedChars = Integer.MAX_VALUE;
            if (typewriterTimer != null) typewriterTimer.stop();
            return;
        }
        revealedChars = 0;
        if (typewriterTimer == null) {
            typewriterTimer = new javax.swing.Timer(18, e -> {
                int step = 1 + (textSpeed / 30);
                revealedChars += step;
                if (revealedChars >= totalPageChars) {
                    revealedChars = totalPageChars;
                    typewriterTimer.stop();
                }
                repaint();
            });
        }
        typewriterTimer.restart();
    }

    private static int pageCharCount(List<DialectHighlighter.StyledLine> lines) {
        int count = 0;
        for (DialectHighlighter.StyledLine l : lines) count += l.text().length();
        return count;
    }

    private static List<DialectHighlighter.StyledLine> slicePageChars(
            List<DialectHighlighter.StyledLine> lines, int maxChars) {
        if (maxChars >= pageCharCount(lines)) return lines;
        List<DialectHighlighter.StyledLine> sliced = new ArrayList<>();
        int remaining = maxChars;
        for (DialectHighlighter.StyledLine l : lines) {
            if (remaining <= 0) break;
            String t = l.text();
            if (t.length() <= remaining) {
                sliced.add(l);
                remaining -= t.length();
            } else {
                sliced.add(new DialectHighlighter.StyledLine(t.substring(0, remaining), l.sourceStart()));
                remaining = 0;
            }
        }
        return sliced;
    }

    private void startSpritePopAnimation() {
        spriteAnimStartNanos = System.nanoTime();
        if (spriteAnimTimer == null) {
            spriteAnimTimer = new javax.swing.Timer(16, e -> {
                if (System.nanoTime() - spriteAnimStartNanos >= SPRITE_ANIM_NANOS) {
                    spriteAnimTimer.stop();
                }
                repaint();
            });
        }
        spriteAnimTimer.restart();
    }

    private double spriteAnimProgress() {
        if (spriteAnimStartNanos == 0L) return 1.0;
        return Math.min(1.0, (System.nanoTime() - spriteAnimStartNanos) / (double) SPRITE_ANIM_NANOS);
    }

    private List<List<DialectHighlighter.StyledLine>> textPages() {
        String formatted = story.format(story.current().text);
        List<DialectHighlighter.StyledLine> lines = highlighter.wrapWithOffsets(
                formatted, getFontMetrics(new Font("SansSerif", Font.PLAIN, 19)), 524);
        List<List<DialectHighlighter.StyledLine>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += 6) pages.add(lines.subList(i, Math.min(i + 6, lines.size())));
        if (pages.isEmpty()) pages.add(List.of(new DialectHighlighter.StyledLine("", 0)));
        return pages;
    }
    private void choose(int choice) {
        if (page != textPages().size() - 1) return;
        story.choose(choice); refresh();
        if (story.current().scene.equals("S1-04") && story.current().kind.equals("CHOICE") && choice == 0
                && story.value("selection." + story.current().id).equals("0"))
            action.accept("Words");
    }
    private void submit() {
        if (!reply.isVisible() || checking) return;
        String text = reply.getText();
        AnswerCheckRequest request = tutor.isReady() ? story.aiRequestFor(text) : null;
        if (request == null) {
            // No AI needed (exact match, blank, not a typed question) or AI off: normal checking.
            story.submit(text); refresh();
            if (reply.isVisible()) reply.selectAll();
            return;
        }
        // Ask the AI on a background thread so the game never freezes.
        String nodeId = story.current().id;
        checking = true;
        reply.setEnabled(false);
        repaint();
        new SwingWorker<Optional<AiVerdict>, Void>() {
            @Override protected Optional<AiVerdict> doInBackground() {
                return tutor.tryCheck(request);
            }
            @Override protected void done() {
                Optional<AiVerdict> verdict;
                try { verdict = get(); } catch (Exception error) { verdict = Optional.empty(); }
                checking = false;
                reply.setEnabled(true);
                story.submitWithAi(nodeId, text, verdict); // empty verdict = normal checking
                refresh();
                if (reply.isVisible()) reply.selectAll();
            }
        }.execute();
    }
    private void next() {
        if (revealedChars < totalPageChars) {
            revealedChars = totalPageChars;
            if (typewriterTimer != null) typewriterTimer.stop();
            repaint();
            return;
        }
        if (page + 1 < textPages().size()) { page++; startTypewriter(); }
        else if (story.completed()) { action.accept("Menu"); return; }
        else story.advance();
        refresh();
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!story.started()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.scale(getWidth() / 960.0, getHeight() / 576.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Semi-transparent backdrop — overworld shows through at ~60-70% transparency
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(CREAM_OVERLAY); g.fillRect(0, 0, 960, 576);
        hits.clear();
        panel(g, 18, 12, 660, 65, PINK_PANEL);
        g.setColor(new Color(110, 30, 58));
        g.setFont(new Font("Georgia", Font.BOLD, 28));
        drawLines(g, wrap(story.scene().title(), g.getFontMetrics(), 625), 31, 46, 30, 2);
        panel(g, 696, 12, 246, 65, CREAM_PANEL);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("Chapter " + progress.getCurrentStage(), 710, 37);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        if (progress.getSelectedPartner().isEmpty()) {
            g.drawString("Getting to know the club", 710, 61);
        } else {
            String partner = progress.getSelectedPartner();
            g.drawString(partner, 710, 60);
            int aff = progress.getAffectionScore();
            int maxAff = 10;
            double ratio = Math.max(0.0, Math.min(1.0, aff / (double) maxAff));
            int barX = 775, barY = 47, barW = 118, barH = 14;
            g.setColor(new Color(230, 215, 195));
            g.fillRoundRect(barX, barY, barW, barH, 8, 8);
            if (ratio > 0) {
                g.setColor(new Color(235, 95, 125));
                g.fillRoundRect(barX, barY, Math.max(6, (int) Math.round(barW * ratio)), barH, 8, 8);
            }
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(barX, barY, barW, barH, 8, 8);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("♥ " + aff, barX + barW + 6, 59);
        }

        sprites.drawPortraits(g, story.current(), 20, 85, 334, 385, spriteAnimProgress());
        String rawSpeaker = story.current().speaker;
        String speaker = rawSpeaker;
        if (speaker.equals("MC") || speaker.equals("You")) speaker = story.value("preferred_address").isBlank()
                ? "MC" : story.value("preferred_address");
        Color nameFill = speakerFillColor(rawSpeaker);
        Color nameBorder = speakerBorderColor(rawSpeaker);
        panel(g, 20, 446, 334, 65, nameFill, nameBorder);
        g.setColor(nameBorder);
        g.setFont(new Font("Georgia", Font.BOLD, 30));
        drawLines(g, wrap(speaker, g.getFontMetrics(), 306), 34, 483, 32, 2);

        panel(g, 373, 95, 568, 231, CREAM);
        g.setFont(new Font("SansSerif", Font.BOLD, 16)); g.drawString(story.current().speaker, 389, 122);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        String formattedText = story.format(story.current().text);
        List<List<DialectHighlighter.StyledLine>> pages = textPages();
        List<DialectHighlighter.StyledLine> currentLines = pages.get(Math.min(page, pages.size() - 1));
        highlighter.drawLines(g, formattedText, slicePageChars(currentLines, revealedChars), 390, 153, 25, INK);
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        if (pages.size() > 1) g.drawString("Page " + (page + 1) + " / " + pages.size(), 391, 313);
        if (page > 0) button(g, 682, 294, 112, 27, "Previous", CREAM, () -> { page--; refresh(); });
        if (page + 1 < pages.size()) {
            button(g, 805, 294, 118, 27, "Read more", PINK, this::next);
        } else controls(g);
        String[] actions = {"Save", "Load", "Words", "Journal", "Options", "Map", "Menu"};
        for (int i = 0; i < actions.length; i++) {
            String label = actions[i];
            button(g, 20 + i * 133, 531, 123, 32, label, i == 2 ? YELLOW : CREAM, () -> action.accept(label));
        }
        g.dispose();
    }
    private void controls(Graphics2D g) {
        StoryScript.Node node = story.current();
        if (story.answered()) {
            panel(g, 374, 341, 565, 115, YELLOW);
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            drawLines(g, wrap(story.feedback(), g.getFontMetrics(), 530), 389, 367, 22, 4);
            button(g, 759, 473, 178, 39, "Continue →", PINK, this::next);
        } else if (node.kind.equals("CHOICE") || node.kind.equals("QUIZ")) {
            for (int i = 0; i < node.options.size(); i++) {
                int choice = i;
                String label = (i + 1) + ". " + story.format(node.options.get(i).text());
                button(g, 374, 340 + i * 59, 565, 53, label, CREAM, () -> choose(choice));
            }
        } else if (story.acceptsText()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString(checking ? "Checking your answer with the AI tutor…"
                    : "Type your response, then press Enter or Send.", 390, 346);
            button(g, 796, 412, 124, 36, "Send", PINK, this::submit);
            if (node.kind.equals("TYPED")) {
                if (hintsEnabled) button(g, 390, 412, 115, 36, "Hint", CREAM, () -> action.accept("Hint"));
                button(g, 516, 412, 190, 36, "Show model answer", CREAM, () -> { story.revealAnswer(); refresh(); });
            }
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            drawLines(g, wrap(story.feedback(), g.getFontMetrics(), 530), 390, 473, 19, 2);
        } else {
            button(g, 738, 464, 200, 47, story.completed() ? "Return to menu" : "Continue →", PINK, this::next);
            if (story.completed()) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 17));
                drawLines(g, wrap("Your story with " + progress.getSelectedPartner() + " is complete. Your vocabulary stays in this save.",
                        g.getFontMetrics(), 525), 390, 365, 24, 3);
            }
        }
    }
    private void button(Graphics2D g, int x, int y, int w, int h, String text, Color fill, Runnable run) {
        panel(g, x, y, w, h, fill);
        Font font = new Font("SansSerif", Font.PLAIN, h >= 50 ? 16 : 15);
        g.setFont(font);
        List<String> lines = wrap(text, g.getFontMetrics(), w - 24);
        // Long authored choices keep their complete wording by shrinking within the button.
        while (lines.size() > 2 && font.getSize() > 12) {
            font = font.deriveFont((float) font.getSize() - 1); g.setFont(font);
            lines = wrap(text, g.getFontMetrics(), w - 24);
        }
        int lineHeight = g.getFontMetrics().getHeight();
        int baseline = y + (h - lines.size() * lineHeight) / 2 + g.getFontMetrics().getAscent();
        drawLines(g, lines, x + 12, baseline, lineHeight, lines.size());
        hits.add(new Hit(new Rectangle(x, y, w, h), run));
    }
    private void panel(Graphics2D g, int x, int y, int w, int h, Color fill) {
        panel(g, x, y, w, h, fill, INK);
    }

    private void panel(Graphics2D g, int x, int y, int w, int h, Color fill, Color border) {
        g.setColor(border); g.fillRoundRect(x + 3, y + 4, w, h, 17, 17);
        g.setColor(fill); g.fillRoundRect(x, y, w, h, 17, 17);
        g.setColor(border); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x, y, w, h, 17, 17);
    }

    private static Color speakerFillColor(String speaker) {
        if (speaker == null) return CREAM_PANEL;
        String s = speaker.trim().toUpperCase(java.util.Locale.ROOT);
        if (s.equals("MC") || s.equals("YOU")) return PINK_PANEL;
        if (s.contains("JESSA")) return new Color(255, 165, 165, 225);
        if (s.contains("INNA"))  return new Color(170, 205, 255, 225);
        if (s.contains("NIKKA")) return new Color(255, 235, 145, 225);
        return CREAM_PANEL;
    }

    private static Color speakerBorderColor(String speaker) {
        if (speaker == null) return INK;
        String s = speaker.trim().toUpperCase(java.util.Locale.ROOT);
        if (s.equals("MC") || s.equals("YOU")) return new Color(165, 55, 90);
        if (s.contains("JESSA")) return new Color(165, 35, 35);
        if (s.contains("INNA"))  return new Color(30, 80, 165);
        if (s.contains("NIKKA")) return new Color(155, 110, 10);
        return INK;
    }
    private static void center(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y);
    }
    private static void drawLines(Graphics2D g, List<String> lines, int x, int y, int lineHeight, int limit) {
        for (int i = 0; i < Math.min(limit, lines.size()); i++) g.drawString(lines.get(i), x, y + i * lineHeight);
    }
    public static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            String line = "";
            for (String word : paragraph.split("\\s+")) {
                if (!line.isEmpty() && metrics.stringWidth(line + " " + word) > width) { result.add(line); line = ""; }
                while (metrics.stringWidth(word) > width) {
                    int split = word.length() - 1;
                    while (split > 1 && metrics.stringWidth(word.substring(0, split)) > width) split--;
                    if (!line.isEmpty()) { result.add(line); line = ""; }
                    result.add(word.substring(0, split)); word = word.substring(split);
                }
                line += (line.isEmpty() ? "" : " ") + word;
            }
            result.add(line);
        }
        return result;
    }
}
