/**
 * Small, hand-written fuzzy inference system for baggage urgency.
 *
 * Inputs (all in "crisp" units):
 *   - Minutes remaining until the flight loading cutoff
 *   - Estimated conveyor travel time to the assigned loading bay
 *   - Transfer urgency (LOW for outgoing bags, HIGH for transfer bags)
 *   - Processing / waiting delay in the queue
 *
 * Every input is mapped to LOW / MEDIUM / HIGH membership values. A compact
 * rule base then produces a score from 0 to 100. No external fuzzy library is
 * used; the maths is deliberately simple enough to explain in a viva.
 */
public class FuzzyUrgency {

    /** Immutable result of one fuzzy evaluation. */
    public static class Result {
        public final boolean eligible;
        public final int score;
        public final String label;
        public final String rule;
        public final int timeRemaining;
        public final int travelTime;
        public final int transferUrgency;
        public final int waitDelay;

        private Result(boolean eligible, int score, String label, String rule,
                       int timeRemaining, int travelTime, int transferUrgency, int waitDelay) {
            this.eligible = eligible;
            this.score = score;
            this.label = label;
            this.rule = rule;
            this.timeRemaining = timeRemaining;
            this.travelTime = travelTime;
            this.transferUrgency = transferUrgency;
            this.waitDelay = waitDelay;
        }

        public static Result ineligible(int timeRemaining, int travelTime, int transferUrgency, int waitDelay) {
            return new Result(false, 0, "N/A", "Not eligible for loading-cutoff urgency", 
                    timeRemaining, travelTime, transferUrgency, waitDelay);
        }
    }

    private static class Rule {
        final String description;
        final double strength;
        final int outputScore;

        Rule(String description, double strength, int outputScore) {
            this.description = description;
            this.strength = strength;
            this.outputScore = outputScore;
        }
    }

