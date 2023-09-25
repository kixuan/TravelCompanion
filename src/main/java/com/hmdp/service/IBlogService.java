package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author kixuan
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result saveBlog(Blog blog);

    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    Result queryMyBlog(Integer current);

    Result queryBlogLikes(Long id);

    Result likeBlog(Long id);

    Result queryBlogOfFollow(Long max, Integer offset);
}
