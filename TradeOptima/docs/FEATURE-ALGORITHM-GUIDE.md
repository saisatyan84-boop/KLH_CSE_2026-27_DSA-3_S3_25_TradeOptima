# TradeOptima: features, algorithms and trader use

## What this application does

TradeOptima is a historical market research and paper-allocation tool built for DSA-3. It connects five hand-built algorithm families. The PPT numbers them Module 2 through Module 6; there is no Module 1.

It answers five different questions:
1. Which companies are mentioned in a piece of news, and can I find a ticker despite a typo?
2. What was the maximum possible profit on these historical prices under a trade limit, fee and cooldown?
3. How can eligible venue liquidity fill orders at the smallest total cost?
4. Which candidates fit a budget while giving a high total supplied utility score?
5. What losses appear in simulations based on historical returns?

## Opening the application

Double-click `C:\COLLEGE\DSA-3\2nd path\TradeOptima\TradeOptima.exe`. Close an older running window and reopen the executable to load the updated UI. Keep the executable inside the project folder: it needs the Java source, scripts, UI and data folders. The launcher uses the project's JDK discovery helper; Java 17 or newer must be available. No terminal is needed for normal use.

### Stock analysis: your own stock

1. Download/export the stock's historical daily prices from a data provider into CSV.
2. Drop exactly one `.csv` file onto the large import area, or choose Browse files. Imports are limited to 32 MB and analysis to 100,000 observations.
3. Expand the preview to inspect the first five rows. This preview is only a file/header check. Java validates the complete selected series when you analyze it.
4. Select the price column in the sidebar. Use Close or adjusted close consistently across comparisons; Open, Volume and other numeric columns are not interchangeable with closing prices.
5. Choose the currency matching your file. USD/INR changes the displayed unit label; it performs no conversion.
6. Set maximum trades, fee per completed trade and cooldown. The model holds one share at a time. Cooldown counts dataset observations, normally trading days, rather than calendar days.
7. Select Analyze stock. Inspect the price chart, buy/sell markers, net profit, validated trade ledger and risk estimate. Scroll down to the ledger. Export ledger saves a CSV.
8. Rerun after changing settings. The banner identifies results that still reflect earlier settings.

Accepted example:
```csv
Date,Close
2024-01-01,100.00
2024-01-02,103.50
2024-01-03,101.25
2024-01-04,106.00
```
Dates must use YYYY-MM-DD and be strictly increasing. Prices must be positive numeric values. Java supports quoted CSV fields and rounds prices to integer cents. Sort a downloaded file from oldest to newest before importing. A one-stock import runs Modules 3 and 6; it does not create real news or venue data for that stock.

### Market replay

Select Market replay, configure the daily budget and past analysis window, then Load market replay. Play, Previous, Next and the slider let you inspect each observed day. This mode connects all five modules on the bundled four-stock dataset. Each snapshot uses only data available by its displayed day. The budget resets every day; there is no persistent trading account or cumulative strategy performance.

The historical prices are real archived data. The headlines and venue books are synthetic fixtures. Their outputs demonstrate the algorithms, not actual exchange execution or news-driven returns.

### Algorithm workspace

Enter a headline/text and a query, then select Explore algorithms. The Java engine runs KMP, Z-search, Aho-Corasick and fuzzy search on your input. The same page shows reproducible synthetic comparisons for min-cost flow versus whole-block matching, greedy versus FPTAS versus exact portfolio selection, and GBM versus bootstrap risk. Try AAPL, Apple and APPL to distinguish substring search, company aliases and typo tolerance. Only the search text/query is editable here; the comparison scenarios are fixed and labeled.

### Verification

Run correctness tests to execute the reference comparisons and integration checks. Run the benchmark to time N=100,000 and k=1,000 against the PPT's two-second target. Full JUnit checks, including the added review regressions, can also be run with `powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1` from the project folder.

## Exactly which feature calls which algorithm

