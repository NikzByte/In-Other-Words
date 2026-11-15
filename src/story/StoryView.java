package story;

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
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/** The existing pink/cream dialogue style, with real story text and paged choices. */
public final class StoryView extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color INK = new Color(0x2B193D), CREAM = new Color(0xF6E9C7),
            PINK = new Color(0xFFB2C2), YELLOW = new Color(0xF2DEA5);
    private final StoryManager story;
    private final GameProgress progress;
    private final Consumer<String> action;
    private final JTextField reply = new JTextField();
    private final List<Hit> hits = new ArrayList<>();
    private record Hit(Rectangle box, Runnable action) {}
    private String displayedNode = "";
    private int page;
    private boolean hintsEnabled = true;

    public void setHintsEnabled(boolean enabled) { hintsEnabled = enabled; }
    public void resetPresentation() { displayedNode = ""; page = 0; reply.setText(""); }

    public StoryView(StoryManager story, GameProgress progress, Consumer<String> action) {
        this.story = story; this.progress = progress; this.action = action;
        setLayout(null); setFocusable(true); setBackground(CREAM);
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
                for (Hit hit : List.copyOf(hits)) if (hit.box.contains(x, y)) {
                    hit.action.run(); return;
                }
                if (!reply.isVisible()) requestFocusInWindow();
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
    }
    public void refresh() {
        if (!story.started()) { reply.setVisible(false); return; }
        if (!displayedNode.equals(story.current().id)) {
            displayedNode = story.current().id; page = 0; reply.setText("");
        }
        int pages = textPages().size();
        page = Math.max(0, Math.min(page, pages - 1));
        reply.setVisible(isVisible() && story.acceptsText() && page == pages - 1);
        doLayout();
        if (isShowing()) {
            if (reply.isVisible()) reply.requestFocusInWindow();
            else requestFocusInWindow();
        }
        repaint();
    }
    private List<List<String>> textPages() {
        List<String> lines = wrap(story.format(story.current().text),
                getFontMetrics(new Font("SansSerif", Font.PLAIN, 19)), 524);
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += 6) pages.add(lines.subList(i, Math.min(i + 6, lines.size())));
        if (pages.isEmpty()) pages.add(List.of(""));
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
        if (!reply.isVisible()) return;
        story.submit(reply.getText()); refresh();
        if (reply.isVisible()) reply.selectAll();
    }
    private void next() {
        if (page + 1 < textPages().size()) page++;
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
        g.setColor(CREAM); g.fillRect(0, 0, 960, 576);
        hits.clear();
        panel(g, 18, 12, 660, 65, PINK);
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        drawLines(g, wrap(story.scene().title(), g.getFontMetrics(), 625), 31, 38, 22, 2);
        panel(g, 696, 12, 246, 65, CREAM);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("Chapter " + progress.getCurrentStage(), 710, 37);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString(progress.getSelectedPartner().isEmpty() ? "Getting to know the club"
                : progress.getSelectedPartner() + "  •  Affection " + progress.getAffectionScore(), 710, 61);

        panel(g, 20, 95, 334, 337, CREAM);
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{7,7}, 0));
        g.drawRoundRect(30, 105, 314, 317, 14, 14);
        g.setStroke(new BasicStroke(2));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        center(g, "[CHARACTER SPRITE]", 187, 247);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        String speaker = story.current().speaker;
        if (speaker.equals("MC") || speaker.equals("You")) speaker = story.value("preferred_address").isBlank()
                ? "MC" : story.value("preferred_address");
        center(g, speaker, 187, 276);
        panel(g, 20, 446, 334, 65, PINK);
        g.setFont(new Font("SansSerif", Font.BOLD, 19));
        drawLines(g, wrap(speaker, g.getFontMetrics(), 306), 34, 473, 23, 2);

        panel(g, 373, 95, 568, 231, CREAM);
        g.setFont(new Font("SansSerif", Font.BOLD, 16)); g.drawString(story.current().speaker, 389, 122);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        List<List<String>> pages = textPages();
        drawLines(g, pages.get(Math.min(page, pages.size()-1)), 390, 153, 25, 6);
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
            g.drawString("Type your response, then press Enter or Send.", 390, 346);
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
        g.setColor(INK); g.fillRoundRect(x + 3, y + 4, w, h, 17, 17);
        g.setColor(fill); g.fillRoundRect(x, y, w, h, 17, 17);
        g.setColor(INK); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x, y, w, h, 17, 17);
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
