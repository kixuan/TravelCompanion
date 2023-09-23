# 黑马点评

## 时长分布
- 基础篇：P1-P22  3h59m
- 实战篇：P24-P95	19h40m
  - 短信登录：24-34  2h20m
  - 商户查询：35-47  3h22m
  - 优惠券秒杀：48-77 8h27m
  - 达人探店：78-81 1h9m
  - 好友关注：82-87 1h56m
  - 附近商户：88-90 1h3m
  - 用户签到：91-95 1h12m

- 高级篇：P96-P144	9h29m
- 原理篇：P145-P175	9h38m


## 启动项目

1. 没有worksapce.xml的话，先自己新建一个-->如果没有service界面
2. 刷新pom.xml文件
3. 修改application的配置文件，mysql密码和redis的host
4. 运行项目
   `HmDianPingApplication`
    - 报错显示`警告: 源发行版 9 需要目标发行版 9`
    - -->`无效的源发行版: 9`
        - https://blog.csdn.net/weixin_45716968/article/details/129436663?spm=1001.2101.3001.6650.1&utm_medium=distribute.pc_relevant.none-task-blog-2%7Edefault%7ECTRLIST%7ERate-1-129436663-blog-121019126.235%5Ev36%5Epc_relevant_default_base3&depth_1-utm_source=distribute.pc_relevant.none-task-blog-2%7Edefault%7ECTRLIST%7ERate-1-129436663-blog-121019126.235%5Ev36%5Epc_relevant_default_base3&utm_relevant_index=2
5. 打开：E:\Project\Java\hmdpAll\nginx-1.18.0  输入cmd，再输入 start nginx.exe （闪屏也没关系），访问http://localhost:8080
   1. 如果后台没有进程，就去conf改端口
   2. 有进程但是报404的错，可能是你改了端口但是访问地址没改端口


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



## 商户查询缓存

### 缓存穿透

缓存穿透 ：是指客户端请求的数据在缓存中和数据库中都不存在，这样缓存永远不会生效，这些请求都会打到数据库。
![image-20230922102237164](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230922102237164.png)

### 缓存雪崩

缓存雪崩：是指在同一时段大量的缓存key同时失效或者Redis服务宕机，导致大量请求到达数据库，带来巨大压力。
![1653327884526](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/1653327884526.png)

解决方案：

* 给不同的Key的TTL添加随机值
* 利用Redis集群提高服务的可用性
* 给缓存业务添加降级限流策略
* 给业务添加多级缓存



### 缓存击穿

缓存击穿问题也叫热点Key问题，就是一个被高并发访问并且缓存重建业务较复杂的key突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击

#### 法1：互斥锁

其实就是一个时间段只能一个线程去处理问题，然后给个锁不让其他线程进来处理，让他去睡觉，睡一会等锁被释放了再来处理

<img src="https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230922102857515.png" alt="image-20230922102857515" style="zoom:50%;" />



#### 法2：逻辑过期

既然是高并发访问那干脆就直接redis里面一直都不要删除了，再加个逻辑过期时间，过期的话就开个独立线程去更新数据写入redis，在没更新完之前访问到的都是redis里面的旧数据

but意想不到的难点居然是如何给数据添加过期时间的字段

方案一：新建⼀个RedisData类，这个类有过期时间字段，然后让pojo继承这个类/直接在原有的类直接加字段，但是这样就改变基础代码了

so方案二：新建⼀个RedisData类，这个类有过期时间字段，同时有另⼀个Object字段（ob就是牛啦存什么都行）用来存数据。相当于给数据又加了⼀层封装

## QS

1. 商铺查询好像有个测试是测什么前面几个没变后面才变了的，没做，不记得是测试什么了

## tips

alt+j 多选快捷键
Ctrl+Alt+V 自动补全快捷键
ctrl+alt+L 格式化代码
ctrl+O 重写方法
ctrl+shift+U 大小写转换

