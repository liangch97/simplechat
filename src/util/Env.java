package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 简易 .env 加载器：读取项目根目录下的 .env 文件到内存，
 * 同时支持从系统环境变量与 -D 系统属性读取。
 */
public class Env {
    private static final Map<String, String> ENV_CACHE = new HashMap<>();
    private static boolean loaded = false;

    private static synchronized void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        File file = new File(".env");
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx <= 0) continue;
                String key = line.substring(0, idx).trim();
                String val = line.substring(idx + 1).trim();
                // 去掉包裹的引号
                if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                ENV_CACHE.put(key, val);
            }
        } catch (IOException ignored) {
        }
    }

    public static String get(String key, String defaultValue) {
        loadIfNeeded();
        // 1) JVM -Dkey=value
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        // 2) 系统环境变量
        v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        // 3) .env 文件
        v = ENV_CACHE.get(key);
        if (v != null && !v.isEmpty()) return v;
        return defaultValue;
    }
}
