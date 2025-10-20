# 项目文档
官方QQ群: 745307987

尽管PoC-Deployer-System已经是一个独立项目但还是在此感谢
项目  https://github.com/Webldix/CVE-2024-31317-PoC-Deployer 

更新内容 :
【uid/gid/selinux上下/groups自定义注入】
【添加应用数据提取功能】
【添加Zygote日志监听功能】
【添加可使用Zygote参数】
## 功能展示

### 图片一
![应用图片](https://codeberg.org/wqry085/PoC-Deployer-System/src/branch/main/jpg/a1.jpg)

uid/gid/groups的注入功能

### 图片二
![应用图片2](https://codeberg.org/wqry085/PoC-Deployer-System/src/branch/main/jpg/a2.jpg)

高级功能

## 支持的情况
 安全补丁 2024 6-1 <
 安卓9-13

## 特性

- 简化了CVE-2024-31317的实现USER只需要授权shizuku权限即可在App内部完成所有提权操作
- 支持反向交互shell