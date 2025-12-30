#include <iostream>
#include <vector>
#include <queue>
#include <cstring>
using namespace std;

const int INF = 0x3f3f3f3f;

int main() {
    int N, M;
    while (cin >> N >> M) {
        // 邻接表存储图
        vector<vector<pair<int, int>>> graph(N);
        
        for (int i = 0; i < M; i++) {
            int A, B, X;
            cin >> A >> B >> X;
            graph[A].push_back({B, X});
            graph[B]. push_back({A, X});
        }
        
        int S, T;
        cin >> S >> T;
        
        // Dijkstra 算法
        vector<int> dist(N, INF);
        priority_queue<pair<int, int>, vector<pair<int, int>>, greater<pair<int, int>>> pq;
        
        dist[S] = 0;
        pq.push({0, S});
        
        while (! pq.empty()) {
            auto [d, u] = pq.top();
            pq. pop();
            
            if (d > dist[u]) continue;
            
            for (auto [v, w] : graph[u]) {
                if (dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    pq.push({dist[v], v});
                }
            }
        }
        
        if (dist[T] == INF) {
            cout << -1 << endl;
        } else {
            cout << dist[T] << endl;
        }
    }
    
    return 0;
}