package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;
import static com.hmdp.constant.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 用户服务实现类
 *
 * @author kixuan
 * @since 2023/09/20
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    /**
     * 发送验证码
     */
    @Override
    public Result sendCode(String phone) {
//        1.检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //  这里抛出异常和return fail有什么区别吗？———> 有区别，抛出异常会被全局异常处理器捕获，return fail不会捕获，而是直接退出程序
            //  java基础很不牢固捏异常的作用都忘了(ˉ▽ˉ；)...
            throw new RuntimeException("手机号格式不正确");
        }
//        2.生成验证码
        String code = RandomUtil.randomNumbers(6);
//        3.保存验证码到session  ———> 保存到redis中,redis名字、值、过期时间、时间单位
//        session.setAttribute("code", code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

//        4.发送验证码到手机
//        注意这里的log是lombok的@Slf4j注解生成的，不然只能写一个参数
        log.debug("发送验证码：{}，到手机：{}", code, phone);
        return Result.ok();
    }

    /**
     * 登录
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
//        1.检验手机号  ———>  因为每个请求都是单独的，使用还要再检查一次
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
//        2.检验验证码   --  从redis中获取
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            System.out.println("cacheCode = " + cacheCode);
            return Result.fail("验证码错误");
        }
//        3.检验用户是否存在
//        法1：最简洁的用法，但是有硬编码
//        User user = query().eq("phone", phone).one();
//        法2：使用lambda表达式，减少硬编码
//        User user = this.lambdaQuery().eq(User::getPhone, loginForm.getPhone()).one();
//        法3：复杂一点，但多了一个isNotBlank的动态查询
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(phone), User::getPhone, phone);
        User user = userMapper.selectOne(queryWrapper);
//        如果不存在则创建用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }

//        4.保存用户到session  -- 保存到redis中 【为什么要这一步？因为要实现拦截器，就是根据redis中的用户数据判断该用户是否登录，能否放行】
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        String token = UUID.randomUUID().toString(true);
        // userDTO 脱敏处理
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // user的id是long类型的，但是StringRedisTemplate只支持String类型的key-value
        // 因此要需要⾃定义map映射规将user对象转成map后进⾏hash存储
        // userDTO：要转换为Map的Java对象       new HashMap<>()：存储转换后的Map的容器
        // 调用CopyOptions的方法，忽略userDTO对象中的空值属性，即那些值为null的属性不会被放入userMap中
        // 将属性值放入userMap前，将属性值（long的id）转换为其字符串表示形式
        // 最后的结果是 userMap 中存储了userDTO对象中的非空属性，且属性值都是字符串类型
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 5.存储  注意hash用的是putAll，而不是put
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 设置过期时间
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }


    /**
     * 退出登录
     */
    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null) {
            return Result.ok("尚未登录！无法退出");
        }
        // 去掉redis的记录
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(tokenKey);
        return Result.ok();
    }

    /**
     * 签到功能
     */
    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId;
        key += keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 获取签到次数
     */
    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        // bitField(key, BitFieldSubCommands.create()...)：这是使用 Redis 的 BITFIELD 命令来进行位域操作的部分。它接受一个键（key）以及一个位域子命令（BitFieldSubCommands.create()）。
        // BitFieldSubCommands.create()：这是一个用于创建位域子命令的工厂方法。
        // .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))：这一部分定义了要获取的位域。
        //  BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth) 指示要获取的位域类型是无符号整数，dayOfMonth 是一个表示具体位域位置的变量或常量。
        // .valueAt(0)：这一部分指示要获取位域的位置，这里是位域中的第一个位（索引为0）。
        // 作用是从指定的 Redis 键（key）中获取位域中的某个位的值，位域类型为无符号整数（unsigned），位域的位置是位域中的第一个位（索引为0）。获取的结果将被存储在一个 List<Long> 中，并且该 List 中的每个元素对应于位域中的一个位的值。
        // 涉及到多个位，需要返回一个List<Long>，其中每个Long表示一个位的状态
        List<Long> result = stringRedisTemplate
                .opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                        // 获取多少位
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        // 从左到右取
                        .valueAt(0));
        System.out.println("result ===== " + result);

        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (num > 0) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                break;
            } else {
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
            System.out.println("num ==== " + num);
        }
        return Result.ok(count);
    }

    /**
     * 根据手机号创建用户
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(7));
        this.save(user);
        return user;
    }
}
