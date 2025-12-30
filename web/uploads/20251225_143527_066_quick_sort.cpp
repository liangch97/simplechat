#include <iostream>
using namespace std;

int a[1005], n;

void quickSort(int left, int right) {
    if (left >= right) return;
    
    int pivot = a[left];
    int i = left, j = right;
    
    while (i < j) {
        while (i < j && a[j] >= pivot) j--;
        while (i < j && a[i] <= pivot) i++;
        if (i < j) swap(a[i], a[j]);
    }
    swap(a[left], a[i]);
    
    quickSort(left, i - 1);
    quickSort(i + 1, right);
}

int main() {
    cin >> n;
    for (int i = 0; i < n; i++) {
        cin >> a[i];
    }
    
    quickSort(0, n - 1);
    
    for (int i = 0; i < n; i++) {
        if (i > 0) cout << " ";
        cout << a[i];
    }
    cout << endl;
    return 0;
}