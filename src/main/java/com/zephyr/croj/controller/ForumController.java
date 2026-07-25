package com.zephyr.croj.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.dto.CreateForumCommentDTO;
import com.zephyr.croj.model.dto.CreateForumPostDTO;
import com.zephyr.croj.model.entity.ForumCategory;
import com.zephyr.croj.model.vo.ForumCommentVO;
import com.zephyr.croj.model.vo.ForumPostVO;
import com.zephyr.croj.service.CommunityContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/forum")
@Tag(name = "论坛")
@Validated
@RequiredArgsConstructor
public class ForumController {
    private final CommunityContentService content;
    private final HttpServletRequest request;

    @GetMapping("/categories")
    @Operation(summary = "论坛分类")
    public Result<List<ForumCategory>> listCategories() {
        return Result.success(content.listCategories());
    }

    @GetMapping("/posts")
    @Operation(summary = "帖子列表")
    public Result<IPage<ForumPostVO>> listPosts(
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long size) {
        return Result.success(content.listPosts(categoryId, current, size));
    }

    @PostMapping("/posts")
    @Operation(summary = "发布帖子")
    public Result<Long> createPost(@RequestBody @Valid CreateForumPostDTO body) {
        return Result.success("发布成功", content.createPost(body, actorId()));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "帖子详情")
    public Result<ForumPostVO> getPost(@PathVariable @Positive Long postId) {
        return Result.success(content.getPost(postId));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "删除自己的帖子；管理员可执行内容治理")
    public Result<Void> deletePost(@PathVariable @Positive Long postId) {
        content.deletePost(postId, actorId());
        return Result.success();
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "评论列表")
    public Result<IPage<ForumCommentVO>> listComments(
            @PathVariable @Positive Long postId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) long size) {
        return Result.success(content.listComments(postId, current, size));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "发表评论或回复")
    public Result<Long> createComment(
            @PathVariable @Positive Long postId,
            @RequestBody @Valid CreateForumCommentDTO body) {
        return Result.success("评论成功", content.createComment(postId, body, actorId()));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "删除自己的评论；管理员可执行内容治理")
    public Result<Void> deleteComment(@PathVariable @Positive Long commentId) {
        content.deleteComment(commentId, actorId());
        return Result.success();
    }

    private Long actorId() {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long id)) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        return id;
    }
}
