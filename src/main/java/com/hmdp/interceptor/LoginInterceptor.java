package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 获取用户信息判断是否需要拦截  ——>   获取token判断  ——>  根据ThreadLocal是否有用户判断

        // ①获取用户信息判断是否需要拦截
        // // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        // Object user = request.getSession().getAttribute("user");
        // if (user == null) {
        //     // 没有，需要拦截，设置状态码
        //     response.setStatus(401);
        //     return false;
        // }
        // 有用户，则保存⽤户信息到ThreadLocal
        // UserHolder.saveUser((UserDTO) user);

        // ②（添加用户token的redis缓存后）获取token判断
        // String token = request.getHeader("authorization");
        // if (token == null) {
        //     // 没有，需要拦截，设置状态码
        //     response.setStatus(401);
        //     return false;
        // }
        // // 有的话以map形式存在redis中，加判断是因为防止：①token已过期，②redis连接出现问题
        // Map<Object, Object> map =
        //         stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        // if (map.isEmpty()) {
        //     response.setStatus(401);
        //     return false;
        // }
        // // 保存⽤户信息到ThreadLocal
        // UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        // UserHolder.saveUser(userDTO);
        // // 刷新token有效期
        // stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // // 放行
        // return true;

        // ③（添加一切拦截器后）根据ThreadLocal判断是否有用户
        if (UserHolder.getUser() == null) {
            // 没有，需要拦截，设置状态码
            response.setStatus(401);
            // 拦截
            return false;
        }
        // 有用户，则放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //  移除ThreadLocal中的⽤户
        UserHolder.removeUser();
    }
}