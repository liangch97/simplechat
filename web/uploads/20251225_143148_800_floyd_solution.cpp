#include <iostream>
#include <cstring>
using namespace std;

const int INF = 0x3f3f3f3f;
int dist[205][205];

int main() {
    int N, M;
    while (cin >> N >> M) {
        // 初始化距离矩阵
        memset(dist, 0x3f, sizeof(dist));
        for (int i = 0; i < N; i++) {
            dist[i][i] = 0;
        }
        
        for (int i = 0; i < M; i++) {
            int A, B, X;
            cin >> A >> B >> X;
            // 注意：可能有重边，取最小值
            if (X < dist[A][B]) {
                dist[A][B] = X;
                dist[B][A] = X;
            }
        }
        
        int S, T;
        cin >> S >> T;
        
        // Floyd 算法
        for (int k = 0; k < N; k++) {
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    if (dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                    }
                }
            }
        }
        
        if (dist[S][T] == INF) {
            cout << -1 << endl;
        } else {
            cout << dist[S][T] << endl;
        }
    }
    
    return 0;
}