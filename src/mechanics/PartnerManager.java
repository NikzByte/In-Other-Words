package mechanics;

import data.GameProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Manages the three available love interests and the Stage 2 partner selection. */
public class PartnerManager {

    private final GameProgress progress;
    private final Map<String, String> partners = new LinkedHashMap<>();

    /** Uses dialect labels until the team supplies the characters' actual names. */
    public PartnerManager(GameProgress progress) {
        this(progress, defaultPartners());
    }

    /** Maps the three character names to their display descriptions. */
    public PartnerManager(GameProgress progress, Map<String, String> partnerDetails) {
        this.progress = Objects.requireNonNull(progress, "Progress must not be null.");
        if (partnerDetails == null || partnerDetails.size() != 3) {
            throw new IllegalArgumentException("Provide exactly three love interests.");
        }
        for (Map.Entry<String, String> partner : partnerDetails.entrySet()) {
            String name = partner.getKey();
            String details = partner.getValue();
            if (name == null || name.isBlank() || details == null || details.isBlank()) {
                throw new IllegalArgumentException("Partner names and descriptions must not be blank.");
            }
            if (findPartner(name) != null) {
                throw new IllegalArgumentException("Partner names must be distinct.");
            }
            partners.put(name.strip(), details.strip());
        }
        if (!progress.getSelectedPartner().isEmpty() && findPartner(progress.getSelectedPartner()) == null) {
            throw new IllegalArgumentException("The saved partner is not in the supplied roster.");
        }
    }

    /** A chosen partner stays fixed until resetPartnerSelection() is explicitly called. */
    public void selectPartner(String partnerName) {
        String canonicalName = requirePartner(partnerName);
        if (isPartnerSelected()) {
            if (normalize(progress.getSelectedPartner()).equals(normalize(canonicalName))) {
                return;
            }
            throw new IllegalStateException("A partner has already been selected.");
        }
        if (progress.getCurrentStage() != 2) {
            throw new IllegalStateException("Choose a project partner during Stage 2.");
        }
        progress.setSelectedPartner(canonicalName);
    }

    public String getSelectedPartner() {
        return progress.getSelectedPartner();
    }

    public boolean isPartnerSelected() {
        return !progress.getSelectedPartner().isEmpty();
    }

    public String getPartnerDetails(String partnerName) {
        return partners.get(requirePartner(partnerName));
    }

    public ArrayList<String> getAvailablePartners() {
        return new ArrayList<>(partners.keySet());
    }

    /** Clears selection only. Use GameProgress.resetProgress() for a complete new game. */
    public void resetPartnerSelection() {
        progress.setSelectedPartner("");
    }

    private String requirePartner(String name) {
        String found = findPartner(name);
        if (found == null) {
            throw new IllegalArgumentException("Unknown partner: " + name);
        }
        return found;
    }

    private String findPartner(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (String existing : partners.keySet()) {
            if (normalize(existing).equals(normalize(name))) {
                return existing;
            }
        }
        return null;
    }

    private String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> defaultPartners() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("Bisaya", "Bisaya-speaking love interest");
        defaults.put("Ilokano", "Ilokano-speaking love interest");
        defaults.put("Bicolano", "Bicolano-speaking love interest");
        return defaults;
    }
}
