# Thread 类

`java.lang.Thread`

## 📌 类简介

Thread（线程）是 Java 中实现并发的基本单位。一个程序可以同时运行多个线程，每个线程独立执行任务。

想象一个餐厅：
- 单线程 = 一个服务员，一次只能服务一桌客人
- 多线程 = 多个服务员，可以同时服务多桌客人

在聊天室项目中，每个客户端连接都需要一个线程来处理。

## 📦 所属包

```java
import java.lang.Thread;  // 实际上不需要导入，java.lang 自动导入
```

## 🔨 创建线程的方式

### 方式1：继承 Thread 类

```java
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("线程运行中: " + getName());
    }
}

// 使用
MyThread t = new MyThread();
t.start();  // 启动线程
```

### 方式2：实现 Runnable 接口（推荐）

```java
class MyTask implements Runnable {
    @Override
    public void run() {
        System.out.println("任务执行中");
    }
}

// 使用
Thread t = new Thread(new MyTask());
t.start();
```

### 方式3：Lambda 表达式（最简洁）

```java
Thread t = new Thread(() -> {
    System.out.println("Lambda 线程");
});
t.start();

// 或者一行搞定
new Thread(() -> System.out.println("Hello")).start();
```

## 📋 常用方法

### 线程控制

| 方法 | 说明 |
|------|------|
| `start()` | 启动线程（调用 run 方法） |
| `run()` | 线程执行的任务（不要直接调用） |
| `join()` | 等待线程结束 |
| `join(long ms)` | 最多等待指定毫秒 |
| `interrupt()` | 中断线程 |
| `isInterrupted()` | 检查是否被中断 |

### 线程状态

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `isAlive()` | `boolean` | 线程是否还在运行 |
| `getState()` | `Thread.State` | 获取线程状态 |
| `getName()` | `String` | 获取线程名称 |
| `setName(String)` | `void` | 设置线程名称 |

### 静态方法

| 方法 | 说明 |
|------|------|
| `Thread.currentThread()` | 获取当前线程 |
| `Thread.sleep(long ms)` | 让当前线程休眠 |
| `Thread.yield()` | 让出 CPU 时间片 |

## 💡 完整示例

### 基本使用

```java
public class ThreadDemo {
    public static void main(String[] args) {
        // 创建线程
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println("线程1: " + i);
                try {
                    Thread.sleep(500);  // 休眠500毫秒
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Worker-1");  // 设置线程名
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println("线程2: " + i);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Worker-2");
        
        // 启动
        t1.start();
        t2.start();
        
        System.out.println("主线程继续执行...");
    }
}
```

输出（顺序可能不同）：
```
主线程继续执行...
线程1: 0
线程2: 0
线程1: 1
线程2: 1
...
```

### 等待线程完成

```java
Thread t = new Thread(() -> {
    try {
        Thread.sleep(2000);
        System.out.println("任务完成");
    } catch (InterruptedException e) {}
});

t.start();
System.out.println("等待线程完成...");

t.join();  // 阻塞，直到 t 结束

System.out.println("线程已结束");
```

### 聊天服务器中的多线程

```java
public class ChatServer {
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(7070);
        
        while (true) {
            Socket client = server.accept();
            
            // 为每个客户端创建新线程
            new Thread(() -> {
                try {
                    handleClient(client);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    
    static void handleClient(Socket client) throws IOException {
        // 处理客户端...
    }
}
```

## 🔄 线程状态

```
    ┌─────────────────────────────────────────┐
    │                                         │
    ▼                                         │
  [NEW] ──start()──► [RUNNABLE] ──────────────┤
                        │  ▲                  │
                        │  │                  │
             sleep()/   │  │ 时间到/          │
             wait()/    │  │ notify()/        │
             join()     │  │ 中断             │
                        │  │                  │
                        ▼  │                  │
                    [WAITING/               │
                     TIMED_WAITING]           │
                        │                     │
                        └─────────────────────┤
                                              │
    [TERMINATED] ◄────── 运行结束 ─────────────┘
```

| 状态 | 说明 |
|------|------|
| NEW | 新建，还未启动 |
| RUNNABLE | 可运行（正在运行或等待 CPU） |
| BLOCKED | 阻塞，等待锁 |
| WAITING | 等待，无限期等待 |
| TIMED_WAITING | 计时等待 |
| TERMINATED | 已结束 |

## ⚠️ 注意事项

### 1. start() vs run()

```java
Thread t = new Thread(() -> System.out.println("Hello"));

// ❌ 错误：直接调用 run()，在当前线程执行
t.run();  

// ✅ 正确：调用 start()，创建新线程执行
t.start();
```

### 2. 处理 InterruptedException

```java
// ❌ 不好：空的 catch 块
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    // 什么都不做
}

// ✅ 好：恢复中断状态或退出
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // 恢复中断状态
    return;  // 或者退出循环
}
```

### 3. 线程安全

```java
// ❌ 不安全：多线程同时修改
int count = 0;
void increment() {
    count++;  // 可能丢失更新
}

// ✅ 安全：加锁
int count = 0;
synchronized void increment() {
    count++;
}

// ✅ 或使用原子类
AtomicInteger count = new AtomicInteger(0);
void increment() {
    count.incrementAndGet();
}
```

### 4. 守护线程

```java
Thread t = new Thread(() -> {
    while (true) {
        // 后台任务...
    }
});

t.setDaemon(true);  // 设为守护线程
t.start();

// 当所有非守护线程结束时，守护线程自动终止
```

## 🏊 线程池（推荐方式）

直接创建 Thread 对象开销大，推荐使用线程池：

```java
import java.util.concurrent.*;

// 创建线程池
ExecutorService pool = Executors.newFixedThreadPool(10);

// 提交任务
pool.submit(() -> {
    System.out.println("任务1");
});

pool.submit(() -> {
    System.out.println("任务2");
});

// 关闭线程池
pool.shutdown();
```

常用线程池：

| 方法 | 说明 |
|------|------|
| `newFixedThreadPool(n)` | 固定 n 个线程 |
| `newCachedThreadPool()` | 按需创建，空闲回收 |
| `newSingleThreadExecutor()` | 单线程 |
| `newScheduledThreadPool(n)` | 定时任务 |

## 📚 在项目中的使用

在 `WebChatServer.java` 中：

```java
// 使用线程池处理请求
server.setExecutor(Executors.newCachedThreadPool());
```

在 `ChatServer.java` 中：

```java
// 为每个客户端创建线程
while (true) {
    Socket client = serverSocket.accept();
    new Thread(new ClientHandler(client)).start();
}
```

心跳线程：

```java
new Thread(() -> {
    while (true) {
        broadcast(":heartbeat");
        Thread.sleep(30000);
    }
}).start();
```
