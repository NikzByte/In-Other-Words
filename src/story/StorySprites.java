package story;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Loads and caches character dialogue sprites mapped to story node IDs,
 * and renders single or multi-character portraits with a subtle pop-in animation.
 */
final class StorySprites {

    private static final int CACHED_W = 450;
    private static final int CACHED_H = 600;

    private final Map<String, List<String>> nodeSprites = new HashMap<>();
    private final Map<String, BufferedImage> imageCache = new HashMap<>();

    StorySprites() {
        loadMappings();
    }

    private void loadMappings() {
        try (BufferedReader reader = openMappings()) {
            if (reader == null) return;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", 2);
                if (parts.length != 2) continue;
                String nodeId = parts[0].trim();
                List<String> paths = new ArrayList<>();
                for (String raw : parts[1].split("\\|")) {
                    String p = raw.trim();
                    if (!p.isEmpty()) paths.add(p);
                }
                if (!paths.isEmpty()) {
                    nodeSprites.put(nodeId, List.copyOf(paths));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static BufferedReader openMappings() throws IOException {
        InputStream stream = StorySprites.class.getResourceAsStream("/resources/story/sprites.tsv");
        if (stream != null) return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        Path path = Path.of("src", "resources", "story", "sprites.tsv");
        if (Files.exists(path)) return Files.newBufferedReader(path, StandardCharsets.UTF_8);
        return null;
    }

    private static final java.util.Set<String> NO_SPRITE_SPEAKERS = java.util.Set.of(
            "", "narration", "system", "epilogue", "ending", "study break", "club", "library");

    List<String> spritesFor(StoryScript.Node node) {
        if (node == null) return List.of();
        String speakerKey = node.speaker == null ? "" : node.speaker.trim().toLowerCase(Locale.ROOT);
        if (NO_SPRITE_SPEAKERS.contains(speakerKey)) return List.of();
        List<String> mapped = nodeSprites.get(node.id);
        if (mapped != null && !mapped.isEmpty()
                && !(mapped.size() == 1 && mapped.get(0).equals("DIALOGUE/DIALOGUE.png"))) {
            return mapped;
        }
        return defaultForSpeaker(node.speaker);
    }

    private static final java.util.Set<String> FEMALE_SPEAKERS = java.util.Set.of(
            "lola ising", "lola pilar", "manang lita", "nanang", "yani", "librarian");

    private static List<String> defaultForSpeaker(String speaker) {
        if (speaker == null) return List.of("PLACEHOLDER/MALE_SILHOUETTE.png");
        String s = speaker.trim().toLowerCase(Locale.ROOT);
        if (s.equals("jessa")) return List.of("JESSA/JESSA_IDLE.png");
        if (s.equals("inna")) return List.of("INNA/INNA_IDLE.png");
        if (s.equals("nikka")) return List.of("NIKKA/NIKKA_IDLE.png");
        if (s.equals("mc") || s.equals("you")) return List.of("MC/MC_IDLE.png");
        if (s.contains("jessa") && s.contains("nikka")) {
            return List.of("JESSA/JESSA_IDLE-talk.png", "NIKKA/NIKKA_POUTBLUSH.png");
        }
        if (s.equals("all")) {
            return List.of("JESSA/JESSA_HAPPY.png", "INNA/INNA_HAPPY.png", "NIKKA/NIKKA_HAPPYBLUSH.png");
        }
        if (FEMALE_SPEAKERS.contains(s)) return List.of("PLACEHOLDER/FEMALE_SILHOUETTE.png");
        // All other speakers (male characters, narration, system, etc.) use the male silhouette
        return List.of("PLACEHOLDER/MALE_SILHOUETTE.png");
    }

    private BufferedImage loadSprite(String relativePath) {
        return imageCache.computeIfAbsent(relativePath, key -> {
            BufferedImage raw = readRawImage(key);
            if (raw == null && key.equals("DIALOGUE/DIALOGUE.png")) {
                raw = readRawImage("MC/DIALOGUE.png");
            }
            if (raw == null) return null;
            BufferedImage scaled = new BufferedImage(CACHED_W, CACHED_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(raw, 0, 0, CACHED_W, CACHED_H, null);
            g2.dispose();
            return scaled;
        });
    }

    private static BufferedImage readRawImage(String relativePath) {
        String resourcePath = "/assets/sprites/" + relativePath;
        try (InputStream stream = StorySprites.class.getResourceAsStream(resourcePath)) {
            if (stream != null) return ImageIO.read(stream);
        } catch (IOException ignored) {
        }
        Path filePath = Path.of("src", "assets", "sprites", relativePath);
        if (Files.exists(filePath)) {
            try {
                return ImageIO.read(filePath.toFile());
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /**
     * Draws the node's character sprite(s) inside the portrait box (x, y, w, h)
     * with a subtle pop-in animation driven by {@code animProgress} in [0, 1].
     */
    void drawPortraits(Graphics2D g, StoryScript.Node node, int x, int y, int w, int h, double animProgress) {
        List<String> paths = spritesFor(node);
        List<BufferedImage> images = new ArrayList<>();
        for (String p : paths) {
            BufferedImage img = loadSprite(p);
            if (img != null) images.add(img);
        }
        if (images.isEmpty()) return;

        double t = Math.max(0.0, Math.min(1.0, animProgress));
        double ease = 1.0 - Math.pow(1.0 - t, 3);
        double pop = t < 0.7 ? (0.93 + 0.09 * (t / 0.7)) : (1.02 - 0.02 * ((t - 0.7) / 0.3));
        int dy = (int) Math.round((1.0 - ease) * 12);
        float alpha = (float) Math.min(1.0, 0.4 + 0.6 * ease);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        Composite prevComp = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int count = images.size();
        if (count == 1) {
            int drawH = h;
            int drawW = drawH * 3 / 4;
            int drawX = x + (w - drawW) / 2;
            int drawY = y + h - drawH;
            drawScaledPop(g2, images.get(0), drawX, drawY, drawW, drawH, pop, dy);
        } else if (count == 2) {
            int drawH = (int) (h * 0.94);
            int drawW = drawH * 3 / 4;
            int overlap = 145;
            int totalW = drawW * 2 - overlap;
            int startX = Math.max(4, x + (w - totalW) / 2);
            int drawY = y + h - drawH;
            drawScaledPop(g2, images.get(0), startX, drawY, drawW, drawH, pop, dy);
            drawScaledPop(g2, images.get(1), startX + drawW - overlap, drawY, drawW, drawH, pop, dy);
        } else {
            int drawH = (int) (h * 0.85);
            int drawW = drawH * 3 / 4;
            int overlap = 163;
            int totalW = drawW * count - overlap * (count - 1);
            int startX = Math.max(4, x + (w + 45 - totalW) / 2);
            int drawY = y + h - drawH;
            for (int i = 0; i < count; i++) {
                drawScaledPop(g2, images.get(i), startX + i * (drawW - overlap), drawY, drawW, drawH, pop, dy);
            }
        }

        g2.setComposite(prevComp);
        g2.dispose();
    }

    private static void drawScaledPop(Graphics2D g, BufferedImage img, int x, int y, int w, int h,
                                      double pop, int dy) {
        int scaledW = (int) Math.round(w * pop);
        int scaledH = (int) Math.round(h * pop);
        int drawX = x + (w - scaledW) / 2;
        int drawY = y + (h - scaledH) + dy;
        g.drawImage(img, drawX, drawY, scaledW, scaledH, null);
    }
}
