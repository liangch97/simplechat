# Java 集合类

`java.util` 包

## 📌 概述

Java 集合框架提供了一套用于存储和操作数据的类和接口。在聊天室项目中，我们用集合来存储：
- 连接的客户端列表
- 消息历史
- MIME 类型映射

## 📊 集合框架结构

```
                    Collection (接口)
                         │
          ┌──────────────┼──────────────┐
          │              │              │
        List           Set           Queue
     (有序可重复)    (无序不重复)     (队列)
          │              │              │
    ┌─────┴─────┐   ┌────┴────┐        │
ArrayList  LinkedList  HashSet  TreeSet  LinkedList
    
                    Map (接口)
                      │
           ┌──────────┴──────────┐
        HashMap              TreeMap
      (无序)                (有序)
```

## 📋 List - 列表

有序集合，可以有重复元素。

### ArrayList（最常用）

基于数组实现，随机访问快。

```java
import java.util.ArrayList;
import java.util.List;

// 创建
List<String> list = new ArrayList<>();

// 添加
list.add("张三");
list.add("李四");
list.add("王五");

// 获取
String first = list.get(0);  // "张三"
int size = list.size();       // 3

// 遍历
for (String name : list) {
    System.out.println(name);
}

// 或用 forEach
list.forEach(name -> System.out.println(name));

// 删除
list.remove("李四");
list.remove(0);  // 按索引删除

// 检查
boolean has = list.contains("张三");

// 清空
list.clear();
```

### LinkedList

基于链表实现，插入删除快。

```java
import java.util.LinkedList;

LinkedList<String> list = new LinkedList<>();

// 特有方法
list.addFirst("头部");
list.addLast("尾部");
String first = list.getFirst();
String last = list.getLast();
list.removeFirst();
list.removeLast();
```

### 线程安全版本

```java
import java.util.concurrent.CopyOnWriteArrayList;

// 线程安全的 ArrayList
List<String> safeList = new CopyOnWriteArrayList<>();
```

## 🗺️ Map - 映射

键值对集合，键不能重复。

### HashMap（最常用）

```java
import java.util.HashMap;
import java.util.Map;

// 创建
Map<String, Integer> map = new HashMap<>();

// 添加
map.put("张三", 90);
map.put("李四", 85);
map.put("王五", 95);

// 获取
int score = map.get("张三");  // 90
int unknown = map.getOrDefault("赵六", 0);  // 不存在返回默认值

// 大小
int size = map.size();  // 3

// 检查
boolean hasKey = map.containsKey("张三");
boolean hasValue = map.containsValue(90);

// 遍历键
for (String key : map.keySet()) {
    System.out.println(key + ": " + map.get(key));
}

// 遍历键值对
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}

// forEach
map.forEach((key, value) -> {
    System.out.println(key + ": " + value);
});

// 删除
map.remove("李四");

// 清空
map.clear();
```

### 静态初始化（Java 9+）

```java
// 不可变 Map
Map<String, String> mimeTypes = Map.of(
    ".html", "text/html",
    ".css", "text/css",
    ".js", "application/javascript"
);
```

### 线程安全版本

```java
import java.util.concurrent.ConcurrentHashMap;

// 线程安全的 HashMap
Map<String, Object> safeMap = new ConcurrentHashMap<>();
```

## 🔵 Set - 集合

无序集合，不能有重复元素。

### HashSet

```java
import java.util.HashSet;
import java.util.Set;

Set<String> set = new HashSet<>();

// 添加
set.add("张三");
set.add("李四");
set.add("张三");  // 重复，不会添加

// 大小
int size = set.size();  // 2

// 检查
boolean has = set.contains("张三");

// 遍历
for (String name : set) {
    System.out.println(name);
}

// 删除
set.remove("张三");
```

## 💡 在项目中的使用

### 存储客户端列表

```java
// 使用线程安全的 List
private static final List<OutputStream> clients = 
    new CopyOnWriteArrayList<>();

// 添加客户端
clients.add(outputStream);

// 广播消息
for (OutputStream client : clients) {
    try {
        client.write(data);
        client.flush();
    } catch (IOException e) {
        clients.remove(client);  // 移除断开的客户端
    }
}
```

### MIME 类型映射

```java
private static final Map<String, String> MIME_TYPES = Map.of(
    ".html", "text/html",
    ".css", "text/css",
    ".js", "application/javascript",
    ".json", "application/json",
    ".png", "image/png",
    ".jpg", "image/jpeg",
    ".svg", "image/svg+xml",
    ".ico", "image/x-icon"
);

// 使用
String ext = ".html";
String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");
```

### 消息历史

```java
private static final List<String> messageHistory = new ArrayList<>();
private static final int MAX_HISTORY = 10;

// 保存消息
public synchronized void saveMessage(String message) {
    messageHistory.add(message);
    if (messageHistory.size() > MAX_HISTORY) {
        messageHistory.remove(0);  // 移除最早的
    }
}
```

## ⚠️ 注意事项

### 1. 遍历时删除

```java
// ❌ 错误：遍历时直接删除会抛异常
for (String item : list) {
    if (condition) {
        list.remove(item);  // ConcurrentModificationException!
    }
}

// ✅ 正确：使用 Iterator
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String item = it.next();
    if (condition) {
        it.remove();  // 安全删除
    }
}

// ✅ 或使用 removeIf
list.removeIf(item -> condition);
```

### 2. 选择合适的集合

| 需求 | 推荐集合 |
|------|----------|
| 有序列表，频繁随机访问 | ArrayList |
| 有序列表，频繁增删 | LinkedList |
| 键值对查找 | HashMap |
| 不重复元素 | HashSet |
| 多线程访问列表 | CopyOnWriteArrayList |
| 多线程访问 Map | ConcurrentHashMap |

### 3. 泛型使用

```java
// ✅ 使用泛型
List<String> list = new ArrayList<>();
Map<String, Integer> map = new HashMap<>();

// ❌ 避免原始类型
List list = new ArrayList();  // 不推荐
```

### 4. 空集合

```java
// 返回空集合而不是 null
public List<String> getMessages() {
    if (noMessages) {
        return Collections.emptyList();  // 空列表
    }
    return messages;
}
```

## 📊 常用操作对比

| 操作 | ArrayList | LinkedList | HashSet | HashMap |
|------|-----------|------------|---------|---------|
| 添加 | add() | add() | add() | put() |
| 获取 | get(i) | get(i) | - | get(key) |
| 删除 | remove() | remove() | remove() | remove() |
| 查找 | contains() | contains() | contains() | containsKey() |
| 大小 | size() | size() | size() | size() |
| 遍历 | for/forEach | for/forEach | for/forEach | entrySet() |

## 🔗 相关类

- [Thread](./Thread.md) - 多线程（与集合线程安全相关）
- [CopyOnWriteArrayList](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CopyOnWriteArrayList.html) - 线程安全列表
- [ConcurrentHashMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html) - 线程安全映射
