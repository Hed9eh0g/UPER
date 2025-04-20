import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

public class AlgoUPER {
    /**
     * the input file
     */
    private String inputFile;

    /**
     * the output file
     */
    private String outputFile;

    /**
     * this buffered writer is used to write the output file
     */
    BufferedWriter writer = null;

    /**
     * start time of the latest execution
     */
    private long startTimestamp;

    private int maxLineNumber = 0;

    /**
     * end time of the latest execution
     */
    private long endTimestamp;

    /**
     * the runtime of program run
     */
    private long deltaTimestamp;

    /**
     * Whether to use a more compact upper bound
     */
    private boolean tighterUpperBound;

    private long CandidateNum=0;

    /**
     * Object to format double numbers in decimal format
     */
    DecimalFormat formatter = new DecimalFormat("#.####");

    /**
     * Maximum memory used during the last execution
     */
    private double maxMemory;

    /**
     * the UPOER count
     */
    private long HUPOERCount = 0;

    /**
     * Only for testing, if true, the matrixs will be outputed
     */
    private boolean matrix;


    /**
     * the path to output the matrix if needed for debugging
     */
    private String outputMatrixPath = "showMatrix.txt";

    /**
     * the utility threshold
     */
    private double minUtility;

    /**
     * the support threshold
     */
    private int minSupport;

    /**
     * the confidence threshold
     */
    private double minConfidence;

    /**
     * the minimum utility threshold : a ratio
     */
    private double minUtilityRatio;

    /* The XSpan */
    private int XSpan;

    /* The YSpan */
    private int YSpan;

    /* The XYSpan */
    private int XYSpan;

    /**
     * The maximum time duration threshold
     **/
    private int maxSpan;

    /**
     * Map: key: item value: another item that followed the first item + support
     * (could be replaced with a triangular matrix...)
     */
    private Map<Integer, Map<Integer, Integer>> cooMapAfter = null;

    /**
     * Total utility in the database
     */
    private long sequenceUtility = 0;

    /**
     * The complex sequence that contains simultaneous items
     */
    private ComplexSequence complexSequence;

    /**
     * The 1-candidates that their TWU larger than minUtility key: 1-candidates
     * value: 1. the minimal occurrrences list of 1-candidates we only use one
     * figure to represent the occurrences for the start timepoint is same as end
     * timepoint 2. the utility vaue in the sequence
     */
    private Map<Integer, SingleMoListUtilityList> mapSingleXCandidatesWithMoListAndUtilityList;

    /**
     * The candidates of X which are generated from the 1-candidates
     */
    private List<EpisodeMoListUtilityList> XCandidatesWithMoListAndUtilityList;
    /**
     * The candidates of Y
     */
    private List<EpisodeMoListUtilityList> YCandidatesWithMoListAndUtilityList;

    /**
     * The High Utility Partially-Ordered Episode Rules
     */
    private List<UPOER> HUPOERs = new ArrayList<>();

    private Map<Integer, List<PositionsAndUtility>> mapFres = new HashMap<>();

    /**
     * The largest TID in the comoplex sequence
     */
    private int largestTID;

    /**
     * Constructor
     */
    public AlgoUPER() {
        // empty
    }

    public void runAlgorithm(String inputFile, String outputFile, int minSupport, double minConfidence, double minUtilityRatio, int XSpan, int YSpan, int XYSpan, int maxLineNumber, boolean matrix, boolean tighterUpperBound) throws IOException {

        this.outputFile = outputFile;
        this.minSupport = minSupport;
        this.minConfidence = minConfidence;
        this.XSpan = XSpan;
        this.YSpan = YSpan;
        this.XYSpan = XYSpan;
        this.maxSpan = XSpan + YSpan + XYSpan - 3;
        this.minUtilityRatio = minUtilityRatio;
        this.maxLineNumber = maxLineNumber;
        this.matrix = matrix;
        this.tighterUpperBound = tighterUpperBound;

        // we prepare the object for writing the output file
        writer = new BufferedWriter(new FileWriter(this.outputFile));

        MemoryLogger.getInstance().reset();
        this.startTimestamp = System.currentTimeMillis();

        this.complexSequence = new ComplexSequence();

        // init the singleCandidates and its minimal occurrences and total utilities
        this.mapSingleXCandidatesWithMoListAndUtilityList = new HashMap<>();

        /**
         * scan the dataset to calculate the support and WEU for each event
         */
        scanDatabaseAndCalcSingleEpisodes(inputFile);

        System.out.println("Event number before WEU pruning: " + this.mapSingleXCandidatesWithMoListAndUtilityList.size());

        /**
         * WEUP strategy and RESP strategy
         */
        this.complexSequence.pruneSingleEventsByUpperBound(this.maxSpan, this.minUtility, this.minSupport, this.minConfidence, this.mapSingleXCandidatesWithMoListAndUtilityList);

        System.out.println("Event number after WEU pruning: " + this.mapSingleXCandidatesWithMoListAndUtilityList.size());

        System.out.println("the time piont of S_Y: " + this.complexSequence.mapYItemEET.size());

        /**
         * build the REUCS
         */
        buildCoocUtilityMatrix();

        System.out.println("mining X begin");

        /**
         * mine all promising X event sets
         */
        miningXEventSet();

        System.out.println("mining X complete");

        /**
         * mine high-utility partially-ordered episode rules
         */
        miningHUPOER();

        this.endTimestamp = System.currentTimeMillis();
        this.deltaTimestamp = this.endTimestamp - this.startTimestamp;

        // close the file
        writer.close();
        MemoryLogger.getInstance().checkMemory();
        this.maxMemory = MemoryLogger.getInstance().getMaxMemory();

    }

    /**
     * scan the database once to find the high utility 1-episosdes and calculate
     * their EWUs (or tighter upper-bound named ERUs 'Episode Remaining Utility')
     * and their minimal occurrences
     */
    private void scanDatabaseAndCalcSingleEpisodes(String inputFile) throws IOException {
        // read file
        @SuppressWarnings("resource")
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        String line;

        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            if (lineNumber >= this.maxLineNumber) {
                break;
            }

            lineNumber++;

            // if the line is a comment, is empty or is a
            // kind of metadata
            if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
                continue;
            }

