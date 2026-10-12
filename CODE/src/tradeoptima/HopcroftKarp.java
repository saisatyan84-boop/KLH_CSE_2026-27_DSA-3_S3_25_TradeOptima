package tradeoptima;

/** Maximum bipartite matching in O(E*sqrt(V)); iterative layered augmenting paths. */
public final class HopcroftKarp {
    private final int nl, nr;
    private final int[] head, nxt, to, dist, cursor, queue, stack, via;
    private int m, shortest;
    public final int[] matchL, matchR;

    public HopcroftKarp(int left, int right, int maxEdges) {
        if (left < 0 || right < 0 || maxEdges < 0) throw new IllegalArgumentException("Negative graph size");
        nl = left; nr = right;
        head = new int[nl]; nxt = new int[maxEdges]; to = new int[maxEdges];
        dist = new int[nl]; cursor = new int[nl]; queue = new int[nl];
        stack = new int[nl]; via = new int[nl];
        matchL = new int[nl]; matchR = new int[nr];
        for (int i = 0; i < nl; i++) { head[i] = -1; matchL[i] = -1; }
        for (int i = 0; i < nr; i++) matchR[i] = -1;
    }

    public void addEdge(int l, int r) {
        if (l < 0 || l >= nl || r < 0 || r >= nr || m == to.length)
            throw new IllegalArgumentException("Invalid edge or edge capacity exhausted");
        to[m] = r; nxt[m] = head[l]; head[l] = m++;
    }

    private boolean bfs() {
        int qh = 0, qt = 0;
        shortest = Integer.MAX_VALUE;
        for (int u = 0; u < nl; u++) {
            cursor[u] = head[u];
            dist[u] = matchL[u] == -1 ? 0 : -1;
            if (dist[u] == 0) queue[qt++] = u;
        }
        while (qh < qt) {
            int u = queue[qh++];
            if (dist[u] >= shortest) continue;
            for (int e = head[u]; e != -1; e = nxt[e]) {
                int w = matchR[to[e]];
                if (w == -1) shortest = dist[u] + 1;
                else if (dist[w] == -1) { dist[w] = dist[u] + 1; queue[qt++] = w; }
            }
        }
        return shortest != Integer.MAX_VALUE;
    }

    private boolean dfs(int root) {
        int depth = 0;
        stack[0] = root;
        while (depth >= 0) {
            int u = stack[depth], e = cursor[u];
            if (e == -1) { dist[u] = -1; depth--; continue; }
            cursor[u] = nxt[e];
            int r = to[e], w = matchR[r];
            if (w == -1 && dist[u] + 1 == shortest) {
                matchL[u] = r; matchR[r] = u;
                while (depth > 0) {
                    r = via[depth--]; u = stack[depth];
                    matchL[u] = r; matchR[r] = u;
                }
                return true;
            }
            if (w != -1 && dist[w] == dist[u] + 1 && dist[w] < shortest) {
                via[++depth] = r; stack[depth] = w;
            }
        }
        return false;
    }

    /** Returns total matching size, including matches from an earlier run. */
    public int run() {
        int size = 0;
        for (int r : matchL) if (r != -1) size++;
        while (bfs()) for (int u = 0; u < nl; u++) if (matchL[u] == -1 && dfs(u)) size++;
        return size;
    }
}
