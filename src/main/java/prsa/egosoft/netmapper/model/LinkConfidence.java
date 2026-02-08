package prsa.egosoft.netmapper.model;

/**
 * Representa la confianza de un enlace con degradación temporal.
 */
public class LinkConfidence {

    public enum DecayProfile {
        HALF_LIFE_24H,
        HALF_LIFE_1H,
        NO_DECAY
    }

    private float score; // 0.0 - 1.0
    private long lastVerified;
    private DecayProfile decayProfile;

    public LinkConfidence() {
        this.score = 1.0f;
        this.lastVerified = System.currentTimeMillis();
        this.decayProfile = DecayProfile.HALF_LIFE_24H;
    }

    public LinkConfidence(float score, DecayProfile decayProfile) {
        this.score = score;
        this.lastVerified = System.currentTimeMillis();
        this.decayProfile = decayProfile;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public long getLastVerified() {
        return lastVerified;
    }

    public void setLastVerified(long lastVerified) {
        this.lastVerified = lastVerified;
    }

    public DecayProfile getDecayProfile() {
        return decayProfile;
    }

    public void setDecayProfile(DecayProfile decayProfile) {
        this.decayProfile = decayProfile;
    }

    /**
     * Calculates the current confidence score based on time decay.
     * 
     * @return decayed score
     */
    public float getCurrentScore() {
        long timeDiff = System.currentTimeMillis() - lastVerified;
        long halfLifeMillis;

        switch (decayProfile) {
            case HALF_LIFE_1H:
                halfLifeMillis = 3600 * 1000;
                break;
            case HALF_LIFE_24H:
                halfLifeMillis = 24 * 3600 * 1000;
                break;
            case NO_DECAY:
            default:
                return score;
        }

        double decayFactor = Math.pow(0.5, (double) timeDiff / halfLifeMillis);
        return (float) (score * decayFactor);
    }
}
