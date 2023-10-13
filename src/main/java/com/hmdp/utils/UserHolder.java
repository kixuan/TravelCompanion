package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

// 这是用来干嘛的🤔
// 获取当前登录用户  -->  牛的捏
public class UserHolder {
    // ThreadLocal是一个线程内部的存储类，可以在指定线程内存储数据，数据存储以后，只有指定线程可以得到存储数据
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
