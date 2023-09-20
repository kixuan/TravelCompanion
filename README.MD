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
    - 注意运行的是 http://localhost:8081/shop-type/list，看是否有json格式数据

每次修改完要重新运行项目

## 发送验证码

### 基于Session实现登录流程

业务逻辑流程图：
![image-20230920132100575](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230920132100575.png)

#### 发送短信验证码

```java
@Override
public Result sendCode(String phone, HttpSession session) {
//1.校验⼿机号是否合法
if(RegexUtils.isPhoneInvalid(phone)){
//2.若不符合，返回错误信息
return Result.fail("⼿机号格式错误");
 }
//3.若符合，⽣成验证码
String code = RandomUtil.randomNumbers(6);
//4.保存验证码到session
session.setAttribute("code",code);
//5.发送验证码 (要调⽤第三⽅，这⾥不做)
log.debug("发送短信验证码：{}",code);
return Result.ok();
}
```

#### 短信登录、注册功能实现

```java
// 短信登陆
@Override
public Result login(LoginFormDTO loginForm, HttpSession session) {
    String phone = loginForm.getPhone();
    String code = loginForm.getCode();
    //校验⼿机号
    if(RegexUtils.isPhoneInvalid(phone)){
    return Result.fail("⼿机号格式错误");
     }
    //校验验证码
    Object cacheCode = session.getAttribute("code");
    if(cacheCode==null||!code.equals(cacheCode.toString())){
    	return Result.fail("验证码错误");
     }
    //查数据库
    LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(StringUtils.isNotBlank(phone),User::getPhone,phone);
    User user = userMapper.selectOne(queryWrapper);
    //判断⽤户是否存在，不存在则创建⼀个
    if(user==null){
    	user=createUserWithPhone(phone);
     }
    //脱敏，剔除user中的敏感信息，保存⼀个UserDTO到session中
    session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
    return Result.ok();
}
```



### 实现拦截器

com/hmdp/interceptor/LoginInterceptor.java

```java
```



### 基于Redis实现共享session登录流程

业务逻辑：
![image-20230920164632398](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230920164632398.png)





检查再存的时候是否用了redis，这里别忘了

拦截器不生效原因：MvcConfig没加@Configuration注解

alt+j 多选快捷键
Ctrl+Alt+V 自动补全快捷键
ctrl+alt+L 格式化代码
ctrl+O 重写方法
ctrl+shift+U 大小写转换

