package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author kixuan
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 注意这里不要写成Result.ok(blogService.saveBlog(current));
        // 不然前端无法正常读取数据
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

}
