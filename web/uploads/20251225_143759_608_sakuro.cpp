#include <bits/stdc++.h>
using namespace std;

int main() {
    ios:: sync_with_stdio(false);
    cin.tie(nullptr);
    
    int t;
    cin >> t;
    
    while (t--) {
        int n;
        cin >> n;
        
        vector<int> p(n + 1);
        for (int i = 1; i <= n; i++) {
            cin >> p[i];
        }
        
        string s;
        cin >> s;
        s = " " + s;  // 1-indexed
        
        vector<int> ans(n + 1, 0);
        vector<bool> visited(n + 1, false);
        
        for (int i = 1; i <= n; i++) {
            if (! visited[i]) {
                // 找出这个循环中的所有点
                vector<int> cycle;
                int cur = i;
                while (!visited[cur]) {
                    visited[cur] = true;
                    cycle.push_back(cur);
                    cur = p[cur];
                }
                
                // 统计循环中黑色整数的数量
                // s[j] == '0' 表示位置 j 是黑色
                int blackCount = 0;
                for (int j : cycle) {
                    if (s[j] == '0') {
                        blackCount++;
                    }
                }
                
                // 循环中所有点的答案相同
                for (int j : cycle) {
                    ans[j] = blackCount;
                }
            }
        }
        
        for (int i = 1; i <= n; i++) {
            cout << ans[i];
            if (i < n) cout << " ";
        }
        cout << "\n";
    }
    
    return 0;
}