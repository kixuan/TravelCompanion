package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author kixuan
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 进行关注/取关操作
     */
    @PutMapping("/{id}/{isFollow}")
    //@PathVariable 映射URL绑定的占位符
    public Result follow(@PathVariable("id")Long followUserId,@PathVariable("isFollow")Boolean isFollow){
        return  followService.follow(followUserId,isFollow);
    }

    /**
     * 判断是否关注
     */
    @GetMapping("or/not/{id}")
    public  Result isFollow(@PathVariable("id")Long followUserId){
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }

}
