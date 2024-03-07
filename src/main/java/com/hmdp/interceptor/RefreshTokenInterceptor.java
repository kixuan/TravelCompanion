package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.constant.RedisConstants.LOGIN_USER_TTL;

/**
 * 刷新token拦截器
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    // 这里没有用到response和handler，那为什么还要写呢？
    // 因为继承了HandlerInterceptor，重写的话也必须参数对应
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }

        // 2.基于TOKEN获取redis中的用户
        String key = LOGIN_USER_KEY + token;
        // entries()：获取指定key的哈希表中的【所有字段】和它们的值
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if(userMap.isEmpty()){
            return true;
        }

        // 3.将查询到的hash数据转为UserDTO
        UserDTO userDTO =  BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 4.存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser(userDTO);

        // 5.刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
