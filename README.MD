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

6. 前端只显示框架不显示具体数据，list接口和hot接口报错--》sos哥们你看看你后端跑起来了没

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







## 达人探店



注意controller的return不用再加Result.ok(BlogServiceImpl.xxx(xxx))了，直接return BlogServiceImpl.xxx(xxx)

前面一种返回值如下，前端是无法读取到的
![image-20230924091827900](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230924091827900.png)

后面一种的返回值如下，才能正常读取
![image-20230924092020739](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230924092020739.png)



set改成zset的时候一直报错：WRONGTYPE Operation against a key holding the wrong kind of value
![image-20230924130827519](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230924130827519.png)

一脸懵，就是DOUBLE类型啊，gpt也笨笨的找不到原因

csdn了一下，是因为存取的类型不一致导致，之前只存了一个member，现在还存了score

直接把之前的key删掉就行



## 好友关注

### 1、实现关注/取关

前端请求：`/follow/2/true`    -->  后端：`@PutMapping("/{id}/{isFollow}")`

redis使用：使用set的add方法，key为follow:user:userId  ，value为followerId

业务逻辑：

![image-20230926221338361](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230926221338361.png)



代码实现：FollowServiceImpl

```java
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取当前登录用户
        long userId = UserHolder.getUser().getId();
        String key = FOLLOW_USER_KEY + userId;

        // 2. 判断是否关注
        if (isFollow) {
            // 未关注，进行关注操作
            Follow follow = new Follow().setUserId(userId).setFollowUserId(followUserId).setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 成功的话就放在redis里面
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            } else {
                return Result.fail("关注失败，请稍后再试！");
            }
        } else {
            // 关注，进行取关操作  :数据库--redis
            // remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }
```



### 2、查看是否关注

前端请求：`follow/or/not/1`    -->  后端：`@GetMapping("or/not/{id}")`

redis使用：set的`isMember`方法，判断是否存在某个value

代码实现：FollowServiceImpl

```java
    @Override
    public Result isFollow(Long followUserId) {
        // 1. 获取当前登录用户
        long userId = UserHolder.getUser().getId();
        // 2. 从数据库中查是否有关注数据   --> 从redis中查
        // Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        Boolean member = stringRedisTemplate.opsForSet().isMember(FOLLOW_USER_KEY + userId, followUserId.toString());
        return Result.ok(member);
    }
```



### 3、查看共同关注

前端请求：`follow/common/1`    -->  后端：`    @GetMapping("/common/{id}")`

redis使用：set的`intersect`方法，查询交集

难点：

1. 获取的交集是Set<String> 类型的，如何转换成List<Long> 类型方便后续查询具体用户信息时调用

```java
// 解析id集合
// .stream() 方法将集合 intersect 转换为一个 Java 8 流（Stream）
// .map(Long::valueOf) 是一个映射操作，它将集合中的每个元素从字符串转换为 Long 类型。
// .collect(Collectors.toList()) 是一个收集操作，它将流中的元素收集到一个新的 List 集合中。这个新的 List 中包含了经过映射后的 Long 类型的元素。
List<Long> ids = intersect
    .stream()
    .map(Long::valueOf)
    .collect(Collectors.toList());
```

2. 获取的List<User> 怎么转换成List<UserDTO>

```java
// 查询用户
// listByIds返回的是一个List<User>，所以需要进行转换
// .stream() 方法将集合 ids 转换为一个 Java 8 流（Stream）
// .map(user -> BeanUtil.copyProperties(user, UserDTO.class)) 是一个映射操作，它将集合中的每个元素从 User 类型转换为 UserDTO 类型。
// .collect(Collectors.toList()将流中的映射后的 UserDTO 对象收集到一个新的 List<UserDTO> 中。这个操作将流转换为一个列表，其中包含了经过映射后的 UserDTO 对象。
List<UserDTO> users = userService
    .listByIds(ids)
    .stream()
    .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
    .collect(Collectors.toList());
```

代码实现：FollowServiceImpl

```java
    @Override
    public Result followCommons(Long otherUserId) {
        // 1. 获取当前登录用户
        long userId = UserHolder.getUser().getId();
        String userKey = FOLLOW_USER_KEY + userId;
        String otherKey = FOLLOW_USER_KEY + otherUserId;

        //求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, otherKey);
        // 没有的话直接返回空列表
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        // 查询用户
        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
```



### 4、关注推送

#### Feed流实现方案

Feed流产品有两种常见模式：

1. Timeline：不做内容筛选，简单的按照内容发布时间排序，常用于好友或关注。例如朋友圈

| 优点 | 信息全面，不会有缺失。并且实现也相对简单       |
| ---- | ---------------------------------------------- |
| 缺点 | 信息噪音较多，用户不一定感兴趣，内容获取效率低 |

2. 智能排序：利用智能算法屏蔽掉违规的、用户不感兴趣的内容。推送用户感兴趣信息来吸引用户

| 优点 | 投喂用户感兴趣信息，用户粘度很高，容易沉迷 |
| ---- | ------------------------------------------ |
| 缺点 | 如果算法不精准，可能起到反作用             |



