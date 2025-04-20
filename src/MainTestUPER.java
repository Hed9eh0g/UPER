import java.io.IOException;


public class MainTestUPER {

    public static void main(String[] args) throws IOException {
        // the min support of HUPOERM algorithm
        int minSupport =10;
        // the XSpan of HUPOERM algorithm
        int xSpan =1;
        // the YSpan of HUPOERM algorithm
        int ySpan =2;
        // the XYSpan of HUPOERM algorithm
        int xySpan =2;
        // the minimum utility threshold : a ratio
        double minUtilityRatio = 0.033;
        // the min confidence of HUPOERM algorithm
        double minConfidence = 0.7;
        // the maximum line number
        int maxLineNumber = 10000000;
        // whether to use matrix
        boolean matrix=true;
        // whether to use tighter upper bound
        boolean tighterUpperBound=true;
        // Input file
        String inputFile = "Malware/Worms_translate3_result.txt";
        // Output file
        String outputFile = "output.txt";

        AlgoUPER HUPOERM = new AlgoUPER();

        //poerm.runAlgorithm(inputFile, minSupport, xSpan, ySpan, minConfidence, winlen, selfIncrement);
        HUPOERM.runAlgorithm(inputFile, outputFile, minSupport, minConfidence, minUtilityRatio, xSpan, ySpan, xySpan, maxLineNumber,matrix, tighterUpperBound);
        HUPOERM.printStats();
    }

}