| Feature | Algorithm / API | Where it is used | What it returns |
|---|---|---|---|
| Single-query text search | KMP: StringAlgorithms.kmpSearch | Algorithm workspace | All substring start positions, including overlapping matches |
| Alternative single-query search | Z-function: StringAlgorithms.zSearch | Algorithm workspace | The same substring positions through a different linear-time algorithm |
| News/company mention counts | Aho-Corasick + whole-word filtering: SearchEngine.mentions | Replay and algorithm workspace | Count for each tracked instrument, including ticker/company aliases |
| Typo-tolerant ticker lookup | Levenshtein edit-distance DP: SearchEngine.fuzzy | Algorithm workspace | Ticker/company suggestions ranked by edit distance, up to two edits |
| Best historical trades | State-machine DP: TradeOptimizer.solve | Stock analysis and each past-window replay analysis | Maximum net profit and chronological buy/sell ledger |
| Ledger checks | TradeOptimizer.validate | Stock analysis, tests and benchmark | Whether ordering, cooldown, trade cap, prices, fees and profit total agree |
| Split orders across price levels | Min-cost max-flow with SPFA: OrderRouter.route | Replay and algorithm workspace | Maximum filled quantity at minimum total quote-plus-fee cost |
| Whole block placement | Hopcroft-Karp: OrderRouter.matchBlocks | Algorithm workspace | Maximum number of compatible whole orders, one block per venue |
| Main budget selection | Value-scaling FPTAS: PortfolioSelector.fptas | Replay (epsilon=0.1) and algorithm workspace | Feasible subset with at least (1-epsilon) of optimal supplied utility |
| Fast budget alternative | Density greedy versus best single: PortfolioSelector.greedy2Approx | Algorithm workspace | Feasible subset with at least half optimal supplied utility |
| Small portfolio reference | Exact budget DP: PortfolioSelector.exact | Algorithm workspace and tests | Optimal utility for the stated small budget |
| Bounded historical sample | Reservoir Algorithm R: RiskEngine.Reservoir | Stock analysis and replay | At most 500 uniformly sampled observed returns or joint return-day indices |
| Historical loss simulation | Bootstrap Monte Carlo: RiskEngine.bootstrapVaR | Stock analysis, replay and algorithm workspace | VaR, CVaR and mean loss from resampled historical log returns |
| Alternative loss simulation | GBM/normal log-return Monte Carlo: RiskEngine.parametricVaR | Algorithm workspace | VaR/CVaR estimated from historical log-return mean and standard deviation |
| Fast simulated-loss quantile | Randomized three-way quickselect: RiskEngine.quickselect | Both risk simulation methods | VaR order statistic without sorting all paths |
| Random draws | xoshiro256**, rejection sampling, Box-Muller: Ds.Rng | Risk, reservoir and randomized tests | Reproducible uniform draws and Gaussian variates |

The fact that an algorithm is implemented does not mean every screen invokes it. The replay uses Aho-Corasick, constrained DP, min-cost flow, FPTAS, reservoir sampling, bootstrap and quickselect. The workspace exposes the alternative methods that replay does not need.

## Module 2: search

KMP uses a prefix table to avoid restarting at every text character: O(text length + query length) time. Z-search builds prefix-match lengths on query + separator + text, also O(text + query). Both are substring methods. The workspace folds ASCII case first, so AAPL and aapl match. Positions are zero based.

Aho-Corasick builds a trie with failure and dictionary links, then scans many aliases in one pass: O(text length + reported matches), after construction. SearchEngine keeps whole-word hits; Apple is not counted inside Pineapple. Aliases are ASCII, duplicate pattern strings keep their first ID, and boundaries treat letters/digits as word characters. Mention counts do not express positive/negative sentiment.

Edit distance counts insertions, deletions and substitutions using two DP rows. Time O(query length * alias length) per alias; memory O(alias length). APPL can therefore suggest AAPL without silently treating an exact search as fuzzy.

Trader value: organizing a watchlist and filtering relevant text quickly. A real-news workflow would require actual dated news input and appropriate company aliases; the current replay's news is synthetic.

## Module 3: the core optimizer

The flat and holding states encode whether one share is held. For day i and j buys used:
- HOLD[i,j] = max(HOLD[i-1,j], NOSTOCK[i-c-1,j-1] - price[i]).
- NOSTOCK[i,j] = max(NOSTOCK[i-1,j], HOLD[i-1,j] + price[i] - fee).

Reading a flat state from c+1 days earlier enforces cooldown without needing a separate stored cooldown state. The fee is deducted once on the sell. Decision bits reconstruct actual dates. On equal total profit the chosen result prefers fewer completed trades.

Bounded time is O(N*k), with O(k*(c+2)) value memory and O(N*k) decision bits. The two bit tables use about 25 MB at the target scale. When k cannot constrain the answer, the implementation switches to an O(N) optimizer. The recent review makes trade-count tie handling explicit in that path.

Trader value: a hindsight upper bound for evaluating a past strategy under the same constraints. A trader who achieved 40 while the DP oracle achieved 100 can study the gap, but could not necessarily have known those optimal dates in advance. Change the fee/cooldown to study transaction-cost sensitivity. Compare identical stock/date ranges and share assumptions. The DP output is not a forecast and does not implement a causal trading strategy backtest.

## Module 4: routing

The flow graph is source -> order -> eligible ask level -> sink. Order capacities encode demand, level capacities encode available shares, and costs include quote plus venue fee. Reverse residual edges let the solver undo earlier assignments when venue restrictions make a different allocation globally better. SPFA finds the next residual shortest path; this implementation maximizes fill first, then minimizes cost. SPFA has unfavorable worst-case behavior, so this is intended for moderate routing graphs.

