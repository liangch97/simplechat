#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    
    int a[1005];
    for (int i = 0; i < n; i++) {
        cin >> a[i];
    }
    
    // 进行 n-1 轮选择排序
    for (int i = 0; i < n - 1; i++) {
        // 找到 a[i... n-1] 中最小元素的索引
        int minIdx = i;
        for (int j = i + 1; j < n; j++) {
            if (a[j] < a[minIdx]) {
                minIdx = j;
            }
        }
        
        // 如果最小元素不是 a[i]，则交换
        if (minIdx != i) {
            int temp = a[i];
            a[i] = a[minIdx];
            a[minIdx] = temp;
        }
        
        // 输出当前轮次排序后的数组
        for (int j = 0; j < n; j++) {
            if (j > 0) cout << " ";
            cout << a[j];
        }
        cout << endl;
    }
    
    return 0;
}