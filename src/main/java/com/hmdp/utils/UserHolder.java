package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

// TODO 这是用来干嘛的🤔
// 获取当前登录用户  -->  牛的捏
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