采用Timeline的模式。该模式的实现方案有三种：

1. 拉模式：只有粉丝⽤户在读取收件箱的时候， 才会根据其关注的⽤户进⾏拉取，把博主发件箱⾥的消息拉取到粉丝⽤户的收件箱⾥，然后对收件箱⾥的消息按时 间戳进⾏排序。

   优点：节约空间

   缺点：比较延迟，假设用户关注了大用户，此时就会拉取海量的内容，对服务器压力巨大。

   ![1653809450816](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/1653809450816.png)

   

2. 推模式：当⽤户（博主）发送消息时，会把消息+时间戳直接发送到所有粉丝⽤户的收件箱中，并按时间戳进⾏排序。当粉 丝⽤户在读取收件箱的消息时，直接读取。
   优点： 延迟低
    缺点： 发消息时，内容占⽤较⾼。因为每个粉丝都会保留⼀份消息。

   ![image-20230926225708409](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230926225708409.png)

   

   

3. 推拉模式：对于粉丝少的博主⽤户，采⽤推模式。 对于粉丝多的博主⽤户，根据粉丝⽤户类型进⾏判断： 活跃度⾼的粉丝⽤户，采⽤推模式 活跃度低的粉丝⽤户，采⽤拉模式

   兼具推和拉两种模式的优点

   ![1653812346852](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/1653812346852.png)



4. 总结

   ![image-20230926230016927](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230926230016927.png)

最后俺们选择推模式（因为实现起来比较简单

#### 推送到粉丝收件箱

需求：

* 修改新增探店笔记的业务，在保存blog到数据库的同时，推送到粉丝的收件箱
* 收件箱满足可以根据时间戳排序，必须用Redis的数据结构实现
* 查询收件箱数据时，可以实现分页查询

难点：

 当粉丝⽤户需要按分页模式来读取收件箱的信息时，不能采⽤传统的分页模式（按数据的⾓标开始查）。因为Feed 流中的数据会不断更新，所以数据的⾓标也在不断变化。传统的分页模式，会出现消息重复读的问题。

![image-20230926230615989](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230926230615989.png)

业务逻辑：

因为是推模式，所以我们是按照接受Blog的用户作为key

![image-20230926233902913](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230926233902913.png)

代码实现：BlogServiceImpl

```java
    @Override
    public Result saveBlog(Blog blog) {
        if (blog.getShopId() == null || blog.getTitle() == null || blog.getContent() == null) {
            return Result.fail("提交前情把Blog全部信息填写完整(●'◡'●)");
        }
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2.保存探店笔记
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败!");
        }
        // 3.查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 4.推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 4.1.获取粉丝id
            Long userId = follow.getUserId();
            // 4.2.推送 (思路就是只把blog的id传到redis里面，到时候再调用bolg的query方法获取详情)
            String key = FEED_KEY + userId;
            // 还是要按时间戳当作value，因为要进行排序
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }
```

redis数据

![image-20230926233519400](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230926233519400.png)

#### 实现分页查询收邮箱

具体操作如下：

1、每次查询完成后，我们要分析出查询出数据的最小时间戳，这个值会作为下一次查询的条件

2、我们需要找到与上一次查询相同的查询个数作为偏移量，下次查询时，跳过这些查询过的数据，拿到我们需要的数据

综上：我们的请求参数中就需要携带 lastId：上一次查询的最小时间戳 和偏移量这两个参数。

这两个参数第一次会由前端来指定，以后的查询就根据后台结果作为条件，再次传递到后台。

业务逻辑：

![image-20230927011227981](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230927011227981.png)

代码实现：

ScrollResult
```java
@Data
public class ScrollResult {
    private List<?> list;
    // 
    private Long minTime;
    private Integer offset;
}
```

BlogServiceImpl
```java
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();

        // 2. 查询自己的收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 解析数据：blogId、minTime（时间戳）、offset
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 2;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? os : os + offset;
        // 根据id查blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 查询blog相关信息
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLike(blog);
        }
        // 封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }
```



## 附近商户



## 用户签到





## toput

### UserHolder

1. 在拦截器的时候就把当前登录用户`save`一个ThreadLocal对象里面，每个线程都有自己独立的 `ThreadLocal` 值，因此它只会返回当前线程中的 `UserDTO`，而不会混淆不同线程的数据。之后要用到的时候再调用`getUser`获取当前登录用户
2. 【`ThreadLocal` 是一个线程级别的变量，它允许你在每个线程中存储和访问不同的数据。在你的代码中，你使用 `ThreadLocal` 存储了 `UserDTO` 对象，使得每个线程都可以独立地访问自己的 `UserDTO` 实例。】



## QS

1. 商铺查询好像有个测试是测什么前面几个没变后面才变了的，没做，不记得是测试什么了

## tips

alt+j 多选快捷键
Ctrl+Alt+V 自动补全快捷键
ctrl+alt+L 格式化代码
ctrl+O 重写方法
ctrl+shift+U 大小写转换

