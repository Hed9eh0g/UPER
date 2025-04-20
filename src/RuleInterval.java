
public class RuleInterval {
	/** the antiEpisode's start time of a Partially-Ordered Episode Rule*/
	public int antiStart;
	
	/** the antiEpisode's end time of a Partially-Ordered Episode Rule*/
	public int antiEnd;
	
	/** the conseEpisode's start time of a Partially-Ordered Episode Rule*/
	public int conseStart;
	
	/** the conseEpisode's end time of a Partially-Ordered Episode Rule*/
	public int conseEnd;
	
	public RuleInterval(int antiStart, int antiEnd, int conseStart, int conseEnd) {
		this.antiStart = antiStart;
		this.antiEnd = antiEnd;
		this.conseStart = conseStart;
		this.conseEnd = conseEnd;
	}
	
	public Boolean equal(RuleInterval other) {
		if (this.antiStart == other.antiEnd && this.antiEnd == other.antiEnd && this.conseStart == other.conseStart && this.conseEnd == other.conseEnd) {
			return true;
		}
		return false;
	}
	
	public String toString() {
		return this.antiStart + " " + this.antiEnd + " " + this.conseStart + " " + this.conseEnd;
	}
}
