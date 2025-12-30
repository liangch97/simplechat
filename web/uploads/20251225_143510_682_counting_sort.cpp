#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    int a[1005];
    int cnt[1000005] = {0};  // 计数数组
    int maxVal = 0;
    
    for (int i = 0; i < n; i++) {
        cin >> a[i];
        cnt[a[i]]++;
        if (a[i] > maxVal) maxVal = a[i];
    }
    
    // 按计数输出
    int idx = 0;
    for (int i = 0; i <= maxVal; i++) {
        while (cnt[i]-- > 0) {
            a[idx++] = i;
        }
    }
    
    for (int i = 0; i < n; i++) {
        if (i > 0) cout << " ";
        cout << a[i];
    }
    cout << endl;
    return 0;
}