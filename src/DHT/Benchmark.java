package DHT;

import peersim.core.*;
import peersim.config.*;
import peersim.edsim.EDSimulator;

import java.util.*;

/**
 * Benchmark — drives parameter sweeps across PeerSim experiments and
 * measures DHT ring quality at the end of each run.
 */
public class Benchmark implements peersim.core.Control {

    // ------------------------------------------------------------------ sweep tables

    private static final int[] LEAFSET_SIZES   = {2, 4, 6, 8};
    private static final int[] MAX_NEIGHBOURS  = {2, 4, 6, 8};
    private static final int[] MAX_IDS         = {100, 500, 1000, 2000};

    public static final int TOTAL_EXPERIMENTS =
            LEAFSET_SIZES.length + MAX_NEIGHBOURS.length + MAX_IDS.length;

    // --- NEW: Static variable to track the current experiment index ---
    private static int currentExpIndex = 0;

    // ------------------------------------------------------------------ hop tracking

    public static final Map<Long, Integer> joinHopCounts = new HashMap<>();

    // ------------------------------------------------------------------ results storage

    private static final List<ExperimentResult> results = new ArrayList<>();

    // ------------------------------------------------------------------ lifecycle

    private final int DHTPid;

    public Benchmark(String prefix) {
        this.DHTPid = Configuration.getPid(prefix + ".DHTProtocolPid");
    }

    // ------------------------------------------------------------------ parameter injection

    /**
     * Called by Initializer at the very start of each experiment.
     */
    public static void applyExperimentParameters(int experiment) {
        // Save the index so execute() can access it later
        currentExpIndex = experiment;

        int leafsetSize, maxNeighbours, maxIDlogique;

        if (experiment < LEAFSET_SIZES.length) {
            leafsetSize  = LEAFSET_SIZES[experiment];
            maxNeighbours = 4;
            maxIDlogique  = 1000;
        } else if (experiment < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length) {
            int i = experiment - LEAFSET_SIZES.length;
            leafsetSize   = 4;
            maxNeighbours = MAX_NEIGHBOURS[i];
            maxIDlogique  = 1000;
        } else {
            int i = experiment - LEAFSET_SIZES.length - MAX_NEIGHBOURS.length;
            leafsetSize   = 4;
            maxNeighbours = 4;
            maxIDlogique  = MAX_IDS[i];
        }

        System.setProperty("simulation.leafsetSize",   String.valueOf(leafsetSize));
        System.setProperty("simulation.maxNeighbours", String.valueOf(maxNeighbours));
        System.setProperty("simulation.maxIDlogique",  String.valueOf(maxIDlogique));

        System.out.println("\n>>> Starting Experiment " + experiment
                + " [ls=" + leafsetSize + ", mn=" + maxNeighbours + ", mid=" + maxIDlogique + "]");
    }

    // ------------------------------------------------------------------ control execution

    @Override
    public boolean execute() {
        // Use the static index instead of CommonState
        int exp    = currentExpIndex;
        int nodeNb = Network.size();

        long leafsetSize   = Long.parseLong(System.getProperty("simulation.leafsetSize", "4"));
        long maxNeighbours = Long.parseLong(System.getProperty("simulation.maxNeighbours", "4"));
        long maxIDlogique  = Long.parseLong(System.getProperty("simulation.maxIDlogique", "1000"));

        Metrics m = computeMetrics(nodeNb, (int) leafsetSize, maxIDlogique, (int) maxNeighbours);

        String sweepName  = getSweepName(exp);
        String paramLabel = getParamLabel(exp, (int) leafsetSize, (int) maxNeighbours, (int) maxIDlogique);

        results.add(new ExperimentResult(sweepName, paramLabel, m));

        joinHopCounts.clear();

        // Print full table on the last experiment
        if (exp == TOTAL_EXPERIMENTS - 1) {
            printResultsTable();
        }

        return false;
    }

    // ------------------------------------------------------------------ metrics logic