            // line form: events:totalUtility:eventsUtility
            String[] lineSplited = line.split(":");
            String[] events = lineSplited[0].split(" ");
            int totalUtility = Integer.parseInt(lineSplited[1]);
            String[] eventsUtility = lineSplited[2].split(" ");

            for (int i = 0; i < events.length; i++) {
                // convert the event to Integer
                Integer event = Integer.parseInt(events[i]);

                // convert the utility of event to Integer
                Integer eventUtility = Integer.parseInt(eventsUtility[i]);

                // add to the complexSequence
                this.complexSequence.add(lineNumber, event, eventUtility);

                // if map does not contains this event, create the key of the map
                if (!this.mapSingleXCandidatesWithMoListAndUtilityList.containsKey(event)) {
                    this.mapSingleXCandidatesWithMoListAndUtilityList.put(event, new SingleMoListUtilityList());
                }

                // save current timepoint and utility of the event to the map
                this.mapSingleXCandidatesWithMoListAndUtilityList.get(event).add(lineNumber, eventUtility);

            }
            this.sequenceUtility += totalUtility;
            this.complexSequence.setTotalUtility(lineNumber, totalUtility);
        }
        // set the largest TID to sequence
        this.largestTID = lineNumber;
        this.complexSequence.setLargestTID(this.largestTID);

        /* get the absolute minimal utility */
        this.minUtility = this.sequenceUtility * this.minUtilityRatio;

    }

    /**
     * Build the coocurrence utility matrix
     *
     * @throws IOException if error reading/writing to a file
     */
    private void buildCoocUtilityMatrix() throws IOException {

        this.cooMapAfter = new HashMap<>();

        for (int TID = 1; TID <= this.largestTID; TID++) {

            List<ComplexSequence.EventUtility> pairs = this.complexSequence.getEventSetAndItsUtilityByTID(TID);

            for (ComplexSequence.EventUtility pair : pairs) {
                int itemI = pair.event;

                Set<Integer> alreadyProcessedAfter = new HashSet<>();
                for (int TIDAfter = TID + 1; TIDAfter <= TID + maxSpan; TIDAfter++) {
                    List<ComplexSequence.EventUtility> pairsAfter = this.complexSequence.getEventSetAndItsUtilityByTID(TIDAfter);
                    for (ComplexSequence.EventUtility pairAfter : pairsAfter) {
                        int itemJ = pairAfter.event;

                        if (alreadyProcessedAfter.contains(itemJ)) {
                            // if itemJ has been processed, pass it
                            continue;
                        }

                        // calculate the WEU of the occurrence
                        int utilityAfter = this.complexSequence.getTotalUtilityOfDuration(TIDAfter - maxSpan,
                                TID + maxSpan);

                        Map<Integer, Integer> map = cooMapAfter.get(itemI);
                        if (map == null) {
                            map = new HashMap<>();
                            cooMapAfter.put(itemI, map);
                        }

                        // the WEU of the event is the sum of its occurrences' WEU
                        Integer utility = map.get(itemJ);
                        if (utility == null) {
                            map.put(itemJ, utilityAfter);
                        } else {
                            map.put(itemJ, utility + utilityAfter);
                        }
                        alreadyProcessedAfter.add(itemJ);
                    }
                }
            }
        }

    }

    /**
     * Find all XEventSet that maybe the anti episode of a Partially-Ordered Episode
     * Rule
     */
    private void miningXEventSet() {
        this.XCandidatesWithMoListAndUtilityList = new ArrayList<>();
        for (Map.Entry<Integer, SingleMoListUtilityList> entry : this.mapSingleXCandidatesWithMoListAndUtilityList.entrySet()) {
            Integer SingleXEvent = entry.getKey();
            List<Integer> XEventList = new ArrayList<>();
            XEventList.add(SingleXEvent);
            List<PositionsAndUtility> SingleXPositionUtilityList = new ArrayList<>();

            List<Integer> SingleXEventPositionList = entry.getValue().getMoList();
            List<Integer> SingleXEventUtilityList = entry.getValue().getUtilityList();
            for (int i = 0; i < SingleXEventPositionList.size(); i++) {
                PositionsAndUtility SingleXPositionUtility = new PositionsAndUtility(SingleXEventPositionList.get(i), SingleXEventPositionList.get(i), SingleXEventUtilityList.get(i));
                SingleXPositionUtilityList.add(SingleXPositionUtility);
            }
            this.XCandidatesWithMoListAndUtilityList.add(new EpisodeMoListUtilityList(XEventList, SingleXPositionUtilityList));
        }
        this.mapSingleXCandidatesWithMoListAndUtilityList.clear();

        try {
            int index = 0;
            int maxIndex = XCandidatesWithMoListAndUtilityList.size();

            while (index < maxIndex) {
                this.mapFres.clear();
                EpisodeMoListUtilityList EpisodeMo = XCandidatesWithMoListAndUtilityList.get(index);
                index++;

                List<Integer> episode = EpisodeMo.getEpisodeList();
                Integer compareKey = episode.get(episode.size() - 1);

                List<PositionsAndUtility> moPositionAndUtilityList = EpisodeMo.getPositionAndUtilityList();
                PositionsAndUtility positionsAndUtility;

                for (int i = 0; i < moPositionAndUtilityList.size(); i++) {
                    int start = moPositionAndUtilityList.get(i).start;
                    int end = moPositionAndUtilityList.get(i).end;
                    int moUtility = moPositionAndUtilityList.get(i).utility;
                    // Search the time intervals [interval.end - XSpan + 1, interval.start)
                    for (int j = end - this.XSpan + 1; j < start; j++) {
                        if (this.complexSequence.getEventSetAndItsUtilityByTID(j).isEmpty()) {
                            continue;
                        }
                        List<ComplexSequence.EventUtility> pairs = this.complexSequence.getEventSetAndItsUtilityByTID(j);
                        for (int k = 0; k < pairs.size(); k++) {
                            int event = pairs.get(k).event;
                            int utility = pairs.get(k).utility;
                            if (event > compareKey) {
                                if (this.mapFres.containsKey(event)) {
                                    positionsAndUtility = new PositionsAndUtility(j, end, moUtility + utility);
                                    this.mapFres.get(event).add(positionsAndUtility);
                                } else {
                                    List<PositionsAndUtility> positionsAndUtilityList = new ArrayList<>();
                                    positionsAndUtility = new PositionsAndUtility(j, end, moUtility + utility);
                                    positionsAndUtilityList.add(positionsAndUtility);
                                    this.mapFres.put(event, positionsAndUtilityList);
                                }
                            }
                        }
                    }
                    // Search the time intervals [interval.end + 1, interval.start + XSpan)
                    for (int j = end + 1; j < start + XSpan; j++) {
                        if (this.complexSequence.getEventSetAndItsUtilityByTID(j).isEmpty()) {
                            continue;
                        }
                        List<ComplexSequence.EventUtility> pairs = this.complexSequence.getEventSetAndItsUtilityByTID(j);
                        for (int k = 0; k < pairs.size(); k++) {
                            int event = pairs.get(k).event;
                            int utility = pairs.get(k).utility;
                            if (event > compareKey) {
                                if (this.mapFres.containsKey(event)) {
                                    positionsAndUtility = new PositionsAndUtility(start, j, moUtility + utility);
                                    this.mapFres.get(event).add(positionsAndUtility);
                                } else {
                                    List<PositionsAndUtility> positionsAndUtilityList = new ArrayList<>();
                                    positionsAndUtility = new PositionsAndUtility(start, j, moUtility + utility);
                                    positionsAndUtilityList.add(positionsAndUtility);
                                    this.mapFres.put(event, positionsAndUtilityList);
                                }
                            }
                        }
                    }
                    // Search the time intervals [intStart, intEnd]
                    for (int j = start; j <= end; j++) {
                        if (this.complexSequence.getEventSetAndItsUtilityByTID(j).isEmpty()) {
                            continue;
                        }
                        List<ComplexSequence.EventUtility> pairs = this.complexSequence.getEventSetAndItsUtilityByTID(j);
                        for (int k = 0; k < pairs.size(); k++) {
                            int event = pairs.get(k).event;
                            int utility = pairs.get(k).utility;
                            if (event > compareKey) {
                                if (this.mapFres.containsKey(event)) {
                                    positionsAndUtility = new PositionsAndUtility(start, end, moUtility + utility);
                                    this.mapFres.get(event).add(positionsAndUtility);
                                } else {
                                    List<PositionsAndUtility> positionsAndUtilityList = new ArrayList<>();
                                    positionsAndUtility = new PositionsAndUtility(start, end, moUtility + utility);
                                    positionsAndUtilityList.add(positionsAndUtility);
                                    this.mapFres.put(event, positionsAndUtilityList);
                                }
                            }
                        }
                    }
                }
                // Add each pair of fresMap such that |value| ≥ minsup into XFreAppear;
                for (Map.Entry<Integer, List<PositionsAndUtility>> entry : this.mapFres.entrySet()) {
                    Integer key = entry.getKey();
                    List<PositionsAndUtility> value = entry.getValue();
                    value.sort(new myComparator());
                    List<PositionsAndUtility> newValue = new ArrayList<>();
                    for (int i = 0; i < value.size(); i++) {
                        if (i == 0 || !(value.get(i).start == newValue.get(newValue.size() - 1).start && value.get(i).end == newValue.get(newValue.size() - 1).end)) {
                            newValue.add(value.get(i));
                        }
                    }
                    if (newValue.size() >= this.minSupport) {
                        List<Integer> newKey = new ArrayList<Integer>(episode);
                        newKey.add(key);
                        EpisodeMoListUtilityList episodeMoListUtilityList = new EpisodeMoListUtilityList(newKey, value);
                        this.XCandidatesWithMoListAndUtilityList.add(episodeMoListUtilityList);
                    }
                }
                maxIndex = this.XCandidatesWithMoListAndUtilityList.size();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }

    /**
     * a Comparator to sort interval by its end.
     */
    public class myComparator implements Comparator<PositionsAndUtility> {
        // a[0]: start,  a[1]:end,  a[2]:utility
        public int compare(PositionsAndUtility a, PositionsAndUtility b) {
            if (a.end == b.end) {
                return a.start - b.start;
            }
            return a.end - b.end;
        }
    }

    public void miningHUPOER() {
        Map<Integer, List<RuleIntervalUtility>> conseRecodeMap = new HashMap<>();
        for (EpisodeMoListUtilityList antiEpisodeUtilityList : this.XCandidatesWithMoListAndUtilityList) {
            List<Integer> antiEpisode = antiEpisodeUtilityList.getEpisodeList();
            List<PositionsAndUtility> PositionUtilityList = antiEpisodeUtilityList.getPositionAndUtilityList();
            int antiStart = -1;
            int antiCount = 0;
            for (PositionsAndUtility PositionUtility : PositionUtilityList) {
                if (PositionUtility.start <= antiStart) {
                    continue;
                }
                antiCount++;
                antiStart = PositionUtility.end;
            }
            if (antiCount < this.minSupport) {
                continue;
            }
            conseRecodeMap.clear();

            for (PositionsAndUtility PositionUtility : PositionUtilityList) {
                int antiEpisodeStart = PositionUtility.start;
                int antiEpisodeEnd = PositionUtility.end;
                int antiEpisodeUtility = PositionUtility.utility;
                for (int i = antiEpisodeEnd + 1; i < antiEpisodeEnd + this.YSpan + this.XYSpan; i++) {
                    if (this.complexSequence.getYEventSetAndItsUtilityByTID(i).isEmpty()) {
                        continue;
                    }
                    List<ComplexSequence.EventUtility> pairs = this.complexSequence.getYEventSetAndItsUtilityByTID(i);
                    for (ComplexSequence.EventUtility pair : pairs) {
                        int event = pair.event;
                        int utility = pair.utility;
                        RuleIntervalUtility thisIntervalUtility;
                        if (this.tighterUpperBound) {
                            int remainingUtility = CalculateTigherUpperBound(antiEpisodeEnd, i, i);
                            thisIntervalUtility = new RuleIntervalUtility(antiEpisodeStart, antiEpisodeEnd, i, i, antiEpisodeUtility + utility, antiEpisodeUtility, remainingUtility);
                        } else {
                            thisIntervalUtility = new RuleIntervalUtility(antiEpisodeStart, antiEpisodeEnd, i, i, antiEpisodeUtility + utility);
                        }
                        if (conseRecodeMap.containsKey(event)) {
                            conseRecodeMap.get(event).add(thisIntervalUtility);
                        } else {
                            ArrayList<RuleIntervalUtility> intervalUtilityList = new ArrayList<RuleIntervalUtility>();
                            intervalUtilityList.add(thisIntervalUtility);
                            conseRecodeMap.put(event, intervalUtilityList);
                        }
                    }
                }
            }

            for (Map.Entry<Integer, List<RuleIntervalUtility>> conseRecodeMapItem : conseRecodeMap.entrySet()) {
                Integer key = conseRecodeMapItem.getKey();
                List<UPOERRuleOccur> ruleOccur = new ArrayList<UPOERRuleOccur>();
                List<RuleIntervalUtility> ruleList = conseRecodeMapItem.getValue();


                if (ruleList.size() < antiCount * this.minConfidence) {
                    continue;
                }
                int possibleRuleStart = -1;
                int possibleRuleCount = 0;
                int realRuleStart = -1;
                int realRuleCount = 0;
                int realRuleUtility = 0;
                int sumRuleUtility = 0;
                int realRuleRemainingUtility = 0;
                int sumRuleRemainingUtility = 0;

                for (RuleIntervalUtility occur : ruleList) {

                    if (occur.antiStart > realRuleStart && occur.conseStart - occur.antiEnd < this.XYSpan) {
                        realRuleCount++;
                        realRuleStart = occur.conseEnd;
                        realRuleUtility = occur.utility;
                        sumRuleUtility += occur.utility;
                    }
                    if (occur.antiStart <= realRuleStart && occur.conseStart - occur.antiEnd < this.XYSpan) {
                        // for redundant occurrences of a rule, we take the maximum utility as their utility
                        if (occur.utility > realRuleUtility) {
                            sumRuleUtility -= realRuleUtility;
                            sumRuleUtility += occur.utility;
                            realRuleUtility = occur.utility;
                        }
                    }

                    if (tighterUpperBound) {
                        if (occur.antiStart <= possibleRuleStart) {
                            if (occur.antiUtility + occur.remainingUtility > realRuleRemainingUtility) {
                                // for redundant occurrences of a rule, we take the maximum utility as their REEU
                                sumRuleRemainingUtility -= realRuleRemainingUtility;
                                sumRuleRemainingUtility += occur.antiUtility + occur.remainingUtility;
                                realRuleRemainingUtility = occur.antiUtility + occur.remainingUtility;
                            }
                        } else {
                            realRuleRemainingUtility = occur.antiUtility + occur.remainingUtility;
                            sumRuleRemainingUtility += occur.antiUtility + occur.remainingUtility;
                        }
                    }

                    if (occur.antiStart > possibleRuleStart) {
                        possibleRuleCount++;
                        possibleRuleStart = occur.conseEnd;
                    }
                }
                if (possibleRuleCount < antiCount * this.minConfidence) {
                    continue;
                }
                List<Integer> conseEpisode = new ArrayList<Integer>();
                conseEpisode.add(key);

                if (realRuleCount >= antiCount * this.minConfidence && !antiEpisode.equals(conseEpisode) && sumRuleUtility >= minUtility) {
                    // we save it
                    saveRule(antiEpisode, conseEpisode, antiCount, realRuleCount, sumRuleUtility);
                }

                Map<Integer, List<RuleIntervalUtility>> tempRuleMap = new HashMap<Integer, List<RuleIntervalUtility>>();

                // WEUP strategy: if WEU(x,e) < minutil, there is no need to expand the rule X->{e}
                if (matrix) {
                    if (!isExpandByCooMapAfter(antiEpisode, key)) {
                        continue;
                    }
                }

                // REEUP strategy: if REEU(r) < minutil, then it is an unpromising rule
                if (tighterUpperBound) {
                    if (sumRuleRemainingUtility < this.minUtility) {
                        continue;
                    }
                }

                this.CandidateNum++;

                // extend a rule with i-item to rules with i+1-item
                for (RuleIntervalUtility occur : ruleList) {
                    // Map<Integer, Boolean> hasBeenSeem = new HashMap<>();
                    int intervalStart = Math.max(occur.antiEnd + 1, occur.conseEnd - this.YSpan + 1);
                    // search [intervalStart, Y.start) to extend the rule
                    for (int i = intervalStart; i < occur.conseStart; ++i) {
                        if (this.complexSequence.getYEventSetAndItsUtilityByTID(i).isEmpty()) {
                            continue;
                        }
                        List<ComplexSequence.EventUtility> pairs = this.complexSequence.getYEventSetAndItsUtilityByTID(i);
                        int PairSize = pairs.size();
                        for (int z = PairSize - 1; z >= 0; z--) {
                            ComplexSequence.EventUtility pair = pairs.get(z);
                            int event = pair.event;
                            int utility = pair.utility;
                            // we expand the rule if the event > key
                            if (event > key) {
                                RuleIntervalUtility ruleIntervalUtility;
                                if (this.tighterUpperBound) {
                                    int remainingUtility = CalculateTigherUpperBound(occur.antiEnd, i, occur.conseEnd);
                                    ruleIntervalUtility = new RuleIntervalUtility(occur.antiStart, occur.antiEnd, i, occur.conseEnd, occur.utility + utility, occur.antiUtility, remainingUtility);
                                } else {
                                    ruleIntervalUtility = new RuleIntervalUtility(occur.antiStart, occur.antiEnd, i, occur.conseEnd, occur.utility + utility);
                                }
                                if (tempRuleMap.containsKey(event)) {
                                    tempRuleMap.get(event).add(ruleIntervalUtility);
                                } else {
                                    ArrayList<RuleIntervalUtility> appearTime = new ArrayList<>();
                                    appearTime.add(ruleIntervalUtility);
                                    tempRuleMap.put(event, appearTime);
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // search [Y.Start, Y.End] to extend the rule
                    for (int i = occur.conseStart; i <= occur.conseEnd; ++i) {
                        if (this.complexSequence.getYEventSetAndItsUtilityByTID(i).isEmpty()) {
                            continue;
                        }
                        List<ComplexSequence.EventUtility> pairs = this.complexSequence.getYEventSetAndItsUtilityByTID(i);
                        int PairSize = pairs.size();
                        for (int z = PairSize - 1; z >= 0; z--) {
                            ComplexSequence.EventUtility pair = pairs.get(z);
                            int event = pair.event;
                            int utility = pair.utility;
                            if (event > key) {
                                RuleIntervalUtility ruleIntervalUtility;
                                if (this.tighterUpperBound) {
                                    int remainingUtility = CalculateTigherUpperBound(occur.antiEnd, occur.conseStart, occur.conseEnd);
                                    ruleIntervalUtility = new RuleIntervalUtility(occur.antiStart, occur.antiEnd, occur.conseStart, occur.conseEnd, occur.utility + utility, occur.antiUtility, remainingUtility);
                                } else {
                                    ruleIntervalUtility = new RuleIntervalUtility(occur.antiStart, occur.antiEnd, occur.conseStart, occur.conseEnd, occur.utility + utility);
                                }
                                if (tempRuleMap.containsKey(event)) {
                                    tempRuleMap.get(event).add(ruleIntervalUtility);
                                } else {
                                    ArrayList<RuleIntervalUtility> appearTime = new ArrayList<>();
                                    appearTime.add(ruleIntervalUtility);
                                    tempRuleMap.put(event, appearTime);
                                }
                            } else {
                                break;
                            }
                        }
                    }
                    int intervalEnd = Math.min(occur.antiEnd + this.XYSpan + this.YSpan - 2, occur.conseStart + this.YSpan - 1);
                    // search [Y.end + 1, intervalEnd) to extend the rule
                    for (int i = occur.conseEnd + 1; i <= intervalEnd; ++i) {
                        if (this.complexSequence.getYEventSetAndItsUtilityByTID(i).isEmpty()) {
                            continue;
                        }
                        List<ComplexSequence.EventUtility> pairs = this.complexSequence.getYEventSetAndItsUtilityByTID(i);
                        int PairSize = pairs.size();
                        for (int z = PairSize - 1; z >= 0; z--) {
                            ComplexSequence.EventUtility pair = pairs.get(z);
                            int event = pair.event;
                            int utility = pair.utility;
                            if (event > key) {
                                RuleIntervalUtility ruleIntervalUtility;
                                if (this.tighterUpperBound) {
                                    int remainingUtility = CalculateTigherUpperBound(occur.antiEnd, occur.conseStart, i);
                                    ruleIntervalUtility = new RuleIntervalUtility(occur.antiStart, occur.antiEnd, occur.conseStart, i, occur.utility + utility, occur.antiUtility, remainingUtility);
                                } else {
                                    ruleIntervalUtility = new RuleIntervalUtility(occur.antiStart, occur.antiEnd, occur.conseStart, i, occur.utility + utility);
                                }
                                if (tempRuleMap.containsKey(event)) {
                                    tempRuleMap.get(event).add(ruleIntervalUtility);
                                } else {
                                    ArrayList<RuleIntervalUtility> appearTime = new ArrayList<>();
                                    appearTime.add(ruleIntervalUtility);
                                    tempRuleMap.put(event, appearTime);
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
                // scan tempRuleMap and put vaild rule in ruleAppear, and possible rule in
                // ruleOccur
                for (Map.Entry<Integer, List<RuleIntervalUtility>> tempRecodeMapItem : tempRuleMap.entrySet()) {
                    Integer tempKey = tempRecodeMapItem.getKey();
                    List<RuleIntervalUtility> tempRuleList = tempRecodeMapItem.getValue();
                    if (tempRuleList.size() < antiCount * this.minConfidence) {
                        continue;
                    }
                    int tempPossibleRuleStart = -1;
                    int tempPossibleRuleCount = 0;
                    int tempRealRuleStart = -1;
                    int tempRealRuleCount = 0;
                    int tempRealRuleUtility = 0;
                    int tempSumRuleUtility = 0;
                    int tempRealRuleRemainingUtility = 0;
                    int tempSumRuleRemainingUtility = 0;
                    for (RuleIntervalUtility occur : tempRuleList) {
                        if (occur.antiStart > tempRealRuleStart && occur.conseStart - occur.antiEnd < this.XYSpan) {
                            tempRealRuleCount++;
                            tempRealRuleStart = occur.conseEnd;
                            tempRealRuleUtility = occur.utility;
                            tempSumRuleUtility += occur.utility;

                        }
                        if (occur.antiStart <= tempRealRuleStart && occur.conseStart - occur.antiEnd < this.XYSpan) {
                            // for redundant occurrences of a rule, we take the maximum utility as their utility
                            if (occur.utility > tempRealRuleUtility) {
                                tempSumRuleUtility -= tempRealRuleUtility;
                                tempSumRuleUtility += occur.utility;
                                tempRealRuleUtility = occur.utility;
                            }
                        }

                        if (tighterUpperBound) {
                            if (occur.antiStart > tempPossibleRuleStart) {
                                tempRealRuleRemainingUtility = occur.antiUtility + occur.remainingUtility;
                                tempSumRuleRemainingUtility += occur.antiUtility + occur.remainingUtility;
                            } else {
                                if (occur.antiUtility + occur.remainingUtility > tempRealRuleRemainingUtility) {
                                    tempSumRuleRemainingUtility -= tempRealRuleRemainingUtility;
                                    tempSumRuleRemainingUtility += occur.antiUtility + occur.remainingUtility;
                                    tempRealRuleRemainingUtility = occur.antiUtility + occur.remainingUtility;
                                }
                            }
                        }
                        if (occur.antiStart > tempPossibleRuleStart) {
                            tempPossibleRuleCount++;
                            tempPossibleRuleStart = occur.conseEnd;
                        }
                    }
                    if (tempPossibleRuleCount < antiCount * this.minConfidence) {
                        continue;
                    }
                    List<Integer> tempConseEpisode = new ArrayList<Integer>();
                    tempConseEpisode.add(key);
                    tempConseEpisode.add(tempKey);


                    if (tempRealRuleCount >= antiCount * this.minConfidence && !antiEpisode.equals(tempConseEpisode) && tempSumRuleUtility >= minUtility) {
                        //this.HUPOERs.add(new UPOER(antiEpisode, tempConseEpisode, null, antiCount, tempRealRuleCount, tempSumRuleUtility));
                        saveRule(antiEpisode, tempConseEpisode, antiCount, tempRealRuleCount, tempSumRuleUtility);
                    }
                    ruleOccur.add(new UPOERRuleOccur(tempConseEpisode, tempRuleList, tempSumRuleUtility, tempSumRuleRemainingUtility));
                    MemoryLogger.getInstance().checkMemory();
                }
                int breadthSearthStart = 0;
                int breadthSearthEnd = ruleOccur.size();
                // extend a rule with i-item to rules with i+1-item
                while (breadthSearthStart < breadthSearthEnd) {
                    tempRuleMap.clear();
                    UPOERRuleOccur oneOccurRule = ruleOccur.get(breadthSearthStart);
                    breadthSearthStart++;
                    List<Integer> episode = oneOccurRule.getEpisode();
                    Integer compareKey = episode.get(episode.size() - 1);
                    // for any x of X, and y of Y, if AWU(x,y)< minutil, there is no need to expand the rule X->Y
                    if (matrix) {
                        if (!isExpandByCooMapAfter(antiEpisode, compareKey)) {
                            continue;
                        }
                    }

                    // REEUP strategy
                    if (tighterUpperBound) {
                        if (oneOccurRule.getRemainingUtility() < this.minUtility) {
                            continue;
                        }
                    }
                    this.CandidateNum++;

                    List<RuleIntervalUtility> oneOccurRuleIntervalUtilityList = oneOccurRule.getIntervals();
                    for (RuleIntervalUtility oneOccurRuleIntervalUtility : oneOccurRuleIntervalUtilityList) {
                        int intervalStart = Math.max(oneOccurRuleIntervalUtility.antiEnd + 1, oneOccurRuleIntervalUtility.conseEnd - this.YSpan + 1);
                        // search [intervalStart, Y.start] to extend the rule
                        for (int i = intervalStart; i < oneOccurRuleIntervalUtility.conseStart; ++i) {
                            if (this.complexSequence.getEventSetAndItsUtilityByTID(i).isEmpty()) {
                                continue;
                            }
                            List<ComplexSequence.EventUtility> pairs = this.complexSequence.getEventSetAndItsUtilityByTID(i);
                            int PairSize = pairs.size();
                            for (int z = PairSize - 1; z >= 0; z--) {
                                ComplexSequence.EventUtility pair = pairs.get(z);
                                int event = pair.event;
                                int utility = pair.utility;
                                // we expand the rule if the event > key
                                if (event > compareKey) {
                                    RuleIntervalUtility ruleIntervalUtility;
                                    if (this.tighterUpperBound) {
                                        int remainingUtility = CalculateTigherUpperBound(oneOccurRuleIntervalUtility.antiEnd, i, oneOccurRuleIntervalUtility.conseEnd);
                                        ruleIntervalUtility = new RuleIntervalUtility(oneOccurRuleIntervalUtility.antiStart, oneOccurRuleIntervalUtility.antiEnd, i, oneOccurRuleIntervalUtility.conseEnd, oneOccurRuleIntervalUtility.utility + utility, oneOccurRuleIntervalUtility.antiUtility, remainingUtility);
                                    } else {
                                        ruleIntervalUtility = new RuleIntervalUtility(oneOccurRuleIntervalUtility.antiStart, oneOccurRuleIntervalUtility.antiEnd, i, oneOccurRuleIntervalUtility.conseEnd, oneOccurRuleIntervalUtility.utility + utility);
                                    }
                                    if (tempRuleMap.containsKey(event)) {
                                        tempRuleMap.get(event).add(ruleIntervalUtility);
                                    } else {
                                        ArrayList<RuleIntervalUtility> appearTime = new ArrayList<>();
                                        appearTime.add(ruleIntervalUtility);
                                        tempRuleMap.put(event, appearTime);
                                    }
                                } else {
                                    break;
                                }
                            }
                        }
                        // search [Y.start, Y.end] to extend the rule
                        for (int i = oneOccurRuleIntervalUtility.conseStart; i <= oneOccurRuleIntervalUtility.conseEnd; ++i) {
                            if (this.complexSequence.getEventSetAndItsUtilityByTID(i).isEmpty()) {
                                continue;
                            }
                            List<ComplexSequence.EventUtility> pairs = this.complexSequence.getEventSetAndItsUtilityByTID(i);
                            int PairSize = pairs.size();
                            for (int z = PairSize - 1; z >= 0; z--) {
                                ComplexSequence.EventUtility pair = pairs.get(z);
                                int event = pair.event;
                                int utility = pair.utility;
                                if (event > compareKey) {
                                    RuleIntervalUtility ruleIntervalUtility;
                                    if (this.tighterUpperBound) {
                                        int remainingUtility = CalculateTigherUpperBound(oneOccurRuleIntervalUtility.antiEnd, oneOccurRuleIntervalUtility.conseStart, oneOccurRuleIntervalUtility.conseEnd);
                                        ruleIntervalUtility = new RuleIntervalUtility(oneOccurRuleIntervalUtility.antiStart, oneOccurRuleIntervalUtility.antiEnd, oneOccurRuleIntervalUtility.conseStart, oneOccurRuleIntervalUtility.conseEnd, oneOccurRuleIntervalUtility.utility + utility, oneOccurRuleIntervalUtility.antiUtility, remainingUtility);
                                    } else {
                                        ruleIntervalUtility = new RuleIntervalUtility(oneOccurRuleIntervalUtility.antiStart, oneOccurRuleIntervalUtility.antiEnd, oneOccurRuleIntervalUtility.conseStart, oneOccurRuleIntervalUtility.conseEnd, oneOccurRuleIntervalUtility.utility + utility);
                                    }
                                    if (tempRuleMap.containsKey(event)) {
                                        tempRuleMap.get(event).add(ruleIntervalUtility);
                                    } else {
                                        ArrayList<RuleIntervalUtility> appearTime = new ArrayList<>();
                                        appearTime.add(ruleIntervalUtility);
                                        tempRuleMap.put(event, appearTime);
                                    }
                                } else {
                                    break;
                                }
                            }
                        }
                        // search [Y.end + 1, intervalEnd) to extend the rule
                        int intervalEnd = Math.min(oneOccurRuleIntervalUtility.antiEnd + this.XYSpan + this.YSpan - 2, oneOccurRuleIntervalUtility.conseStart + this.YSpan - 1);
                        for (int i = oneOccurRuleIntervalUtility.conseEnd + 1; i <= intervalEnd; ++i) {
                            if (this.complexSequence.getEventSetAndItsUtilityByTID(i).isEmpty()) {
                                continue;
                            }
                            List<ComplexSequence.EventUtility> pairs = this.complexSequence.getEventSetAndItsUtilityByTID(i);
                            int PairSize = pairs.size();
                            for (int z = PairSize - 1; z >= 0; z--) {
                                ComplexSequence.EventUtility pair = pairs.get(z);
                                int event = pair.event;
                                int utility = pair.utility;
                                if (event > compareKey) {
                                    RuleIntervalUtility ruleIntervalUtility;
                                    if (this.tighterUpperBound) {
                                        int remainingUtility = CalculateTigherUpperBound(oneOccurRuleIntervalUtility.antiEnd, oneOccurRuleIntervalUtility.conseStart, i);
                                        ruleIntervalUtility = new RuleIntervalUtility(oneOccurRuleIntervalUtility.antiStart, oneOccurRuleIntervalUtility.antiEnd, oneOccurRuleIntervalUtility.conseStart, i, oneOccurRuleIntervalUtility.utility + utility, oneOccurRuleIntervalUtility.antiUtility, remainingUtility);
                                    } else {
                                        ruleIntervalUtility = new RuleIntervalUtility(oneOccurRuleIntervalUtility.antiStart, oneOccurRuleIntervalUtility.antiEnd, oneOccurRuleIntervalUtility.conseStart, i, oneOccurRuleIntervalUtility.utility + utility);
                                    }
                                    if (tempRuleMap.containsKey(event)) {
                                        tempRuleMap.get(event).add(ruleIntervalUtility);
                                    } else {
                                        ArrayList<RuleIntervalUtility> appearTime = new ArrayList<>();
                                        appearTime.add(ruleIntervalUtility);
                                        tempRuleMap.put(event, appearTime);
                                    }
                                } else {
                                    break;
                                }
                            }
                        }
                    }
                    oneOccurRule.clear();
                    // scan tempRuleMap and put vaild rule in ruleAppear, and possible rule in
                    // ruleOccur
                    for (Map.Entry<Integer, List<RuleIntervalUtility>> tempRecodeMapItem : tempRuleMap.entrySet()) {
                        Integer tempKey = tempRecodeMapItem.getKey();
                        List<RuleIntervalUtility> tempRuleList = tempRecodeMapItem.getValue();
                        if (tempRuleList.size() < antiCount * this.minConfidence) {
                            continue;
                        }
                        int tempPossibleRuleStart = -1;
                        int tempPossibleRuleCount = 0;
                        int tempRealRuleStart = -1;
                        int tempRealRuleCount = 0;
                        int tempRealRuleUtility = 0;
                        int tempSumRuleUtility = 0;
                        int tempRealRuleRemainingUtility = 0;
                        int tempSumRuleRemainingUtility = 0;
                        for (RuleIntervalUtility occur : tempRuleList) {
                            if (occur.antiStart > tempRealRuleStart && occur.conseStart - occur.antiEnd < this.XYSpan) {
                                tempRealRuleCount++;
                                tempRealRuleStart = occur.conseEnd;
                                tempRealRuleUtility = occur.utility;
                                tempSumRuleUtility += occur.utility;
                            }
                            if (occur.antiStart <= tempRealRuleStart && occur.conseStart - occur.antiEnd < this.XYSpan) {
                                if (occur.utility > tempRealRuleUtility) {
                                    // for redundant occurrences of a rule, we take the maximum utility as their utility
                                    tempSumRuleUtility -= tempRealRuleUtility;
                                    tempSumRuleUtility += occur.utility;
                                    tempRealRuleUtility = occur.utility;
                                }
                            }

                            if (tighterUpperBound) {
                                if (occur.antiStart > tempPossibleRuleStart) {
                                    tempRealRuleRemainingUtility = occur.antiUtility + occur.remainingUtility;
                                    tempSumRuleRemainingUtility += occur.antiUtility + occur.remainingUtility;
                                } else {
                                    if (occur.antiUtility + occur.remainingUtility > tempRealRuleRemainingUtility) {
                                        tempSumRuleRemainingUtility -= tempRealRuleRemainingUtility;
                                        tempSumRuleRemainingUtility += occur.antiUtility + occur.remainingUtility;
                                        tempRealRuleRemainingUtility = occur.antiUtility + occur.remainingUtility;
                                    }
                                }
                            }

                            if (occur.antiStart > tempPossibleRuleStart) {
                                tempPossibleRuleCount++;
                                tempPossibleRuleStart = occur.conseEnd;
                            }
                        }
                        if (tempPossibleRuleCount < antiCount * this.minConfidence) {
                            continue;
                        }
                        List<Integer> tempConseEpisode = new ArrayList<Integer>(episode);
                        tempConseEpisode.add(tempKey);

                        if (tempRealRuleCount >= antiCount * this.minConfidence && !antiEpisode.equals(tempConseEpisode) && tempSumRuleUtility >= minUtility) {
                            // we save it
                            saveRule(antiEpisode, tempConseEpisode, antiCount, tempRealRuleCount, tempSumRuleUtility);
                        }
                        ruleOccur.add(new UPOERRuleOccur(tempConseEpisode, tempRuleList, tempSumRuleUtility, tempSumRuleRemainingUtility));
                    }
                    MemoryLogger.getInstance().checkMemory();
                    breadthSearthEnd = ruleOccur.size();
                }
            }
        }
    }

    public void saveRule(List<Integer> antiEpisode, List<Integer> conseEpisode, int antiCount, int realRuleCount, int ruleUtility) {
        this.HUPOERCount++;
        try {
            MemoryLogger.getInstance().checkMemory();

            StringBuilder buffer = new StringBuilder();


            for (Integer anti : antiEpisode) {
                buffer.append(anti);
                buffer.append(' ');
            }
            buffer.append("==> ");
            for (Integer conse : conseEpisode) {
                buffer.append(conse);
                buffer.append(' ');
            }
            buffer.append("#SUP: ");
            buffer.append(realRuleCount);
            buffer.append(" #CONF: ");
            buffer.append(formatter.format(realRuleCount / (double) antiCount));
            buffer.append(" #Util: ");
            buffer.append(ruleUtility + System.lineSeparator());

            writer.write(buffer.toString());
            //bw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isExpandByCooMapAfter(List<Integer> antiEpisode, Integer conseEvent) {
        for (Integer antiEvent : antiEpisode) {
            if (cooMapAfter.get(antiEvent) != null) {
                if (cooMapAfter.get(antiEvent).get(conseEvent) != null) {
                    if (cooMapAfter.get(antiEvent).get(conseEvent) < this.minUtility) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void save(List<UPOER> HUPOERs) {
        try {
            MemoryLogger.getInstance().checkMemory();
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.outputFile, false)));

            StringBuilder buffer = new StringBuilder();
            for (UPOER HUPOER : HUPOERs) {

                for (Integer anti : HUPOER.getAntiEpisode()) {
                    buffer.append(anti);
                    buffer.append(' ');
                }
                buffer.append("==> ");
                for (Integer conse : HUPOER.getConseEpisode()) {
                    buffer.append(conse);
                    buffer.append(' ');
                }
                buffer.append("#SUP: ");
                buffer.append(HUPOER.getAntiCount());
                buffer.append(" #CONF: ");
                buffer.append(formatter.format(HUPOER.getRuleCount() / (double) HUPOER.getAntiCount()));
                buffer.append(" #Util: ");
                buffer.append(HUPOER.getUtility() + System.lineSeparator());
            }
            bw.write(buffer.toString());
            bw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public int CalculateTigherUpperBound(int antiEnd, int conseStart, int conseEnd) {
        // calculate the search interval
        int searchStart = Math.max(antiEnd + 1, conseEnd - this.YSpan + 1);
        int searchEnd = Math.min(antiEnd + this.XYSpan + this.YSpan - 2, conseStart + this.YSpan - 1);
        // then we compute the sum of utility
        return complexSequence.getTotalUtilityOfDuration(searchStart, searchEnd);
    }

    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  HUPOERM - STATS =============");
        //HUPOERCount = this.HUPOERs.size();
        System.out.println(" Minimum utility : " + this.minUtility);
        System.out.println(" Candidate count : " + this.CandidateNum);
        System.out.println(" Rule count : " + this.HUPOERCount);
        System.out.println(" Maximum memory usage : " + formatter.format(this.maxMemory) + " mb");
        System.out.println(" Total time : " + this.deltaTimestamp + " ms");
        System.out.println("===================================================");
    }

    /**
     * implements a class contains moList and utility list of single candidates
     * (items)
     */
    public class SingleMoListUtilityList {
        // minimal occurrence list
        List<Integer> moList;
        // utility list
        List<Integer> utilityList;

        /**
         * Constructor
         */
        public SingleMoListUtilityList() {
            this.moList = new ArrayList<>();
            this.utilityList = new ArrayList<>();
        }

        /**
         * Constructor
         *
         * @param moList      a moList
         * @param utilityList a utility list
         */
        public SingleMoListUtilityList(List<Integer> moList, List<Integer> utilityList) {
            this.moList = moList;
            this.utilityList = utilityList;
        }

        /**
         * Add a minimal occurrence and its utility
         *
         * @param mo      a minimal occurrence
         * @param utility its utility
         */
        public void add(int mo, int utility) {
            this.moList.add(mo);
            this.utilityList.add(utility);
        }

        /**
         * Get the minimum occurrence list
         *
         * @return the minimum occurrence list
         */
        public List<Integer> getMoList() {
            return moList;
        }

        /**
         * Get the utility list
         *
         * @return the utility list
         */
        public List<Integer> getUtilityList() {
            return utilityList;
        }
    }


    /**
     * implements a class contains moList and utility list of single candidates
     * (items)
     */
    public class EpisodeMoListUtilityList {
        // Episode
        List<Integer> EpisodeList;
        // Start position of minimal occurrence
        List<Integer> moStartList;
        // End position of minimal occurrence
        List<Integer> moEndList;

        // utility list
        List<Integer> utilityList;

        /**
         * int[]: 0: start, 1: end, 2: utility
         */
        List<PositionsAndUtility> PositionAndUtilityList;

        /**
         * Constructor
         */
        public EpisodeMoListUtilityList() {
            this.EpisodeList = new ArrayList<>();
            this.moStartList = new ArrayList<>();
            this.moEndList = new ArrayList<>();
            this.utilityList = new ArrayList<>();
        }

        /**
         * Constructor
         *
         * @param EpisodeList Episode
         * @param moStartList a start position list of moList
         * @param moEndList   an end position list of moList
         * @param utilityList a utility list
         */
        public EpisodeMoListUtilityList(List<Integer> EpisodeList, List<Integer> moStartList, List<Integer> moEndList, List<Integer> utilityList) {
            this.EpisodeList = EpisodeList;
            this.moStartList = moStartList;
            this.moEndList = moEndList;
            this.utilityList = utilityList;
        }

        /**
         * @param EpisodeList            Episode
         * @param PositionAndUtilityList int[]: 0: start, 1: end, 2: utility
         */
        public EpisodeMoListUtilityList(List<Integer> EpisodeList, List<PositionsAndUtility> PositionAndUtilityList) {
            this.EpisodeList = EpisodeList;
            this.PositionAndUtilityList = PositionAndUtilityList;
        }

        /**
         * Add a minimal occurrence and its utility
         *
         * @param moStart start position of a minimal occurrence
         * @param moEnd   end position of a minimal occurrence
         * @param utility its utility
         */
        public void add(int moStart, int moEnd, int utility) {
            this.moStartList.add(moStart);
            this.moEndList.add(moEnd);
            this.utilityList.add(utility);
        }

        /**
         * Get the episode list
         *
         * @return the episode list
         */
        public List<Integer> getEpisodeList() {
            return EpisodeList;
        }

        /**
         * Get the start position list of minimum occurrence
         *
         * @return the start position list of minimum occurrence
         */
        public List<Integer> getMoStartList() {
            return moStartList;
        }

        /**
         * Get the end position list of minimum occurrence
         *
         * @return the end position list of minimum occurrence
         */
        public List<Integer> getMoEndList() {
            return moEndList;
        }

        /**
         * Get the utility list
         *
         * @return the utility list
         */
        public List<Integer> getUtilityList() {
            return utilityList;
        }

        /**
         * Get the position and utility List
         *
         * @return the position and utility List
         */
        public List<PositionsAndUtility> getPositionAndUtilityList() {
            return PositionAndUtilityList;
        }
    }

    public class PositionsAndUtility {
        int start;
        int end;
        int utility;

        public PositionsAndUtility(int start, int end, int utility) {
            this.start = start;
            this.end = end;
            this.utility = utility;
        }
    }

}
