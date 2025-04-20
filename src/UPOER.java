
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;


public class UPOER {
    /**
     * the antiEpisode of a Partially-Ordered Episode Rule
     */
    private List<Integer> antiEpisode;

    /**
     * the conseEpisode of a Partially-Ordered Episode Rule
     */
    private List<Integer> conseEpisode;

    /**
     * the appear time interval of a Partially-Ordered Episode Rule
     */
    private List<RuleInterval> intervals;

    /**
     * the antiEpisode appear time of a Partially-Ordered Episode Rule
     */
    private int antiCount;

    /**
     * the confident of a Partially-Ordered Episode Rule
     */
    private int confidence;

    /**
     * the utility of a Partially-Ordered Episode Rule
     */
    private int utility;

    /**
     * Object to format double numbers in decimal format
     */
    private DecimalFormat formatter = new DecimalFormat("#.####");

    public UPOER(List<Integer> antiEpisode,
                 List<Integer> conseEpisode, List<RuleInterval> intervals,
                 int antiCount, int confident, int utility) {
        this.setAntiEpisode(antiEpisode);
        this.setConseEpisode(conseEpisode);
        this.setIntervals(intervals);
        this.setAntiCount(antiCount);
        this.setConfident(confident);
        this.setUtility(utility);
    }


    public int match(List<int[]> antecedent) {
        int i = 0;
        List<Integer> intersection = new ArrayList<Integer>();
        intersection.addAll(antiEpisode);
        List<Integer> otherList = new ArrayList<Integer>();
        for (int j = 0; j < antecedent.size(); ++j) {
            int[] nowItemSet = antecedent.get(j);
            for (int k = 0; k < nowItemSet.length; ++k) {
                otherList.add(nowItemSet[k]);
            }
        }
        intersection.retainAll(otherList);
        if (intersection.size() == this.antiEpisode.size()) {
            return this.antiEpisode.size();
        } else {
            return 0;
        }
        //	return 0;
    }

    @Override
    public String toString() {
        String episodeRule = "";
        List<Integer> antiEpisode = this.getAntiEpisode();
        List<Integer> conseEpisode = this.getConseEpisode();
        for (Integer anti : antiEpisode) {
            episodeRule += anti + " ";
        }
        episodeRule += "==> ";
        for (Integer conse : conseEpisode) {
            episodeRule += conse + " ";
        }
        return "rule: " + episodeRule + "#SUP: " + this.getRuleCount() + " #CONF: "
                + formatter.format(this.getRuleCount() / (double) this.getAntiCount());
    }

    /**
     * Compare this pattern with another pattern
     *
     * @param o another pattern
     * @return 0 if equal, -1 if smaller, 1 if larger (in terms of support).
     */
    public int compareTo(UPOER o) {
        if (o == this) {
            return 0;
        }
        long compare = this.antiCount - o.antiCount;
        if (compare > 0) {
            return 1;
        }
        if (compare < 0) {
            return -1;
        }
        return 0;
    }

    public List<Integer> getAntiEpisode() {
        return antiEpisode;
    }

    public void setAntiEpisode(List<Integer> antiEpisode) {
        this.antiEpisode = antiEpisode;
    }

    public List<Integer> getConseEpisode() {
        return conseEpisode;
    }

    public void setConseEpisode(List<Integer> conseEpisode) {
        this.conseEpisode = conseEpisode;
    }

    public List<RuleInterval> getIntervals() {
        return intervals;
    }

    public void setIntervals(List<RuleInterval> intervals) {
        this.intervals = intervals;
    }

    public int getRuleCount() {
        return confidence;
    }

    public void setConfident(int confident) {
        this.confidence = confident;
    }

    public int getAntiCount() {
        return antiCount;
    }

    public void setAntiCount(int antiCount) {
        this.antiCount = antiCount;
    }

    public void setUtility(int utility) {
        this.utility = utility;
    }

    public int getUtility() {
        return utility;
    }
}
