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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;
import static com.hmdp.constant.SystemConstants.*;

/**
 * 服务实现类
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

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        1.检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //  这里抛出异常和return fail有什么区别吗？———> 有区别，抛出异常会被全局异常处理器捕获，返回fail不会
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

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        1.检验手机号  ———>  因为每个请求都是单独的，使用还要再检查一次
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
//   ⭐     2.检验验证码   --  从redis中获取
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

//        4.保存用户到session  -- 保存到token中
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 因为user的id是long类型的，但是StringRedisTemplate只支持String类型的key-value，因此要需要⾃定义map映射规将user转成map后进⾏hash存储
        // userDTO：要转换为Map的Java对象       new HashMap<>()：存储转换后的Map的容器
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                // 忽略userDTO对象中的空值属性，即那些值为null的属性不会被放入userMap中
                CopyOptions.create().setIgnoreNullValue(true)
                        // 将属性值放入userMap前，将属性值转换为其字符串表示形式
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

//        5.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//        设置过期时间
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(7));
        this.save(user);
        return user;
    }
}
