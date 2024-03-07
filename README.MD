# 旅行伴侣

【具体代码笔记查看note.md】

## 项目介绍

旅行伴侣是⼀个⼤量使⽤Redis的项⽬，具有以下功能：

* 短信登录：使用redis共享session来实现
* 旅游城市查询缓存：理解缓存击穿，缓存穿透，缓存雪崩等问题
* 车票秒杀：Redis的计数器功能， 结合Lua完成高性能的redis操作，同时学会Redis分布式锁的原理，包括Redis的三种消息队列
* 点赞：基于List来完成点赞列表的操作，同时基于SortedSet来完成点赞的排行榜功能
* 好友关注：基于Set集合的关注、取消关注，共同关注等等功能
* 附近城市：利用Redis的GEOHash来完成对于地理坐标的操作
* 用户签到：使用Redis的BitMap数据统计功能
* UV统计：使用Redis来完成统计功能

## 短信登陆

### 基于Session实现登录流程

实现逻辑：

![image-20230927173434311](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230927173434311.png)

### 基于Redis实现共享session登录流程

实现逻辑：橙色为修改部分

![image-20230927175727239](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230927175727239.png)



### 拦截器及优化

实现逻辑：

![image-20231011220113654](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231011220113654.png)





## 查询缓存

### 添加redis

实现逻辑：

![image-20230928141201772](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230928141201772.png)



### 缓存穿透

问题出现：

是指客户端请求的数据在缓存中和数据库中都不存在，这样缓存永远不会生效，这些请求都会打到数据库。

解决逻辑：

![image-20230922102237164](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230922102237164.png)



### 缓存雪崩

问题出现：

同一时段大量的缓存key同时失效或者Redis服务宕机，导致大量请求到达数据库，带来巨大压力。

解决逻辑：

* 给不同的Key的TTL添加随机值（key失效）
* 给业务添加多级缓存（key失效）
* 利用Redis集群提高服务的可用性（redis宕机）
* 给缓存业务添加降级限流策略（redis宕机）
* 

### 缓存击穿

问题出现：

一个被高并发访问并且缓存重建业务较复杂的key突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击

解决逻辑：

![image-20230928221747038](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230928221747038.png)



## 车票秒杀

### 生成全局唯一ID

### 实现秒杀下单

实现逻辑：

![image-20230928224816106](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230928224816106.png)

### 超卖问题

解决逻辑：乐观锁判断版本号

```java
boolean success = seckillVoucherService.update()
            .setSql("stock= stock -1")
            .eq("voucher_id", voucherId).update().gt("stock",0); //where id = ? and stock > 0
```



### 一人一单

![1653371854389](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/为什么显示不出来.png)



## 分布式锁

### 误删问题

问题出现：

![1653385920025](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/1653385920025.png)

解决逻辑：在获取锁时存入线程标示（可以用UUID表示），一致放锁，不一致不放锁



### 原子性问题

![1653387764938](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/1653387764938.png)

解决思路： Lua脚本解决多条命令原子性问题

## redisson

### 可重入锁原理

![image-20230930164115965](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230930164115965.png)

### lua脚本实现可重入锁

![image-20230930164936178](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230930164936178.png)

![image-20230930165048433](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230930165048433.png)



### 锁重试和WatchDog机制

![image-20231008143752437](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231008143752437.png)

## 优化秒杀

实现逻辑：

![image-20231008163227251](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231008163227251.png)

lua保证原子性

![image-20231008162950201](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231008162950201.png)

## 消息队列

### 基于List实现消息队列

![image-20231009083501487](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231009083501487.png)

### 基于PubSub的消息队列

![image-20231009083532234](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231009083532234.png)



### Redis的Stream消息队列实现异步秒杀

![image-20231009090819195](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231009090819195.png)



## 达人探城

### 发布、查看探店笔记

![image-20230928110152226](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230928110152226.png)



### 点赞

![image-20230928114045743](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230928114045743.png)



### 点赞排行榜

![image-20231013091119204](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231013091119204.png)



## 好友关注

### 实现关注/取关

![image-20231013091725186](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20231013091725186.png)



### 查看共同关注

set的`intersect`方法查询交集，传入两个key查找交集value

```java
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, otherKey);
```



## 附近城市

### 导入城市位置

![image-20230927131151418](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230927131151418.png)


### 附近城市

![image-20230927140333252](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230927140333252.png)



## 用户签到

### 签到

![image-20230927103837780](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230927103837780.png)



### 统计签到次数

 ![image-20230927112307193](https://cdn.jsdelivr.net/gh/kixuan/PicGo/images/image-20230927112307193.png)



## UV统计

实现逻辑：

```java
stringRedisTemplate.opsForHyperLogLog().add("testHyperLogLog", values);
```

