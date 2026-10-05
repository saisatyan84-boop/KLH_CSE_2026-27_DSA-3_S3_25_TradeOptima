# Algorithm review against the problem-statement PPT

Reviewed 2026-10-04 against `TradeOptima_Problem_Statement_Review (2).pptx`, particularly Proposed Implementations, Constraints and Expected Outcome. The PPT contains five subsystems numbered 2 through 6.

## Assessment

All named algorithm families are implemented by hand and their tested behavior agrees with independent references within the documented input domain. This is an implementation and test review, not a formal proof of correctness for every possible input. The core DP is a hindsight oracle, and news/order-book fixtures remain synthetic.

| PPT requirement | Implementation reviewed | Evidence |
|---|---|---|
| KMP, Z-function, Aho-Corasick | StringAlgorithms, AhoCorasick, SearchEngine | Randomized naive comparisons; whole-word/fuzzy checks; interactive Java workspace |
| Constrained state-machine DP and ledger | TradeOptimizer | 3,000 random profit comparisons against brute force; 1,200 additional exhaustive profit-and-trade-count cases; boundary/cooldown/ledger checks |
| Min-cost max-flow and bipartite matching | MinCostMaxFlow, HopcroftKarp, OrderRouter | Greedy price-sweep checks; brute-force matching; restricted residual-routing checks; 300 exhaustive restricted-allocation cases; long alternating path test |
| Budget approximation with guarantee | PortfolioSelector | Greedy >= OPT/2 and FPTAS >= (1-epsilon)*OPT checked against exact DP; invalid inputs and table limits checked |
| Monte Carlo VaR and reservoir sampling | RiskEngine, Ds.Rng | Quickselect reference checks, reservoir inclusion-frequency checks, risk sanity/log-drift tests; both simulation models exposed in the workspace |
| Independent subsystem tests | Separate JUnit classes + SelfTest + IntegrationTest | All 23 JUnit tests pass; GUI bridge and replay integration pass |
| N=100,000, k=1,000 under two seconds | Benchmark | 495.7 ms in the review run, fee 0.50 and cooldown 1; 1,000 trades with legal ledger |
| Source restriction | build.ps1 checks application source | No prohibited imports, collection utilities, regex splitting or formatting helpers; JUnit remains in separate tests |

## Improvements made

1. **Hopcroft-Karp:** replaced recursive DFS with explicit primitive-array stacks and shortest-path layering. Per-phase edge cursors avoid repeated edge scans. Repeated run() returns total matching size. A 30,000-node chain forces a long alternating augmentation and verifies unique right-side matches.
2. **Random draws/reservoir:** replaced modulo-only bounded draws with rejection sampling to remove mathematical modulo bias. Reservoir constructor/values and random bounds fail clearly on invalid input. Existing seeded demonstrations remain reproducible with the updated generator.
3. **Optimizer ties:** made completed-trade counts explicit in the unlimited path and tested the lexicographic objective: maximize profit, then minimize completed trades. This strengthens the documented contract; the review did not reproduce an incorrect original profit result.
4. **Portfolio domain and size checks:** enforce nonnegative costs/utility and supported budgets, catch utility overflow and guard scaled-value/exact tables before allocation. Fixed the exact oracle's cast of a large infeasible asset cost by checking it against budget before narrowing to int.
5. **Routing input and arithmetic checks:** enforce matching price/size arrays, positive orders, nonnegative quotes/fees/liquidity and at most 64 venues. Checked total flow/cost arithmetic rejects overflow instead of wrapping.
6. **Visible algorithm coverage:** added an Algorithm workspace calling the Java methods directly. KMP/Z/fuzzy lookup, block matching, greedy/exact selection and parametric risk were implemented before this review, but were not all used by the replay screen. Their placement is now visible and documented.

## GUI verification

XAML loads 70 named controls. The GUI smoke mode exercises actual WPF drag/drop event handlers, verifies a five-row CSV preview, rejects multi-file drops, runs asynchronous stock/lab/replay jobs and renders all four pages. Stock/lab/replay renders were visually inspected. This is programmatic application testing; an Explorer mouse drag was not manually performed. UI renders are in reports/gui-history.png, gui-lab.png, gui-replay.png and gui-verify.png.

The separate adapter check validates imported CSV, missing-column errors, all 2,306 replay days, expected first/last paper costs, and risk availability. Existing pipeline outputs remain consistent. Native launcher build and Javadoc generation are checked separately.

## Remaining limits to state in a viva

- KMP/Z are substring search; Aho-Corasick mention counts use whole-word filtering. ASCII case folding and first-ID duplicate alias behavior remain documented.
- DP holds at most one share and assumes known historical prices. No causal trading strategy, short selling or predictive model is implemented.
- Min-cost flow maximizes fill before minimizing cost; it does not impose order/time priority. SPFA has poor worst-case performance on unsuitable large graphs. Direct negative-cycle flow graphs are outside this router's model.
- Whole blocks use one block per venue per round. Maximum matching does not guarantee cheapest block execution.
- FPTAS guarantees the supplied utility score, not future investment return; large n/small epsilon is rejected when the table would be too large.
- CVaR uses the discrete tail beginning at the nearest-rank VaR observation. Monte Carlo is an estimate; losses can exceed VaR.
- Replay uses independent daily paper baskets, real archived prices and synthetic news/books. There is no broker execution, live-data connection or persistent account.

Run commands from the project folder: test.ps1 for JUnit, run.ps1 -Mode benchmark for performance, tools/test-gui-bridge.ps1 for adapter integration, gui.ps1 -SmokeTest for interface checks, and docs.ps1 for Javadoc.
