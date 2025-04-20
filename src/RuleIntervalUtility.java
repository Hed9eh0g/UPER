
public class RuleIntervalUtility {
    /**
     * the antiEpisode's start time of a Partially-Ordered Episode Rule
     */
    public int antiStart;

    /**
     * the antiEpisode's end time of a Partially-Ordered Episode Rule
     */
    public int antiEnd;

    /**
     * the conseEpisode's start time of a Partially-Ordered Episode Rule
     */
    public int conseStart;

    /**
     * the conseEpisode's end time of a Partially-Ordered Episode Rule
     */
    public int conseEnd;

    /**
     * the utility of a Partially-Ordered Episode Rule
     */
    public int utility;

    /**
     * the utility of the X of the rule
     */
    public int antiUtility;

    /**
     * the remaining utility of a Partially-Ordered Episode Rule
     */
    public int remainingUtility;

    public RuleIntervalUtility(int antiStart, int antiEnd, int conseStart, int conseEnd, int utility) {
        this.antiStart = antiStart;
        this.antiEnd = antiEnd;
        this.conseStart = conseStart;
        this.conseEnd = conseEnd;
        this.utility = utility;
    }

    public RuleIntervalUtility(int antiStart, int antiEnd, int conseStart, int conseEnd, int utility, int antiUtility, int remainingUtility) {
        this.antiStart = antiStart;
        this.antiEnd = antiEnd;
        this.conseStart = conseStart;
        this.conseEnd = conseEnd;
        this.utility = utility;
        this.antiUtility = antiUtility;
        this.remainingUtility = remainingUtility;
    }

    public Boolean equal(RuleIntervalUtility other) {
        if (this.antiStart == other.antiEnd && this.antiEnd == other.antiEnd && this.conseStart == other.conseStart && this.conseEnd == other.conseEnd) {
            return true;
        }
        return false;
    }

    public String toString() {
        return this.antiStart + " " + this.antiEnd + " " + this.conseStart + " " + this.conseEnd;
    }
}
