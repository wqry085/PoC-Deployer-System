# PoC-Deployer-System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

一个基于CVE-2024-31317的Android提权漏洞部署系统，集成了Termux终端模拟器功能。

## 项目声明

### 致谢
尽管PoC-Deployer-System已经是一个独立项目，但在此特别感谢原项目的启发和贡献：

**原项目**: https://github.com/Webldix/CVE-2024-31317-PoC-Deployer

## 更新内容

### 最新特性
- **【uid/gid/selinux上下文/groups自定义注入】**
- **【添加应用数据提取功能】**
- **【添加Zygote日志监听功能】**
- **【添加可使用Zygote参数】**

## 功能展示

### uid/gid/groups注入功能
![应用图片](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a1.jpg)

### 高级功能界面
![应用图片2](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a2.jpg)

### 反方向shell
![应用图片](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a3.jpg)

## 系统要求

### 支持的情况
- **安全补丁**: 2024年6月之前
- **Android版本**: 9 - 13
- **权限要求**: Shizuku权限

## 主要特性

### 核心功能
- **简化实现**: 简化了CVE-2024-31317的实现，用户只需要授权Shizuku权限即可在App内部完成所有提权操作
- **Termux集成**: 集成了Termux部分终端模拟器功能
- **反向Shell**: 支持反向交互shell
- **权限注入**: 完整的uid/gid/selinux上下文/groups自定义注入能力
- **数据提取**: 应用数据提取功能
- **Zygote监控**: Zygote日志监听和参数配置

### 技术特点
- 只需shizuku权限即可实现提权操作
- 完整操作功能界面
- 实时系统监控

## 使用说明

1. 安装应用并授予Shizuku权限
2. 开启socket监听
3. 配置所需注入的参数
4. 执行注入

## 免责声明

本项目仅用于安全研究和教育目的，请勿用于非法用途。使用者需遵守当地法律法规，对使用本项目造成的任何后果负责。

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

**联系信息：**
- GitHub: [wqry085](https://github.com/wqry085)

*请在使用前仔细阅读许可证中的免责条款和特别警告。*