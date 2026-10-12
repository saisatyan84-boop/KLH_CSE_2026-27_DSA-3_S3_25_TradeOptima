package tradeoptima;

/** Structured output adapter for the Windows interface. Algorithms stay in their original classes. */
public final class GuiBridge {
    private GuiBridge() {}

    public static void main(String[] args) {
        try {
            if (args.length == 0) throw new IllegalArgumentException("Missing GUI action");
            String input = Main.readInput();
            if (args[0].equals("history")) System.out.print(history(input, args[1], Integer.parseInt(args[2]), CsvLoader.cents(args[3]), Integer.parseInt(args[4])));
            else if (args[0].equals("replay")) System.out.print(replay(input, CsvLoader.cents(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), CsvLoader.cents(args[4]), Integer.parseInt(args[5])));
            else if (args[0].equals("lab")) System.out.print(lab(input));
            else throw new IllegalArgumentException("Unknown GUI action");
        } catch (Exception ex) { System.err.println(ex.getMessage()); System.exit(1); }
    }

    static String history(String csv, String column, int k, long fee, int cooldown) {
        if (k < 0 || k > 1000 || fee < 0 || fee > 1_000_000_000L || cooldown < 0 || cooldown > 1000)
            throw new IllegalArgumentException("Trades and cooldown must be between 0 and 1000; fee must be nonnegative and within the supported range.");
        CsvLoader.Prices p = CsvLoader.prices(csv, column);
        if (p.close.length > 100_000) throw new IllegalArgumentException("At most 100000 observations");
        long start = System.nanoTime();
        TradeOptimizer.Result result = TradeOptimizer.solve(p.close, k, fee, cooldown);
        double ms = (System.nanoTime() - start) / 1e6;
        boolean legal = TradeOptimizer.validate(p.close, result, k, fee, cooldown);
        if (!legal) throw new IllegalStateException("Trade ledger did not pass validation");
        StringBuilder out = new StringBuilder("{\"kind\":\"history\",\"column\":");
        quote(out, column);
        out.append(",\"profit\":").append(result.profit).append(",\"durationMs\":").append(Ds.fix(ms, 3));
        out.append(",\"legal\":true,\"dates\":"); strings(out, p.dates);
        out.append(",\"prices\":"); numbers(out, p.close);
        out.append(",\"trades\":[");
        for (int i = 0; i < result.trades.length; i++) {
            if (i > 0) out.append(',');
            TradeOptimizer.Trade t = result.trades[i];
            out.append("{\"buyDay\":").append(t.buyDay).append(",\"sellDay\":").append(t.sellDay);
            out.append(",\"buyDate\":"); quote(out, p.dates[t.buyDay]);
            out.append(",\"sellDate\":"); quote(out, p.dates[t.sellDay]);
            out.append(",\"buyPrice\":").append(t.buyPrice).append(",\"sellPrice\":").append(t.sellPrice).append(",\"profit\":").append(t.profit).append('}');
        }
        out.append("],\"risk\":");
        RiskEngine.Risk risk = null;
        if (p.close.length >= 3) {
            RiskEngine.Reservoir sample = new RiskEngine.Reservoir(500, new Ds.Rng(7));
            for (double r : RiskEngine.logReturns(p.close)) sample.add(r);
            risk = RiskEngine.bootstrapVaR(sample.sample(), p.close[p.close.length - 1] / 100.0, 1, 5000, 0.95, new Ds.Rng(17));
        }
        risk(out, risk); return out.append('}').toString();
    }

