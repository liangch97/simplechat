import java.io.*;
import java.net.*;

public class CopySimpleClient {
    
    public static void main(String[] args) {
        // 服务器地址和端口
        String host = "127.0.0.1";
        int port = 5050;
        System.out.println("=== 复制的简单客户端示例 ===");
        System.out.println("正在连接服务器 " + host + ":" + port);
        try(Socket socket = new Socket(host,port)){
            System.out.println("连接成功!");

            
        }