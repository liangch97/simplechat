#include <iostream>
using namespace std;

typedef long long ll;

// 获取节点x的父节点
ll getParent(ll x, ll k) {
    if (x == 1) return 1;
    return (x - 2) / k + 1;
}

// 获取节点x的深度（根节点深度为0）
ll getDepth(ll x, ll k) {
    ll depth = 0;
    while (x > 1) {
        x = getParent(x, k);
        depth++;
    }
    return depth;
}

// 将节点x向上移动steps步
ll moveUp(ll x, ll k, ll steps) {
    while (steps > 0 && x > 1) {
        x = getParent(x, k);
        steps--;
    }
    return x;
}

ll lca(ll k, ll x, ll y) {
    // 获取两个节点的深度
    ll dx = getDepth(x, k);
    ll dy = getDepth(y, k);
    
    // 将较深的节点向上移动，使两者在同一深度
    if (dx > dy) {
        x = moveUp(x, k, dx - dy);
    } else {
        y = moveUp(y, k, dy - dx);
    }
    
    // 两者同时向上移动，直到相遇
    while (x != y) {
        x = getParent(x, k);
        y = getParent(y, k);
    }
    
    return x;
}

int main() {
    ios:: sync_with_stdio(false);
    cin.tie(nullptr);
    
    ll k, x, y;
    while (cin >> k >> x >> y) {
        if (k == 0 && x == 0 && y == 0) break;
        cout << lca(k, x, y) << "\n";
    }
    
    return 0;
}