    static String replay(String packet, long budget, int window, int k, long fee, int cooldown) {
        String[] sections = Main.unpack(packet);
        MarketData data = new MarketData(sections[0], sections[1], sections[2], sections[3]);
        Pipeline pipeline = new Pipeline(data, budget, window, k, fee, cooldown, 2000);
        StringBuilder out = new StringBuilder("{\"kind\":\"replay\",\"tickers\":"); strings(out, data.tickers);
        out.append(",\"dates\":"); strings(out, data.dates);
        out.append(",\"days\":[");
        for (int day = 0; day < data.dates.length; day++) {
            if (day > 0) out.append(',');
            Pipeline.Snapshot s = pipeline.next();
            out.append("{\"date\":"); quote(out, data.dates[day]);
            out.append(",\"newsCount\":").append(s.newsCount).append(",\"spent\":").append(s.spent).append(",\"value\":").append(s.value);
            out.append(",\"reserved\":").append(s.selection.cost).append(",\"utility\":").append(s.selection.value).append(",\"returnsSeen\":").append(s.returnsSeen);
            out.append(",\"assets\":[");
            for (int a = 0; a < data.tickers.length; a++) {
                if (a > 0) out.append(',');
                boolean chosen = false;
                for (int x : s.selection.chosen) if (x == a) chosen = true;
                out.append("{\"ticker\":"); quote(out, data.tickers[a]);
                out.append(",\"price\":").append(data.close[a][day]).append(",\"mentions\":").append(s.mentions[a]);
                out.append(",\"profit\":").append(s.analysis[a].profit).append(",\"selected\":").append(chosen).append('}');
            }
            out.append("],\"fills\":["); int count = 0;
            for (int i = 0; i < s.routes.length; i++) {
                int a = s.selection.chosen[i]; OrderRouter.Venue[] venues = data.venues(a, day);
                for (OrderRouter.Fill f : s.routes[i].fills) {
                    if (count++ > 0) out.append(',');
                    out.append("{\"ticker\":"); quote(out, data.tickers[a]);
                    out.append(",\"venue\":"); quote(out, venues[f.venue].name);
                    out.append(",\"quantity\":").append(f.qty).append(",\"unitCost\":").append(f.unitCost).append('}');
                }
            }
            out.append("],\"risk\":"); risk(out, s.risk); out.append('}');
        }
        return out.append("]}").toString();
    }

    /** Interactive search plus small, reproducible scenarios for the alternative algorithms. */
    static String lab(String packet) {
        String[] parts = Main.unpack(packet);
        String text = parts[0], query = parts[1];
        if (text.length() > 10000 || query.length() < 1 || query.length() > 100)
            throw new IllegalArgumentException("Use 1-100 query characters and at most 10000 text characters");
        String[] tickers = {"AAPL", "MSFT", "IBM", "SBUX"};
        String[] names = {"Apple", "Microsoft", "IBM", "Starbucks"};
        SearchEngine search = new SearchEngine(tickers, names);
        int[] kmp = StringAlgorithms.kmpSearch(StringAlgorithms.lower(text), StringAlgorithms.lower(query));
        int[] z = StringAlgorithms.zSearch(StringAlgorithms.lower(text), StringAlgorithms.lower(query));
        StringBuilder report = new StringBuilder("MODULE 2 / SEARCH\nCase-insensitive substring positions (zero based)\nKMP: ");
        indices(report, kmp); report.append("\nZ-search: "); indices(report, z);
        report.append("\nAho-Corasick whole-word mentions: ");
        int[] mentions = search.mentions(text);
        for (int i = 0; i < tickers.length; i++) report.append(tickers[i]).append('=').append(mentions[i]).append("  ");
        report.append("\nEdit-distance suggestions (up to 2 edits): ");
        SearchEngine.Hit[] hits = search.fuzzy(query, 2);
        for (SearchEngine.Hit h : hits) report.append(h.ticker).append(" (distance ").append(h.distance).append(") ");
        if (hits.length == 0) report.append("none");

        OrderRouter.Venue[] venues = {
            new OrderRouter.Venue("SIM-A", 1, new long[] {10000}, new long[] {4}),
            new OrderRouter.Venue("SIM-B", 2, new long[] {9990}, new long[] {3})
        };
        OrderRouter.Order[] orders = {new OrderRouter.Order(1, 3, 3), new OrderRouter.Order(2, 4, 1)};
        OrderRouter.Plan plan = OrderRouter.route(venues, orders);
        int[] blocks = OrderRouter.matchBlocks(venues, orders);
        report.append("\n\nMODULE 4 / SYNTHETIC ROUTING\nSIM-A: 4 shares at 100.00 + 0.01 fee; SIM-B: 3 at 99.90 + 0.02 fee\nOrder 1: 3 shares, either venue. Order 2: 4 shares, SIM-A only.\nMin-cost max-flow: ").append(plan.filledQty).append(" shares; total ").append(Ds.money(plan.totalCost));
        report.append("\nHopcroft-Karp whole-block assignments (one block per venue):\n");
        for (int i = 0; i < blocks.length; i++) report.append("Order ").append(i + 1).append(" -> ").append(blocks[i] < 0 ? "unmatched" : venues[blocks[i]].name).append('\n');
        report.append("Matching maximizes placed blocks; it does not optimize their price.");

        PortfolioSelector.Asset[] assets = {
            new PortfolioSelector.Asset("AAPL", 10000, 50), new PortfolioSelector.Asset("MSFT", 6000, 35),
            new PortfolioSelector.Asset("IBM", 8000, 40), new PortfolioSelector.Asset("SBUX", 4000, 25)
        };
        report.append("\n\nMODULE 5 / SYNTHETIC BUDGET SCENARIO\nBudget 200.00; costs AAPL=100, MSFT=60, IBM=80, SBUX=40\nUtilities: 50, 35, 40, 25 (supplied scores, not expected returns)\n");
        selection(report, "Greedy + best single (at least OPT/2)", assets, PortfolioSelector.greedy2Approx(assets, 20000));
        selection(report, "FPTAS epsilon=0.1 (at least 90% of OPT)", assets, PortfolioSelector.fptas(assets, 20000, 0.1));
        selection(report, "Exact budget DP reference", assets, PortfolioSelector.exact(assets, 20000));

        double[] returns = {-0.02, 0.01, 0.005, -0.01, 0.015, -0.03, 0.02, 0.001};
        RiskEngine.Risk gbm = RiskEngine.parametricVaR(returns, 1000, 1, 5000, 0.95, new Ds.Rng(17));
        RiskEngine.Risk boot = RiskEngine.bootstrapVaR(returns, 1000, 1, 5000, 0.95, new Ds.Rng(17));
        report.append("\nMODULE 6 / SYNTHETIC RISK COMPARISON\nSame eight log returns; basket value 1000; 1-day horizon; 5000 paths\n");
        report.append("GBM Monte Carlo: 95% VaR ").append(Ds.fix(gbm.var, 2)).append("; CVaR ").append(Ds.fix(gbm.cvar, 2));
        report.append("\nHistorical bootstrap: 95% VaR ").append(Ds.fix(boot.var, 2)).append("; CVaR ").append(Ds.fix(boot.cvar, 2));
        report.append("\nBoth use randomized quickselect. The stock analysis and replay also use reservoir sampling.\n\nMODULE 3 / CORE DP\nUse Stock analysis to run the constrained optimizer on your imported prices and inspect its validated ledger.");
        StringBuilder out = new StringBuilder("{\"kind\":\"lab\",\"text\":");
        quote(out, report.toString()); return out.append('}').toString();
    }

