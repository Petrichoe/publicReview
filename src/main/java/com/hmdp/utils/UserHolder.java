package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
/**
 * UserHolder类用于在多线程环境中保存和获取当前线程的用户信息。
 * 通过ThreadLocal实现线程隔离，确保每个线程的用户信息互不干扰。
 */
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
