package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.constant.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author kixuan
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IBlogService blogService;

    @Resource
    private IUserService userService;

    // @Override
    // public Result queryBolgByid(Long id) {
    //     Blog blog = this.getById(id);
    //     if (blog == null) {
    //         return Result.fail("笔记不存在！");
    //     }
    //
    //     // 2.查询blog有关的用户
    //     queryBlogUser(blog);
    //     return Result.ok(blog);
    // }

    @Override
    public Result saveBlog(Blog blog) {
        if (blog.getShopId() == null || blog.getTitle() == null || blog.getContent() == null) {
            return Result.fail("必须把相关信息填写完整！");
        }
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        this.save(blog);
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = this.query()
                .eq("user_id", user.getId())
                .orderByDesc("liked")
                .page(new Page<>(current, MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 用户查询，根据点赞数进行降序排列
        //     Page<Blog> page = this.query()
        //             .orderByDesc("liked")
        //             .page(new Page<>(current, MAX_PAGE_SIZE));
        //     // 获取当前页数据
        //     List<Blog> records = page.getRecords();
        //     // 查询用户
        //     records.forEach(blog -> {
        //         Long userId = blog.getUserId();
        //         User user = userService.getById(userId);
        //         blog.setName(user.getNickName());
        //         blog.setIcon(user.getIcon());
        //     });
        //     return Result.ok(records);
        // }
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 修改点赞数量,直接update数据库，但是这样可以无限刷赞
        // blogService.update().setSql("liked = liked + 1").eq("id", id).update();

        // // 1.获取登录用户
        // UserDTO user = UserHolder.getUser();
        // // 2.判断当前登录用户是否已经点赞
        // String key = "blog:liked:" + id;
        return Result.ok();
    }
}
