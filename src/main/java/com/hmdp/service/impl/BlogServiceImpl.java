package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.constant.RedisConstants.BLOG_LIKED_KEY;
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
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }

        // 2.查询blog有关的用户
        queryBlogUser(blog);
        // 3. 查询blog是否被点赞（前端显示）
        isBlogLike(blog);
        return Result.ok(blog);
    }

    private void isBlogLike(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        String key = BLOG_LIKED_KEY + blog.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, user.getId().toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

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
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLike(blog);
        });
        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


    @Override
    public Result likeBlog(Long id) {
        // 修改点赞数量,直接update数据库，但是这样可以无限刷赞
        // blogService.update().setSql("liked = liked + 1").eq("id", id).update();

        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        // 2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;

        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, user.getId().toString());
        // 包装类不要直接比较，要用BooleanUtil
        if (BooleanUtil.isFalse(isMember)) {
            //3.如果未点赞，可以点赞
            //3.1 数据库点赞数+1
            boolean isSuccess = blogService.update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2 保存用户到Redis的set集合
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, user.getId().toString());
            }
        } else {
            //4.如果已点赞，取消点赞
            //4.1 数据库点赞数-1
            boolean isSuccess = blogService.update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2 把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, user.getId().toString());
            }
        }
        return Result.ok();
    }
}
