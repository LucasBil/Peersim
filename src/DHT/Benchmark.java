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

    private static final int[] LEAFSET_SIZES   = {2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 30};
    private static final int[] MAX_NEIGHBOURS  = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    private static final int[] MAX_IDS         = {100, 250, 500, 1000, 2000, 3000, 5000};

    private static final int TRIALS_PER_CONFIG = 5;

    // Total experiments: (11 + 10 + 7) * 5 = 140
    public static final int TOTAL_EXPERIMENTS =
            (LEAFSET_SIZES.length + MAX_NEIGHBOURS.length + MAX_IDS.length) * TRIALS_PER_CONFIG;

    private static int currentExpIndex = 0;

    // ------------------------------------------------------------------ averaging storage

    private static final List<ExperimentResult> results = new ArrayList<>();
    private static Metrics accumulator = new Metrics(); // Sums values across trials

    // ------------------------------------------------------------------ hop tracking

    public static final Map<Long, Integer> joinHopCounts = new HashMap<>();

    // ------------------------------------------------------------------ lifecycle

    private final int DHTPid;

    public Benchmark(String prefix) {
        this.DHTPid = Configuration.getPid(prefix + ".DHTProtocolPid");
    }

    // ------------------------------------------------------------------ parameter injection

    public static void applyExperimentParameters(int experiment) {
        currentExpIndex = experiment;
        int configIndex = experiment / TRIALS_PER_CONFIG;

        int leafsetSize, maxNeighbours, maxIDlogique;

        if (configIndex < LEAFSET_SIZES.length) {
            leafsetSize   = LEAFSET_SIZES[configIndex];
            maxNeighbours = 4;
            maxIDlogique  = 1000;
        } else if (configIndex < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length) {
            int i = configIndex - LEAFSET_SIZES.length;
            leafsetSize   = 4;
            maxNeighbours = MAX_NEIGHBOURS[i];
            maxIDlogique  = 1000;
        } else {
            int i = configIndex - LEAFSET_SIZES.length - MAX_NEIGHBOURS.length;
            leafsetSize   = 4;
            maxNeighbours = 4;
            maxIDlogique  = MAX_IDS[i];
        }

        System.setProperty("simulation.leafsetSize",   String.valueOf(leafsetSize));
        System.setProperty("simulation.maxNeighbours", String.valueOf(maxNeighbours));
        System.setProperty("simulation.maxIDlogique",  String.valueOf(maxIDlogique));

        System.out.println(">>> Run " + experiment + " (Config " + configIndex + ") | Trial " + (experiment % TRIALS_PER_CONFIG + 1));
    }

    // ------------------------------------------------------------------ control execution

    @Override
    public boolean execute() {
        int exp         = currentExpIndex;
        int configIndex = exp / TRIALS_PER_CONFIG;
        int nodeNb      = Network.size();

        // Retrieve current parameters for metrics calculation
        int leafsetSize   = Integer.parseInt(System.getProperty("simulation.leafsetSize", "4"));
        int maxNeighbours = Integer.parseInt(System.getProperty("simulation.maxNeighbours", "4"));
        long maxIDlogique = Long.parseLong(System.getProperty("simulation.maxIDlogique", "1000"));

        // 1. Compute metrics for this specific run
        Metrics currentRun = computeMetrics(nodeNb, leafsetSize, maxIDlogique, maxNeighbours);

        // 2. Accumulate values for averaging
        accumulator.completenessPercent += currentRun.completenessPercent;
        accumulator.correctnessPercent  += currentRun.correctnessPercent;
        accumulator.longLinkCoverage    += currentRun.longLinkCoverage;
        accumulator.avgHops             += currentRun.avgHops;

        // 3. If this is the LAST trial of the current configuration, finalize the average
        if ((exp + 1) % TRIALS_PER_CONFIG == 0) {
            accumulator.completenessPercent /= TRIALS_PER_CONFIG;
            accumulator.correctnessPercent  /= TRIALS_PER_CONFIG;
            accumulator.longLinkCoverage    /= TRIALS_PER_CONFIG;
            accumulator.avgHops             /= TRIALS_PER_CONFIG;

            String sweepName  = getSweepName(configIndex);
            String paramLabel = getParamLabel(configIndex, leafsetSize, maxNeighbours, (int)maxIDlogique);

            results.add(new ExperimentResult(sweepName, paramLabel, accumulator));

            // Reset for next config
            accumulator = new Metrics();
        }

        joinHopCounts.clear();

        // Print full table on the last experiment run
        if (exp == TOTAL_EXPERIMENTS - 1) {
            printResultsTable();
        }

        return false;
    }

    // ------------------------------------------------------------------ metrics logic

    private Metrics computeMetrics(int nodeNb, int leafsetSize, long maxIDlogique, int maxNeighbours) {
        Metrics m = new Metrics();

        long[] sortedIds = new long[nodeNb];
        DHTNode[] nodes  = new DHTNode[nodeNb];
        for (int i = 0; i < nodeNb; i++) {
            nodes[i]     = (DHTNode) Network.get(i).getProtocol(DHTPid);
            sortedIds[i] = nodes[i].getLogicalId();
        }
        Arrays.sort(sortedIds);

        int totalSlots = 0, correctSlots = 0, fullLS = 0, fullLL = 0;
        int half = leafsetSize / 2;

        for (DHTNode node : nodes) {
            List<Node> ls = node.getLeafset();
            if (ls.size() >= leafsetSize) fullLS++;
            if (node.getFurthestNodes().size() >= maxNeighbours) fullLL++;

            Set<Long> trueNeighbors = getTrueNeighbors(node.getLogicalId(), sortedIds, half);
            totalSlots += ls.size();
            for (Node n : ls) {
                long neighborLogicalId = ((DHTNode)n.getProtocol(DHTPid)).getLogicalId();
                if (trueNeighbors.contains(neighborLogicalId)) {
                    correctSlots++;
                }
            }
        }

        m.completenessPercent = 100.0 * fullLS / nodeNb;
        m.correctnessPercent  = totalSlots > 0 ? 100.0 * correctSlots / totalSlots : 0;
        m.longLinkCoverage    = 100.0 * fullLL / nodeNb;

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
        System.out.println("|                    BENCHMARK SUMMARY (AVERAGED OVER " + TRIALS_PER_CONFIG + " TRIALS)                 |");
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

    private String getSweepName(int configIndex) {
        if (configIndex < LEAFSET_SIZES.length) return "leafsetSize";
        if (configIndex < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length) return "maxNeighbours";
        return "maxIDlogique";
    }

    private String getParamLabel(int configIndex, int ls, int mn, int mid) {
        if (configIndex < LEAFSET_SIZES.length) return "ls=" + ls;
        if (configIndex < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length) return "mn=" + mn;
        return "mid=" + mid;
    }

    static class Metrics {
        double completenessPercent = 0, correctnessPercent = 0, longLinkCoverage = 0, avgHops = 0;
    }

    static class ExperimentResult {
        final String sweepName, paramLabel;
        final Metrics metrics;
        ExperimentResult(String s, String p, Metrics m) {
            sweepName = s; paramLabel = p; metrics = m;
        }
    }
}