Hopcroft-Karp constructs a bipartite graph between whole orders and eligible venues with enough liquidity. BFS layers and iterative augmenting paths find a maximum matching in O(E*sqrt(V)). It maximizes the number of whole blocks placed, not their price, share count or priority. One block per venue per round is the project's simplified model. Venue masks support at most 64 venues.

Trader value: understanding liquidity, fee-aware execution and venue eligibility. Current replay orders are one-share paper orders against synthetic books; it cannot send real exchange orders or estimate actual market impact, latency or queue position.

## Module 5: budget selection

This is 0/1 knapsack: choose candidates once while total cost stays within budget. A hand-built merge sort orders density for greedy selection; comparison with the best feasible single item yields the half-optimum guarantee for nonnegative utilities.

FPTAS scales values using K=epsilon*vmax/n and runs a minimum-cost DP over scaled utility. Its guarantee concerns the supplied utility score. Runtime is O(n^3/epsilon), value memory O(n^2/epsilon), and decision storage O(n^3/epsilon) bits in the worst case. Exact DP runs in O(n*budget-in-cents) and is used only as a small-input reference. Input validation and explicit table-size guards reject unsuitable inputs rather than attempting huge allocations.

In replay each asset means one share, cost reserves the largest fixture quote plus fee, and utility is past-window DP profit in cents + current headline mentions. This score is a teaching example. It ignores sentiment, expected return forecasts, diversification targets and risk constraints. The approximation guarantee does not guarantee investment profits.

Trader value: demonstrating how to prioritize a supplied candidate list under a spending limit. Real portfolio decisions would need a defensible scoring model and additional constraints. The current selector should be described as budget allocation, not a validated stock recommendation model.

## Module 6: risk

Returns are log(current price / previous price). Algorithm R stores a uniform fixed-size sample instead of retaining the entire stream. Replacement uses unbiased bounded random draws. Replay samples joint dates across assets so the fixed-share basket retains observed cross-asset co-movement.

Bootstrap repeatedly draws observed log returns. GBM simulation instead uses fitted log-return mean and standard deviation with Gaussian draws. The existing log-return mean is already a log drift; the code does not subtract half the variance a second time. Stock analysis uses 5,000 one-day bootstrap paths; replay uses 2,000. Both have a 95% confidence setting and deterministic seeds for repeatable demonstrations.

Quickselect partitions simulated losses around randomized pivots in expected O(paths) time; worst-case O(paths squared). VaR uses the empirical nearest-rank quantile. CVaR is the project's discrete tail average from that rank through the largest loss, including the quantile observation. It can differ slightly from definitions that interpolate to exactly 5% tail mass. Positive loss means a loss; a negative value means a gain in that simulated outcome. Fees are excluded from these risk calculations. At least three prices (two returns) are required, although that is only a computational minimum, not adequate evidence for a dependable estimate.

Trader value: comparing possible loss magnitude across the same stock/date/settings and considering tail losses alongside historical opportunity. For example a one-share VaR of 3 and CVaR of 5 means the modeled 95th-percentile loss is around 3, while the selected worst tail averages around 5. Losses can exceed VaR; these estimates depend on history and modeling assumptions.

References for interpretation: [SEC investor bulletin on performance claims](https://www.investor.gov/introduction-investing/general-resources/news-alerts/alerts-bulletins/investor-bulletins-47) explains that historical performance does not necessarily predict future results. [Reserve Bank of Australia: Value-at-Risk](https://www.rba.gov.au/publications/rdp/1997/9708/value-at-risk.html) explains confidence levels and losses beyond the VaR threshold.

## A useful trader workflow today

Import a consistently adjusted history for one stock, run a baseline analysis, record the oracle profit and risk, then rerun with higher fees or stricter cooldown. Compare results over identical date ranges. Inspect the ledger to understand which historical moves drove the optimum. Export the ledger for study. Use replay to understand how only observed data feeds search, budget selection and paper routing; use the algorithm workspace to compare methods.

The real problem it helps explore is the combination of constrained trade sequencing, limited capital, fragmented liquidity and uncertain losses. It supports historical research and algorithm demonstrations today. Turning it into a real trader decision system would require real news/books, validated forward-looking utility, a causal strategy with out-of-sample evaluation, persistent holdings/cash accounting and realistic execution costs. Those capabilities are not currently implemented.

## Suggested viva demonstration

Open Stock analysis and explain the CSV, constraints and ledger. Show that higher fees can reduce achievable profit. Open replay and identify Modules 2, 3, 5, 4 and 6 in its output. Open the algorithm workspace, try APPL and explain why fuzzy lookup finds AAPL while exact substring search can return no hit. Discuss why whole-block matching and min-cost routing solve different objectives. Run Verification and the benchmark. Say: all five module families are implemented; historical prices are real, news/books are synthetic, and optimizer profits are hindsight upper bounds.
