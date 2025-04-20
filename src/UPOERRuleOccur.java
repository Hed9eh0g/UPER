import java.util.List;


public class UPOERRuleOccur {
    /**
     * the conseEpisode of a Partially-Ordered Episode Rule
     */
    private List<Integer> episode;

    /**
     * the appear time interval of a Partially-Ordered Episode Rule
     */
    private List<RuleIntervalUtility> IntervalUtility;

    /**
     * the utility of a Partially-Ordered Episode Rule
     */
    private int utility;
    /**
     * the remaining utility of a Partially-Ordered Episode Rule
     */
    private int remainingUtility;

    public UPOERRuleOccur(List<Integer> episode, List<RuleIntervalUtility> IntervalUtility, int utility, int remainingUtility) {
        this.episode = episode;
        this.IntervalUtility = IntervalUtility;
        this.utility = utility;
        this.remainingUtility = remainingUtility;
    }

    public void clear(){
        this.episode=null;
        this.IntervalUtility=null;
    }

    public int getUtility() {
        return this.utility;
    }

    public int getRemainingUtility() {
        return this.remainingUtility;
    }

    public List<Integer> getEpisode() {
        return episode;
    }

    public void setEpisode(List<Integer> episode) {
        this.episode = episode;
    }

    public List<RuleIntervalUtility> getIntervals() {
        return IntervalUtility;
    }

    public void setIntervals(List<RuleIntervalUtility> IntervalUtility) {
        this.IntervalUtility = IntervalUtility;
    }

    public String toString() {
        return "episode: " + this.episode.toString() + " " + "intervals: " + this.IntervalUtility.toString();
    }

}
