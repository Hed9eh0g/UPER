import java.util.*;

public class ComplexSequence {
    /**
     * a map with key: TID(timepoint) value: (events, eventsUtility, totalUtility)
     */
    public Map<Integer, EventsEventsUtilityTotalUtility> mapItemEET;

    public Map<Integer, EventsEventsUtilityTotalUtility> mapYItemEET;

    /**
     * the largest time point in the sequence
     */
    private int largestTID;

    /**
     * constructor
     */
    public ComplexSequence() {
        this.mapItemEET = new HashMap<>();
        this.largestTID = 0;
    }

    /**
     * Add the total utility (transaction utility) of TID to the sequence
     *
     * @param tid          the transaction id
     * @param totalUtility the total utility
     */
    public void setTotalUtility(int tid, int totalUtility) {
        this.mapItemEET.get(tid).setTotalUtility(totalUtility);
    }

    /**
     * set the largest TID
     *
     * @param tid the transaction id
     */
    public void setLargestTID(int tid) {
        this.largestTID = tid;
    }

    /**
     * Add a event with its utility to the sequence
     *
     * @param tid     a transaction id
     * @param event   an event
     * @param utility the utility
     */
    public void add(int tid, int event, int utility) {
        EventsEventsUtilityTotalUtility eet = this.mapItemEET.get(tid);
        if (eet == null) {
            eet = new EventsEventsUtilityTotalUtility();
            this.mapItemEET.put(tid, eet);
        }
        eet.add(event, utility);
    }

    /**
     * Get an event set and its utility for a given TID
     *
     * @param tid the transaction identifier
     * @return the event set
     */
    public List<EventUtility> getEventSetAndItsUtilityByTID(int tid) {
        return this.mapItemEET.containsKey(tid) ? this.mapItemEET.get(tid).getPairs() : new ArrayList<>();
    }

    /**
     * Get an event set and its utility for a given TID
     *
     * @param tid the transaction identifier
     * @return the event set
     */
    public List<EventUtility> getYEventSetAndItsUtilityByTID(int tid) {
        return this.mapYItemEET.containsKey(tid) ? this.mapYItemEET.get(tid).getPairs() : new ArrayList<>();
    }

    /**
     * This methods do four things: 1. According to maxDuration and
     * minUtilityAbsolute measure, calculate the TWU of each event, 2. get the
     * singleCandidates that thier TWU larger than minUtilityAbsolute (remove the
     * non 1-candidate from mapSingleCandidatesWithMOs_Utility_Pair) 3. prune the
     * non singleCandidates from the complex sequence 4. sort each event at each
     * timepoint by the TWU order
     *
     * @param maxDuration                           a maximum duration
     * @param minUtilityAbsolute                    a minimum utility value
     * @param mapSingleCandidatesWithMOsUtilityPair a map of
     */
    public void pruneSingleEventsByUpperBound(int maxDuration, double minUtilityAbsolute, int minsup, double minconf,
                                              Map<Integer, AlgoUPER.SingleMoListUtilityList> mapSingleCandidatesWithMOsUtilityPair) {

        // 1. calculate active utility
        /*
          key: event, value: active utility = utility of active occurrence
         */
        Map<Integer, Integer> mapEventWithActUtility = new HashMap<>();

        for (int TID = 1; TID <= this.largestTID; TID++) {
            EventsEventsUtilityTotalUtility eet = this.mapItemEET.get(TID);
            if (eet == null) {
                continue;
            }
            // active utility: the utility from TID-maxDuration+1 to TID+maxDuration-1
            int actUtility = this.getTotalUtilityOfDuration(TID - maxDuration, TID + maxDuration);
            for (EventUtility pair : eet.getPairs()) {
                // pair[0] : item , pair[1] : utility
                mapEventWithActUtility.put(pair.event, mapEventWithActUtility.getOrDefault(pair.event, 0) + actUtility);
            }
        }

        // 2. get the singleCandidates and their minimal occurrences
        // active utility remove
        for (int item : mapEventWithActUtility.keySet()) {
            int actUtility = mapEventWithActUtility.get(item);
            int support = mapSingleCandidatesWithMOsUtilityPair.get(item).moList.size();
            if (support < minsup || actUtility < minUtilityAbsolute) {
                mapSingleCandidatesWithMOsUtilityPair.remove(item);
            }

        }
        mapYItemEET = mapItemEET;

        // 3. remove the non 1-candidates from the complex sequence
        for (int TID = 1; TID <= this.largestTID; TID++) {
            EventsEventsUtilityTotalUtility eet = this.mapItemEET.get(TID);
            EventsEventsUtilityTotalUtility Yeet = this.mapYItemEET.get(TID);
            if (eet == null || Yeet == null) {
                continue;
            }

            // pairs[pos][0] represent a event with the order pos
            // pairs[pos][1] represent the utility of the event with the order pos
            List<EventUtility> pairs = eet.getPairs();
            List<EventUtility> Ypairs = Yeet.getPairs();

            // record the sum of utility by removing
            int removedUtilitySum = 0;
            int YremovedUtilitySum = 0;

            // remove event
            for (int i = pairs.size() - 1; i >= 0; i--) {
                int event = pairs.get(i).event;
                if (!mapSingleCandidatesWithMOsUtilityPair.containsKey(event)) {
                    // record the removed utility
                    removedUtilitySum += pairs.get(i).utility;
                    pairs.remove(i);
                }
            }

            for (int i = Ypairs.size() - 1; i >= 0; i--) {
                int event = Ypairs.get(i).event;
                if (!mapSingleCandidatesWithMOsUtilityPair.containsKey(event)) {
                    // record the removed utility
                    YremovedUtilitySum += Ypairs.get(i).utility;
                    Ypairs.remove(i);
                } else {
                    int Ysupport = mapSingleCandidatesWithMOsUtilityPair.get(event).moList.size();
                    if (Ysupport < minsup * minconf) {
                        YremovedUtilitySum += pairs.get(i).utility;
                        Ypairs.remove(i);
                    }
                }
            }

            if (pairs.size() > 0) {
                // 4. sort the pairs by the order of the upper-bound utility
                // sort the pairs by the order of the active utility
                eet.setPairs(pairs);
                eet.setTotalUtility(eet.getTotalUtility() - removedUtilitySum);
            } else {
                this.mapItemEET.remove(TID);
            }

            if (Ypairs.size() > 0) {
                Yeet.setPairs(Ypairs);
                Yeet.setTotalUtility(Yeet.getTotalUtility() - YremovedUtilitySum);
            } else {
                this.mapYItemEET.remove(TID);
            }
        }

        mapEventWithActUtility.clear();

    }

