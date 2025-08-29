package com.bhe.bhMultiServerLauncher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MultiServerLauncher {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat LOG_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private Config config;
    private ExecutorService executorService;
    private Map<String, Process> serverProcesses = new HashMap<>();
    private Map<String, PrintWriter> serverWriters = new HashMap<>();
    private boolean running = true;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("请指定配置文件路径: java -jar launcher.jar config.json");
            return;
        }

        new MultiServerLauncher().startServers(args[0]);
    }

    public void startServers(String configPath) {
        try {
            // 读取配置文件
            String content = new String(Files.readAllBytes(Paths.get(configPath)));
            config = Config.fromJson(content);

            // 检查配置是否成功解析
            if (config == null) {
                System.err.println("配置解析失败，无法启动服务器");
                return;
            }

            // 创建日志目录
            new File(config.logDirectory).mkdirs();

            // 创建线程池（用于用户输入处理和服务器启动）
            // 使用足够大的线程池来处理所有服务器
            executorService = Executors.newFixedThreadPool(config.servers.length + 2);

            // 启动用户输入处理
            executorService.submit(this::handleUserInput);

            // 启动所有配置为自动启动的服务器（在单独的线程中）
            for (ServerConfig serverConfig : config.servers) {
                if (serverConfig.autorun) {
                    // 为每个服务器创建一个新线程
                    executorService.submit(() -> {
                        try {
                            System.out.println("正在启动服务器: " + serverConfig.name);
                            startServer(serverConfig);
                        } catch (IOException e) {
                            System.err.println("启动服务器 " + serverConfig.name + " 失败: " + e.getMessage());
                        }
                    });

                    // 等待一段时间再启动下一个服务器
                    try {
                        Thread.sleep(20000); // 20秒延迟
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("启动延迟被中断");
                    }
                } else {
                    System.out.println("跳过自动启动: " + serverConfig.name + " (autorun=false)");
                }
            }

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("正在关闭所有服务器...");
                running = false;
                executorService.shutdownNow();

                // 关闭所有服务器进程
                for (Process process : serverProcesses.values()) {
                    if (process.isAlive()) {
                        process.destroy();
                    }
                }
            }));

        } catch (IOException e) {
            System.err.println("读取配置文件失败: " + e.getMessage());
        }
    }

    private void startServer(ServerConfig serverConfig) throws IOException {
        String logFileName = String.format("%s/%s-%s.log",
                config.logDirectory, serverConfig.name, DATE_FORMAT.format(new Date()));

        try (PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName, true))) {
            ProcessBuilder builder = new ProcessBuilder(parseCommand(serverConfig.startCommand));
            builder.directory(new File(serverConfig.workingDirectory));
            builder.redirectErrorStream(true); // 合并标准错误和标准输出

            Process process = builder.start();
            serverProcesses.put(serverConfig.name, process);

            // 获取进程的输出流用于发送命令
            PrintWriter processWriter = new PrintWriter(process.getOutputStream());
            serverWriters.put(serverConfig.name, processWriter);

            System.out.println("[" + serverConfig.name + "] 服务器已启动");

            // 读取进程输出
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                String timestamp = LOG_TIME_FORMAT.format(new Date());
                String formattedLine = String.format("[%s] [%s] %s", serverConfig.name, timestamp, line);

                // 输出到控制台
                System.out.println(formattedLine);

                // 写入日志文件
                logWriter.println(formattedLine);
                logWriter.flush();
            }

            int exitCode = process.waitFor();
            String exitMessage = String.format("[%s] 服务器进程已退出，代码: %d", serverConfig.name, exitCode);
            System.out.println(exitMessage);
            logWriter.println(exitMessage);

            // 从映射中移除
            serverProcesses.remove(serverConfig.name);
            serverWriters.remove(serverConfig.name);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[" + serverConfig.name + "] 服务器进程被中断");
        }
    }

    private void handleUserInput() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            System.out.println("BHMultiServerLauncher 已启动 - 输入 'help' 查看帮助");

            String line;
            while (running && (line = consoleReader.readLine()) != null) {
                String response = processCommand(line);
                System.out.println(response);

                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            System.err.println("读取用户输入时出错: " + e.getMessage());
        }
    }

    private String processCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "无效命令";
        }

        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "help":
                return "可用命令:\n" +
                        "list - 列出所有服务器\n" +
                        "start <服务器> - 启动指定服务器\n" +
                        "send <服务器> <命令> - 向指定服务器发送命令\n" +
                        "broadcast <命令> - 向所有服务器发送命令\n" +
                        "status <服务器> - 查看服务器状态\n" +
                        "quit/exit - 退出程序";

            case "list":
                StringBuilder sb = new StringBuilder("服务器列表:\n");
                for (ServerConfig server : config.servers) {
                    boolean isRunning = serverProcesses.containsKey(server.name) &&
                            serverProcesses.get(server.name).isAlive();
                    sb.append(" - ").append(server.name)
                            .append(" (").append(isRunning ? "运行中" : "已停止")
                            .append(", autorun=").append(server.autorun).append(")\n");
                }
                return sb.toString();

            case "start":
                if (arg.isEmpty()) {
                    return "用法: start <服务器>";
                }

                // 查找要启动的服务器配置
                ServerConfig targetServerConfig = null;
                for (ServerConfig server : config.servers) {
                    if (server.name.equals(arg)) {
                        targetServerConfig = server;
                        break;
                    }
                }

                if (targetServerConfig == null) {
                    return "错误: 服务器 '" + arg + "' 不存在";
                }

                if (serverProcesses.containsKey(arg) && serverProcesses.get(arg).isAlive()) {
                    return "错误: 服务器 '" + arg + "' 已经在运行中";
                }

                try {
                    // 创建一个final引用，以便在lambda中使用
                    final ServerConfig finalServerConfig = targetServerConfig;

                    // 在新线程中启动服务器，避免阻塞用户输入
                    executorService.submit(() -> {
                        try {
                            System.out.println("正在启动服务器: " + finalServerConfig.name);
                            startServer(finalServerConfig);
                            System.out.println("服务器 " + finalServerConfig.name + " 已启动完成");
                        } catch (IOException e) {
                            System.err.println("启动服务器 " + finalServerConfig.name + " 失败: " + e.getMessage());
                        }
                    });
                    return "正在启动服务器: " + arg;
                } catch (Exception e) {
                    return "错误: 启动服务器 '" + arg + "' 失败: " + e.getMessage();
                }

            case "send":
                if (arg.isEmpty()) {
                    return "用法: send <服务器> <命令>";
                }

                String[] sendParts = arg.split(" ", 2);
                if (sendParts.length < 2) {
                    return "用法: send <服务器> <命令>";
                }

                String targetServer = sendParts[0];
                String serverCommand = sendParts[1];

                if (!serverWriters.containsKey(targetServer)) {
                    return "错误: 服务器 '" + targetServer + "' 不存在或未运行";
                }

                try {
                    PrintWriter writer = serverWriters.get(targetServer);
                    writer.println(serverCommand);
                    writer.flush();
                    return "已向服务器 '" + targetServer + "' 发送命令: " + serverCommand;
                } catch (Exception e) {
                    return "错误: 发送命令到服务器 '" + targetServer + "' 失败: " + e.getMessage();
                }

            case "broadcast":
                if (arg.isEmpty()) {
                    return "用法: broadcast <命令>";
                }

                int successCount = 0;
                for (String serverName : serverWriters.keySet()) {
                    try {
                        PrintWriter writer = serverWriters.get(serverName);
                        writer.println(arg);
                        writer.flush();
                        successCount++;
                    } catch (Exception e) {
                        System.err.println("向服务器 '" + serverName + "' 广播命令失败: " + e.getMessage());
                    }
                }

                return "已向 " + successCount + " 个服务器广播命令: " + arg;

            case "status":
                if (arg.isEmpty()) {
                    return "用法: status <服务器>";
                }

                if (!serverProcesses.containsKey(arg)) {
                    return "错误: 服务器 '" + arg + "' 不存在或未运行";
                }

                Process process = serverProcesses.get(arg);
                return "服务器 '" + arg + "' 状态: " +
                        (process.isAlive() ? "运行中" : "已停止");

            case "quit":
            case "exit":
                running = false;
                return "正在关闭所有服务器...";

            default:
                return "未知命令: " + cmd + " - 输入 'help' 查看帮助";
        }
    }

    private String[] parseCommand(String command) {
        // 简单的命令解析，支持带引号的参数
        return command.split(" (?=([^\"]*\"[^\"]*\")*[^\"]*$)");
    }

    // 配置类
    static class Config {
        String logDirectory;
        ServerConfig[] servers;

        static Config fromJson(String json) {
            try {
                JSONObject jsonObject = new JSONObject(json);
                Config config = new Config();

                config.logDirectory = jsonObject.getString("logDirectory");

                JSONArray serversArray = jsonObject.getJSONArray("servers");
                config.servers = new ServerConfig[serversArray.length()];

                for (int i = 0; i < serversArray.length(); i++) {
                    JSONObject serverObj = serversArray.getJSONObject(i);
                    ServerConfig serverConfig = new ServerConfig();

                    serverConfig.name = serverObj.getString("name");
                    serverConfig.workingDirectory = serverObj.getString("workingDirectory");
                    serverConfig.startCommand = serverObj.getString("startCommand");
                    serverConfig.autorun = serverObj.optBoolean("autorun", true); // 默认为true

                    config.servers[i] = serverConfig;
                }

                return config;
            } catch (Exception e) {
                System.err.println("解析 JSON 配置时出错: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
    }

    // 服务器配置类
    static class ServerConfig {
        String name;
        String workingDirectory;
        String startCommand;
        boolean autorun = true; // 默认为true
    }
}