    private Metrics computeMetrics(int nodeNb, int leafsetSize, long maxIDlogique, int maxNeighbours) {
        Metrics m        = new Metrics();
        m.totalNodes     = nodeNb;

        long[] sortedIds = new long[nodeNb];
        DHTNode[] nodes  = new DHTNode[nodeNb];
        for (int i = 0; i < nodeNb; i++) {
            nodes[i]     = (DHTNode) Network.get(i).getProtocol(DHTPid);
            sortedIds[i] = nodes[i].getLogicalId();
        }
        Arrays.sort(sortedIds);

        int totalSlots    = 0;
        int correctSlots  = 0;
        int full          = 0;
        int half          = leafsetSize / 2;
        int fullLongLinks = 0;

        for (DHTNode node : nodes) {
            List<Node> ls = node.getLeafset();
            if (ls.size() >= leafsetSize) full++;
            if (node.getFurthestNodes().size() >= maxNeighbours) fullLongLinks++;

            Set<Long> trueNeighbors = getTrueNeighbors(node.getLogicalId(), sortedIds, half);
            totalSlots += ls.size();
            for (Node n : ls) {
                if (trueNeighbors.contains(((DHTNode)n.getProtocol(DHTPid)).getLogicalId())) {
                    correctSlots++;
                }
            }
        }

        m.completenessPercent = 100.0 * full / nodeNb;
        m.correctnessPercent  = totalSlots > 0 ? 100.0 * correctSlots / totalSlots : 0;
        m.longLinkCoverage    = 100.0 * fullLongLinks / nodeNb;

        if (!joinHopCounts.isEmpty()) {
            double total = 0;
            for (int h : joinHopCounts.values()) total += h;
            m.avgHops = total / joinHopCounts.size();
        }

        return m;
    }

    private Set<Long> getTrueNeighbors(long myId, long[] sortedIds, int half) {
        Set<Long> result = new HashSet<>();
        int n   = sortedIds.length;
        int pos = Arrays.binarySearch(sortedIds, myId);
        if (pos < 0) return result;

        for (int i = 1; i <= half; i++) {
            result.add(sortedIds[(pos + i) % n]);
            result.add(sortedIds[Math.floorMod(pos - i, n)]);
        }
        return result;
    }

    // ------------------------------------------------------------------ output

    private void printResultsTable() {
        System.out.println("\n+------------------------------------------------------------------------------+");
        System.out.println("|                         BENCHMARK RESULTS SUMMARY                          |");
        System.out.println("+------------------------------------------------------------------------------+");

        String[] sweeps = {"leafsetSize", "maxNeighbours", "maxIDlogique"};
        for (String sweep : sweeps) {
            System.out.println("\n| Sweep: " + sweep);
            System.out.printf("| %-16s | %-12s | %-12s | %-12s | %-10s%n",
                    "Value", "Completeness", "Correctness", "LongLinks", "AvgHops");
            System.out.println("| " + "-".repeat(74));
            for (ExperimentResult r : results) {
                if (!r.sweepName.equals(sweep)) continue;
                System.out.printf("| %-16s | %10.1f%% | %10.1f%% | %10.1f%% | %10.2f%n",
                        r.paramLabel,
                        r.metrics.completenessPercent,
                        r.metrics.correctnessPercent,
                        r.metrics.longLinkCoverage,
                        r.metrics.avgHops);
            }
        }
        System.out.println("+------------------------------------------------------------------------------+\n");
    }

    private String getSweepName(int exp) {
        if (exp < LEAFSET_SIZES.length) return "leafsetSize";
        if (exp < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length) return "maxNeighbours";
        return "maxIDlogique";
    }

    private String getParamLabel(int exp, int ls, int mn, int mid) {
        if (exp < LEAFSET_SIZES.length) return "ls=" + ls;
        if (exp < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length) return "mn=" + mn;
        return "mid=" + mid;
    }

    static class Metrics {
        int totalNodes;
        double completenessPercent, correctnessPercent, longLinkCoverage, avgHops;
    }

    static class ExperimentResult {
        final String sweepName, paramLabel;
        final Metrics metrics;
        ExperimentResult(String s, String p, Metrics m) {
            sweepName = s; paramLabel = p; metrics = m;
        }
    }
}