    /**
     * Get the total utility of all timepoints in the [start, end] contains start
     * and end
     *
     * @param start start timepoint
     * @param end   end timepoint
     * @return
     */
    public int getTotalUtilityOfDuration(int start, int end) {

        if (start > this.largestTID) {
            return 0;
        }
        if (end > this.largestTID) {
            end = this.largestTID;
        }
        int totalUtility = 0;
        for (int TID = start; TID <= end; TID++) {
            if (this.mapItemEET.containsKey(TID)) {
                totalUtility += this.mapItemEET.get(TID).getTotalUtility();
            }
        }
        return totalUtility;
    }

    /**
     * implements a class contains events, their utilities, and the totalUtility for
     * one timepoint
     */
    public class EventsEventsUtilityTotalUtility {
        /**
         * An array of pairs where: pairs[pos][0] represent an event with the order pos
         * pairs[pos][1] represent the utility of the event with the order pos
         */
        List<EventUtility> pairs;

        /**
         * the total utility
         */
        int totalUtility;

        /**
         * Constructor
         */
        public EventsEventsUtilityTotalUtility() {
            this.pairs = new ArrayList<>();
            this.totalUtility = 0;
        }

        /**
         * Constructor with a list of pairs and a total utility
         */
        public EventsEventsUtilityTotalUtility(List<EventUtility> pairs, int totalUtility) {
            this.pairs = pairs;
            this.totalUtility = totalUtility;
        }

        /**
         * Add ane event and its utility to the list of pairs
         *
         * @param event   an event
         * @param utility its utility
         */
        public void add(int event, int utility) {
            this.pairs.add(new EventUtility(event, utility));
        }

        /**
         * Set the total utility
         *
         * @param totalUtility the total utility
         */
        public void setTotalUtility(int totalUtility) {
            this.totalUtility = totalUtility;
        }

        /**
         * Get the total utility
         *
         * @return the total utility
         */
        public int getTotalUtility() {
            return totalUtility;
        }

        /**
         * Get the list of pairs
         *
         * @return the list of pairs
         */
        public List<EventUtility> getPairs() {
            return pairs;
        }

        /**
         * Set the list of pairs
         *
         * @param pairs the list of pairs
         */
        public void setPairs(List<EventUtility> pairs) {
            this.pairs = pairs;
        }


    }

    public class EventUtility {
        int event;
        int utility;

        public EventUtility(int event, int utility) {
            this.event = event;
            this.utility = utility;
        }
    }
}
