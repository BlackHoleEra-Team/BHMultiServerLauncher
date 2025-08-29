# BHMultiServerLauncher

一个基于控制台的 Minecraft 多服务器启动与管理工具，专为 MCSM 面板环境设计，可同时管理多个 Minecraft 服务端实例。同时也可在其他运行环境（如VPS）使用

## 功能特点

- 🚀 **多服务器管理**：同时启动和管理多个 Minecraft 服务端
- 📝 **集中式日志**：所有服务器日志统一存储和管理
- ⌨️ **交互式控制台**：提供简洁易用的命令控制界面
- 🔧 **灵活配置**：支持自定义启动参数和工作目录
- ⚡ **自动启动**：支持配置服务器自动启动

## 快速开始

### 安装要求

- Java 运行环境

### 启动方式

```bash
java -jar BHMultiServerLauncher.jar servers-config.json
```

## 配置文件说明

创建 `servers-config.json` 文件来配置您的服务器：

```json
{
  "logDirectory": "server-logs",
  "servers": [
    {
      "name": "服务器名称",
      "workingDirectory": "服务器工作目录",
      "startCommand": "启动命令",
      "autorun": true
    }
  ]
}
```

### 配置字段说明

- `logDirectory`: 服务器日志存储目录
- `servers`: 服务器配置数组
  - `name`: 服务器标识名称
  - `workingDirectory`: 服务器文件所在目录
  - `startCommand`: 启动服务器的命令
  - `autorun`: 是否随启动器自动启动

### 配置示例

```json
{
  "logDirectory": "server-logs",
  "servers": [
    {
      "name": "Velocity",
      "workingDirectory": "Velocity",
      "startCommand": "java -Xmx200M -jar Velocity.jar nogui",
      "autorun": true
    },
    {
      "name": "Main-World",
      "workingDirectory": "./",
      "startCommand": "java -Xmx4G -jar leaves.jar nogui",
      "autorun": true
    },
    {
      "name": "Main-World2",
      "workingDirectory": "Main-World2",
      "startCommand": "java -Xmx1750M -jar purpur-1.20.4-2176.jar nogui",
      "autorun": true
    }
  ]
}
```

## 命令列表

| 命令 | 参数 | 描述 |
|------|------|------|
| `list` | 无 | 列出所有已配置的服务器 |
| `start` | `<服务器名称>` | 启动指定的服务器 |
| `send` | `<服务器名称> <命令>` | 向指定服务器发送控制台命令 |
| `broadcast` | `<命令>` | 向所有服务器发送同一命令 |
| `status` | `<服务器名称>` | 查看指定服务器的状态信息 |
| `quit`/`exit` | 无 | 安全退出启动器程序 |

## 使用示例

1. **列出所有服务器**:
```
   list
```

2. **启动特定服务器**:
```
   start Velocity
```

3. **向服务器发送命令**:
```
   send Main-World say Hello everyone!
```

4. **广播命令到所有服务器**:
```
   broadcast say Server maintenance in 10 minutes!
```

5. **检查服务器状态**:
```
   status Main-World2
```

## 注意事项

- 确保所有配置的工作目录存在且包含正确的服务器文件
- 启动命令应使用相对于工作目录的路径
- 退出程序前请确保全部服务器已被关闭！
- 日志文件会自动保存在指定的日志目录中

---

如有问题或建议，请提交 Issue。