    private static void indices(StringBuilder out, int[] positions) {
        out.append('[');
        for (int i = 0; i < positions.length; i++) { if (i > 0) out.append(", "); out.append(positions[i]); }
        out.append(']');
    }
    private static void selection(StringBuilder out, String label, PortfolioSelector.Asset[] assets, PortfolioSelector.Selection s) {
        out.append(label).append(": ");
        for (int i : s.chosen) out.append(assets[i].name).append(' ');
        out.append("| cost ").append(Ds.money(s.cost)).append(" | utility ").append(s.value).append('\n');
    }

    private static void risk(StringBuilder out, RiskEngine.Risk risk) {
        if (risk == null) { out.append("null"); return; }
        out.append("{\"var\":").append(Ds.fix(risk.var, 6)).append(",\"cvar\":").append(Ds.fix(risk.cvar, 6));
        out.append(",\"paths\":").append(risk.paths).append('}');
    }
    private static void numbers(StringBuilder out, long[] values) {
        out.append('[');
        for (int i = 0; i < values.length; i++) { if (i > 0) out.append(','); out.append(values[i]); }
        out.append(']');
    }
    private static void strings(StringBuilder out, String[] values) {
        out.append('[');
        for (int i = 0; i < values.length; i++) { if (i > 0) out.append(','); quote(out, values[i]); }
        out.append(']');
    }
    /** JSON string escaping without a serialization library. */
    static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) {
                out.append("\\u00"); String hex = "0123456789abcdef";
                out.append(hex.charAt(c / 16)).append(hex.charAt(c % 16));
            } else out.append(c);
        }
        out.append('"');
    }
}
