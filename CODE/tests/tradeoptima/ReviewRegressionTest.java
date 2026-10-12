package tradeoptima;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ReviewRegressionTest {
    @Test void unlimitedTiesPreferFewerCompletedTrades() {
        long[] p = {1, 2, 2, 3};
        TradeOptimizer.Result r = TradeOptimizer.solve(p, 20, 0, 0);
        assertEquals(2, r.profit); assertEquals(1, r.trades.length);
        assertTrue(TradeOptimizer.validate(p, r, 20, 0, 0));
    }
    // Independent enumeration checks BOTH objective components, not only profit.
    private long bestProfit; private int bestCount;
    private void enumerate(long[] p, int day, int bought, int lastSell, int count, int k, int c, long fee, long profit) {
        if (day == p.length) {
            if (bought < 0 && (profit > bestProfit || (profit == bestProfit && count < bestCount))) {
                bestProfit = profit; bestCount = count;
            }
            return;
        }
        enumerate(p, day + 1, bought, lastSell, count, k, c, fee, profit);
        if (bought < 0 && count < k && day > lastSell + c)
            enumerate(p, day + 1, day, lastSell, count, k, c, fee, profit - p[day]);
        if (bought >= 0)
            enumerate(p, day + 1, -1, day, count + 1, k, c, fee, profit + p[day] - fee);
    }
    @Test void boundedAndUnlimitedMatchProfitAndTradeCountOracle() {
        Ds.Rng rng = new Ds.Rng(81);
        for (int t = 0; t < 1200; t++) {
            long[] p = new long[2 + rng.nextInt(8)];
            for (int i = 0; i < p.length; i++) p[i] = 1 + rng.nextInt(5);
            int k = rng.nextInt(7), c = rng.nextInt(3); long fee = rng.nextInt(3);
            bestProfit = Long.MIN_VALUE; bestCount = Integer.MAX_VALUE;
            enumerate(p, 0, -1, -1-c, 0, k, c, fee, 0);
            TradeOptimizer.Result r = TradeOptimizer.solve(p, k, fee, c);
            assertEquals(bestProfit, r.profit); assertEquals(bestCount, r.trades.length);
            assertTrue(TradeOptimizer.validate(p, r, k, fee, c));
        }
    }
    @Test void matchingHandlesLongAlternatingPathAndRepeatedRun() {
        int n = 30000;
        HopcroftKarp h = new HopcroftKarp(n, n, 2*n);
        for (int i = 0; i < n-1; i++) { h.addEdge(i, i+1); h.addEdge(i, i); }
        h.addEdge(n-1, 0);
        assertEquals(n, h.run()); assertEquals(n, h.run());
        boolean[] used = new boolean[n];
        for (int r : h.matchL) { assertTrue(r >= 0); assertFalse(used[r]); used[r] = true; }
    }
    private long bestFill, bestCost;
    private void allocate(OrderRouter.Venue[] v, OrderRouter.Order[] o, int unit, int[] owner, long[] left, long filled, long cost) {
        if (unit == owner.length) {
            if (filled > bestFill || (filled == bestFill && cost < bestCost)) { bestFill = filled; bestCost = cost; }
            return;
        }
        allocate(v, o, unit + 1, owner, left, filled, cost);
        for (int venue = 0; venue < v.length; venue++) {
            if (left[venue] == 0 || ((o[owner[unit]].allowedMask >>> venue) & 1L) == 0) continue;
            left[venue]--;
            allocate(v, o, unit + 1, owner, left, filled + 1, cost + v[venue].price[0] + v[venue].feePerShare);
            left[venue]++;
        }
    }
    @Test void restrictedRouterMatchesExhaustiveAllocation() {
        Ds.Rng rng = new Ds.Rng(12);
        for (int t = 0; t < 300; t++) {
            OrderRouter.Venue[] v = new OrderRouter.Venue[3];
            long[] left = new long[3];
            for (int i = 0; i < 3; i++) {
                left[i] = rng.nextInt(4);
                v[i] = new OrderRouter.Venue("V"+i, rng.nextInt(3), new long[] {1+rng.nextInt(12)}, new long[] {left[i]});
            }
            OrderRouter.Order[] o = new OrderRouter.Order[3];
            int[] owner = new int[6]; int units = 0;
            for (int i = 0; i < 3; i++) {
                int qty = 1+rng.nextInt(2);
                o[i] = new OrderRouter.Order(i, qty, rng.nextInt(8));
                for (int j = 0; j < qty; j++) owner[units++] = i;
            }
            int[] actualOwner = new int[units]; System.arraycopy(owner,0,actualOwner,0,units);
            bestFill = -1; bestCost = Long.MAX_VALUE;
            allocate(v,o,0,actualOwner,left,0,0);
            OrderRouter.Plan r = OrderRouter.route(v,o);
            assertEquals(bestFill,r.filledQty); assertEquals(bestCost,r.totalCost);
        }
    }
    @Test void badInputsFailClearly() {
        assertThrows(IllegalArgumentException.class, () -> new Ds.Rng(1).nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> new RiskEngine.Reservoir(0, new Ds.Rng(1)));
        assertThrows(IllegalArgumentException.class, () -> PortfolioSelector.greedy2Approx(
            new PortfolioSelector.Asset[] {new PortfolioSelector.Asset("bad", -1, 2)}, 10));
        assertThrows(IllegalArgumentException.class, () -> PortfolioSelector.fptas(
            new PortfolioSelector.Asset[] {new PortfolioSelector.Asset("huge", 1, 2)}, 10, 1e-20));
        assertThrows(IllegalArgumentException.class, () -> OrderRouter.route(new OrderRouter.Venue[65], new OrderRouter.Order[0]));
    }
    @Test void portfolioOracleDoesNotNarrowInfeasibleCosts() {
        PortfolioSelector.Asset[] a = {new PortfolioSelector.Asset("too expensive", (1L << 32) + 1, 100)};
        PortfolioSelector.Selection s = PortfolioSelector.exact(a, 10);
        assertEquals(0, s.value); assertEquals(0, s.chosen.length);
    }
    @Test void routerRejectsCostOverflowInsteadOfWrapping() {
        OrderRouter.Venue[] v = {new OrderRouter.Venue("large",0,new long[] {1_000_000_000L},new long[] {20_000_000_000L})};
        OrderRouter.Order[] o = {new OrderRouter.Order(1,20_000_000_000L,1)};
        assertThrows(ArithmeticException.class, () -> OrderRouter.route(v,o));
    }
    @Test void laboratoryUsesRealJavaAlgorithms() {
        String text = "AAPL and Apple", query = "AAPL";
        String packet = text.length()+"\n"+text+query.length()+"\n"+query+"0\n0\n";
        String result = GuiBridge.lab(packet);
        assertTrue(result.contains("KMP: [0]")); assertTrue(result.contains("Z-search: [0]"));
        assertTrue(result.contains("AAPL=2")); assertTrue(result.contains("SIM-B"));
        assertTrue(result.contains("FPTAS")); assertTrue(result.contains("GBM Monte Carlo"));
    }
}