    /**
     * Calculates urgency for a baggage item. Incoming bags and bags without a
     * loading cutoff are not eligible, because they are not dispatched to a
     * departing flight bay.
     */
    public static Result evaluate(Baggage bag, int simulatedTime) {
        if (bag == null) {
            return Result.ineligible(0, 0, 0, 0);
        }

        int remaining = bag.getLoadingCutoff() > 0
                ? Math.max(0, bag.getLoadingCutoff() - simulatedTime)
                : 240;
        int travel = bag.getEstimatedTime() >= 0 ? bag.getEstimatedTime() : 60;
        int transfer = bag.isTransferBag() ? 90 : 35;
        int delay = bag.getQueuedSince() > 0
                ? Math.max(0, simulatedTime - bag.getQueuedSince())
                : 0;

        if (bag.isIncomingBag() || bag.getLoadingCutoff() <= 0 || isTerminal(bag.getStatus())) {
            return Result.ineligible(remaining, travel, transfer, delay);
        }

        // Membership values for each linguistic category.
        double remLow = low(remaining, 30, 60);
        double remMedium = triangle(remaining, 30, 60, 90);
        double remHigh = high(remaining, 60, 90);

        double travelLow = low(travel, 10, 20);
        double travelMedium = triangle(travel, 10, 20, 30);
        double travelHigh = high(travel, 20, 30);

        double transferLow = low(transfer, 25, 50);
        double transferHigh = high(transfer, 50, 75);

        double delayLow = low(delay, 10, 25);
        double delayMedium = triangle(delay, 10, 25, 40);
        double delayHigh = high(delay, 25, 40);

        Rule[] rules = new Rule[] {
            // Critical combinations: very little time left plus a long or delayed journey.
            new Rule("Remaining time LOW and travel time HIGH -> CRITICAL", min(remLow, travelHigh), 95),
            new Rule("Remaining time LOW and waiting delay HIGH -> CRITICAL", min(remLow, delayHigh), 95),
            new Rule("Remaining time LOW and transfer urgency HIGH -> CRITICAL", min(remLow, transferHigh), 92),

            // High combinations.
            new Rule("Remaining time LOW and travel time MEDIUM -> HIGH", min(remLow, travelMedium), 80),
            new Rule("Remaining time LOW and waiting delay MEDIUM -> HIGH", min(remLow, delayMedium), 75),
            new Rule("Remaining time LOW -> HIGH", remLow, 70),
            new Rule("Transfer urgency HIGH and remaining time MEDIUM -> HIGH", min(transferHigh, remMedium), 80),
            new Rule("Transfer urgency HIGH and travel time HIGH -> HIGH", min(transferHigh, travelHigh), 78),
            new Rule("Remaining time MEDIUM and travel time HIGH -> HIGH", min(remMedium, travelHigh), 75),
            new Rule("Remaining time MEDIUM and waiting delay HIGH -> HIGH", min(remMedium, delayHigh), 70),

            // Medium combinations.
            new Rule("Transfer urgency HIGH -> MEDIUM", transferHigh, 55),
            new Rule("Remaining time MEDIUM and travel time MEDIUM -> MEDIUM", min(remMedium, travelMedium), 55),
            new Rule("Waiting delay HIGH -> MEDIUM", delayHigh, 55),
            new Rule("Travel time HIGH -> MEDIUM", travelHigh, 55),
            new Rule("Remaining time MEDIUM -> MEDIUM", remMedium, 50),

            // Low combinations: plenty of time and an easy journey.
            new Rule("Remaining time HIGH and travel time LOW -> LOW", min(remHigh, travelLow), 15),
            new Rule("Remaining time HIGH and transfer urgency LOW -> LOW", min(remHigh, transferLow), 20),
            new Rule("Travel time LOW and waiting delay LOW -> LOW", min(travelLow, delayLow), 20),
            new Rule("Remaining time HIGH -> LOW", remHigh, 30),
            new Rule("Travel time LOW -> LOW", travelLow, 30)
        };

        // The strongest rule wins, but its score is blended towards neutral (50)
        // when its antecedent only partially fires. This keeps the score smooth.
        double bestScore = -1;
        Rule bestRule = null;
        for (Rule rule : rules) {
            if (rule.strength <= 0) continue;
            double blended = (rule.outputScore * rule.strength) + (50.0 * (1.0 - rule.strength));
            if (blended > bestScore) {
                bestScore = blended;
                bestRule = rule;
            }
        }

        if (bestRule == null) {
            bestScore = 50;
            bestRule = new Rule("No specific rule fired -> MEDIUM", 1, 50);
        }

        int score = (int) Math.round(Math.max(0, Math.min(100, bestScore)));
        String label;
        if (score >= 85) label = "CRITICAL";
        else if (score >= 65) label = "HIGH";
        else if (score >= 35) label = "MEDIUM";
        else label = "LOW";

        return new Result(true, score, label, bestRule.description,
                remaining, travel, transfer, delay);
    }

    private static boolean isTerminal(BaggageStatus status) {
        return status == BaggageStatus.LOADED
                || status == BaggageStatus.COLLECTED
                || status == BaggageStatus.MISSED_CUTOFF
                || status == BaggageStatus.REJECTED;
    }

    // Membership functions: low falls from 1 to 0, high rises from 0 to 1,
    // triangle peaks in the middle. All return values in [0, 1].
    private static double low(double x, double fullBelow, double zeroAbove) {
        if (x <= fullBelow) return 1.0;
        if (x >= zeroAbove) return 0.0;
        return (zeroAbove - x) / (zeroAbove - fullBelow);
    }

    private static double high(double x, double zeroBelow, double fullAbove) {
        if (x <= zeroBelow) return 0.0;
        if (x >= fullAbove) return 1.0;
        return (x - zeroBelow) / (fullAbove - zeroBelow);
    }

    private static double triangle(double x, double left, double peak, double right) {
        if (x <= left || x >= right) return 0.0;
        if (x == peak) return 1.0;
        if (x < peak) return (x - left) / (peak - left);
        return (right - x) / (right - peak);
    }

    private static double min(double a, double b) {
        return Math.min(a, b);
    }
}
