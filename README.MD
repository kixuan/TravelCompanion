# 黑马点评

## 启动项目

1. 没有worksapce.xml的话，先自己新建一个-->如果没有service界面
2. 刷新pom.xml文件
3. 修改application的配置文件，mysql密码和redis的host
4. 运行项目
   `HmDianPingApplication`
    - 报错显示`警告: 源发行版 9 需要目标发行版 9`
    - -->`无效的源发行版: 9`
        - https://blog.csdn.net/weixin_45716968/article/details/129436663?spm=1001.2101.3001.6650.1&utm_medium=distribute.pc_relevant.none-task-blog-2%7Edefault%7ECTRLIST%7ERate-1-129436663-blog-121019126.235%5Ev36%5Epc_relevant_default_base3&depth_1-utm_source=distribute.pc_relevant.none-task-blog-2%7Edefault%7ECTRLIST%7ERate-1-129436663-blog-121019126.235%5Ev36%5Epc_relevant_default_base3&utm_relevant_index=2
5. 访问http://localhost:8080

- 出错显示`Whitelabel Error Page`
    - 看第三步是否完成
    - 注意运行的是 http://localhost:8081/shop-type/list

每次修改完要重新运行项目

## 发送验证码

### 基于Session实现登录流程

### 实现拦截器

### redis实现验证码
检查再存的时候是否用了redis，这里别忘了

拦截器不生效原因：MvcConfig没加@Configuration注解

alt+j 多选快捷键
Ctrl+Alt+V 自动补全快捷键
ctrl+alt+L 格式化代码
ctrl+O 重写方法
ctrl+shift+U 大小写转换