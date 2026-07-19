package com.zephyr.croj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.community.ForumResourceType;
import com.zephyr.croj.contest.ContestService;
import com.zephyr.croj.mapper.ForumCategoryMapper;
import com.zephyr.croj.mapper.ForumCommentMapper;
import com.zephyr.croj.mapper.ForumPostMapper;
import com.zephyr.croj.mapper.SolutionMapper;
import com.zephyr.croj.model.dto.CreateForumCommentDTO;
import com.zephyr.croj.model.dto.CreateForumPostDTO;
import com.zephyr.croj.model.dto.PublishSolutionDTO;
import com.zephyr.croj.model.entity.ForumCategory;
import com.zephyr.croj.model.entity.ForumComment;
import com.zephyr.croj.model.entity.ForumPost;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.Solution;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.vo.ForumCommentVO;
import com.zephyr.croj.model.vo.ForumPostVO;
import com.zephyr.croj.model.vo.SolutionVO;
import com.zephyr.croj.service.CommunityContentService;
import com.zephyr.croj.service.ProblemService;
import com.zephyr.croj.service.UserService;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class CommunityContentServiceImpl implements CommunityContentService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final String DELETED = "DELETED";

    private final ForumCategoryMapper categories;
    private final ForumPostMapper posts;
    private final ForumCommentMapper comments;
    private final SolutionMapper solutions;
    private final UserService users;
    private final ProblemService problems;
    private final ContestService contests;

    @Override
    public List<ForumCategory> listCategories() {
        return categories.selectList(new LambdaQueryWrapper<ForumCategory>()
                .orderByAsc(ForumCategory::getSortOrder).orderByAsc(ForumCategory::getId));
    }

    @Override
    public IPage<ForumPostVO> listPosts(
            Long categoryId, ForumResourceType resourceType, Long resourceId, long current, long size) {
        requireReadableResource(resourceType, resourceId, null, false);
        LambdaQueryWrapper<ForumPost> query = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getStatus, PUBLISHED)
                .eq(categoryId != null, ForumPost::getCategoryId, categoryId)
                .eq(ForumPost::getResourceType, resourceType.name())
                .eq(resourceId != null, ForumPost::getResourceId, resourceId)
                .isNull(resourceId == null, ForumPost::getResourceId)
                .orderByDesc(ForumPost::getPinned)
                .orderByDesc(ForumPost::getCreatedAt);
        return mapAuthoredPage(posts.selectPage(new Page<>(current, size), query),
                ForumPost::getAuthorId, this::toPostVO);
    }

    @Override
    public ForumPostVO getPost(Long postId) {
        ForumPost post = requirePublishedPost(postId);
        requireReadablePost(post, null, false);
        return toPostVO(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long createPost(CreateForumPostDTO request, Long actorId) {
        requireActiveActor(actorId);
        if (categories.selectById(request.getCategoryId()) == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        requireReadableResource(request.getResourceType(), request.getResourceId(), null, false);
        LocalDateTime now = LocalDateTime.now();
        ForumPost post = new ForumPost();
        post.setCategoryId(request.getCategoryId());
        post.setAuthorId(actorId);
        post.setResourceType(request.getResourceType().name());
        post.setResourceId(request.getResourceId());
        post.setTitle(request.getTitle().trim());
        post.setContentMarkdown(request.getContentMarkdown());
        post.setContentHtml(renderSafeHtml(request.getContentMarkdown()));
        post.setStatus(PUBLISHED);
        post.setPinned(false);
        post.setLocked(false);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        requireInsert(posts.insert(post));
        return post.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long postId, Long actorId) {
        ForumPost post = requirePublishedPost(postId);
        requireOwnerOrAdmin(post.getAuthorId(), actorId);
        post.setStatus(DELETED);
        post.setUpdatedAt(LocalDateTime.now());
        requireUpdate(posts.updateById(post));
    }

    @Override
    public IPage<ForumCommentVO> listComments(Long postId, long current, long size) {
        ForumPost post = requirePublishedPost(postId);
        requireReadablePost(post, null, false);
        LambdaQueryWrapper<ForumComment> query = new LambdaQueryWrapper<ForumComment>()
                .eq(ForumComment::getPostId, postId)
                .eq(ForumComment::getStatus, PUBLISHED)
                .orderByAsc(ForumComment::getCreatedAt);
        return mapAuthoredPage(comments.selectPage(new Page<>(current, size), query),
                ForumComment::getAuthorId, this::toCommentVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long createComment(Long postId, CreateForumCommentDTO request, Long actorId) {
        requireActiveActor(actorId);
        ForumPost post = requirePublishedPost(postId);
        requireReadablePost(post, null, false);
        if (Boolean.TRUE.equals(post.getLocked())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        if (request.getParentId() != null) {
            ForumComment parent = comments.selectById(request.getParentId());
            if (parent == null || !postId.equals(parent.getPostId()) || !PUBLISHED.equals(parent.getStatus())) {
                throw new BusinessException(ResultCodeEnum.NOT_FOUND);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        ForumComment comment = new ForumComment();
        comment.setPostId(postId);
        comment.setParentId(request.getParentId());
        comment.setAuthorId(actorId);
        comment.setContentMarkdown(request.getContentMarkdown());
        comment.setContentHtml(renderSafeHtml(request.getContentMarkdown()));
        comment.setStatus(PUBLISHED);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        requireInsert(comments.insert(comment));
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long actorId) {
        ForumComment comment = comments.selectById(commentId);
        if (comment == null || !PUBLISHED.equals(comment.getStatus())) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        requireOwnerOrAdmin(comment.getAuthorId(), actorId);
        comment.setStatus(DELETED);
        comment.setUpdatedAt(LocalDateTime.now());
        requireUpdate(comments.updateById(comment));
    }

    @Override
    public IPage<SolutionVO> listSolutions(Long problemId, long current, long size) {
        requirePublicProblem(problemId);
        LambdaQueryWrapper<Solution> query = new LambdaQueryWrapper<Solution>()
                .eq(Solution::getProblemId, problemId)
                .eq(Solution::getStatus, PUBLISHED)
                .orderByDesc(Solution::getFeatured)
                .orderByDesc(Solution::getPublishedAt);
        return mapAuthoredPage(solutions.selectPage(new Page<>(current, size), query),
                Solution::getAuthorId, this::toSolutionVO);
    }

    @Override
    public SolutionVO getSolution(Long problemId, Long solutionId) {
        requirePublicProblem(problemId);
        Solution solution = solutions.selectById(solutionId);
        if (solution == null || !problemId.equals(solution.getProblemId()) || !PUBLISHED.equals(solution.getStatus())) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        return toSolutionVO(solution);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long publishSolution(Long problemId, PublishSolutionDTO request, Long actorId) {
        requireActiveActor(actorId);
        Problem problem = requirePublicProblem(problemId);
        if (problem.getPublishedVersionId() == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "题目尚未发布版本");
        }
        LocalDateTime now = LocalDateTime.now();
        Solution solution = new Solution();
        solution.setProblemId(problemId);
        solution.setProblemVersionId(problem.getPublishedVersionId());
        solution.setAuthorId(actorId);
        solution.setTitle(request.getTitle().trim());
        solution.setContentMarkdown(request.getContentMarkdown());
        solution.setContentHtml(renderSafeHtml(request.getContentMarkdown()));
        solution.setStatus(PUBLISHED);
        solution.setFeatured(false);
        solution.setCreatedAt(now);
        solution.setPublishedAt(now);
        solution.setUpdatedAt(now);
        requireInsert(solutions.insert(solution));
        return solution.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSolution(Long problemId, Long solutionId, Long actorId) {
        Solution solution = solutions.selectById(solutionId);
        if (solution == null || !problemId.equals(solution.getProblemId()) || !PUBLISHED.equals(solution.getStatus())) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        requireOwnerOrAdmin(solution.getAuthorId(), actorId);
        solution.setStatus(DELETED);
        solution.setUpdatedAt(LocalDateTime.now());
        requireUpdate(solutions.updateById(solution));
    }

    private ForumPost requirePublishedPost(Long postId) {
        ForumPost post = posts.selectById(postId);
        if (post == null || !PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        return post;
    }

    private Problem requirePublicProblem(Long problemId) {
        Problem problem = problems.getById(problemId);
        if (problem == null || !Integer.valueOf(0).equals(problem.getStatus())) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        return problem;
    }

    private void requireOwnerOrAdmin(Long ownerId, Long actorId) {
        User actor = requireActiveActor(actorId);
        if (ownerId.equals(actorId)) {
            return;
        }
        if (!Integer.valueOf(1).equals(actor.getRole()) && !Integer.valueOf(2).equals(actor.getRole())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
    }

    private User requireActiveActor(Long actorId) {
        User actor = users.getById(actorId);
        if (actor == null || !Integer.valueOf(0).equals(actor.getStatus())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        return actor;
    }

    private void requireReadableResource(
            ForumResourceType resourceType, Long resourceId, Long actorId, boolean administrator) {
        boolean invalid = resourceType == null
                || (resourceType == ForumResourceType.GENERAL && resourceId != null)
                || (resourceType != ForumResourceType.GENERAL && (resourceId == null || resourceId <= 0));
        if (invalid) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR);
        }
        if (resourceType == ForumResourceType.PROBLEM) {
            requirePublicProblem(resourceId);
        } else if (resourceType == ForumResourceType.CONTEST) {
            contests.detail(resourceId, actorId, administrator);
        }
    }

    private void requireReadablePost(ForumPost post, Long actorId, boolean administrator) {
        requireReadableResource(
                ForumResourceType.valueOf(post.getResourceType()),
                post.getResourceId(),
                actorId,
                administrator);
    }

    private String renderSafeHtml(String markdown) {
        return HtmlUtils.htmlEscape(markdown);
    }

    private void requireInsert(int rows) {
        if (rows != 1) {
            throw new BusinessException(ResultCodeEnum.CREATE_ERROR);
        }
    }

    private void requireUpdate(int rows) {
        if (rows != 1) {
            throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    private ForumPostVO toPostVO(ForumPost source) {
        return toPostVO(source, authorName(source.getAuthorId()));
    }

    private ForumPostVO toPostVO(ForumPost source, String authorName) {
        ForumPostVO target = new ForumPostVO();
        BeanUtils.copyProperties(source, target);
        target.setAuthorName(authorName);
        return target;
    }

    private ForumCommentVO toCommentVO(ForumComment source) {
        return toCommentVO(source, authorName(source.getAuthorId()));
    }

    private ForumCommentVO toCommentVO(ForumComment source, String authorName) {
        ForumCommentVO target = new ForumCommentVO();
        BeanUtils.copyProperties(source, target);
        target.setAuthorName(authorName);
        return target;
    }

    private SolutionVO toSolutionVO(Solution source) {
        return toSolutionVO(source, authorName(source.getAuthorId()));
    }

    private SolutionVO toSolutionVO(Solution source, String authorName) {
        SolutionVO target = new SolutionVO();
        BeanUtils.copyProperties(source, target);
        target.setAuthorName(authorName);
        return target;
    }

    private String authorName(Long authorId) {
        User author = users.getById(authorId);
        return author == null ? "已注销用户" : author.getUsername();
    }

    private <S, T> IPage<T> mapAuthoredPage(
            IPage<S> source,
            Function<S, Long> authorId,
            BiFunction<S, String, T> mapper) {
        Map<Long, String> names = authorNames(source.getRecords().stream().map(authorId).toList());
        return mapPage(source, item -> mapper.apply(item, names.getOrDefault(authorId.apply(item), "已注销用户")));
    }

    private Map<Long, String> authorNames(Collection<Long> authorIds) {
        List<Long> distinctIds = authorIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return users.listByIds(distinctIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private <S, T> IPage<T> mapPage(IPage<S> source, Function<S, T> mapper) {
        Page<T> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setPages(source.getPages());
        target.setRecords(source.getRecords().stream().map(mapper).toList());
        return target;
    }
}
