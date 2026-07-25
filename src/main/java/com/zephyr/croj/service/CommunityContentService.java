package com.zephyr.croj.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zephyr.croj.model.dto.CreateForumCommentDTO;
import com.zephyr.croj.model.dto.CreateForumPostDTO;
import com.zephyr.croj.model.dto.PublishSolutionDTO;
import com.zephyr.croj.model.entity.ForumCategory;
import com.zephyr.croj.model.vo.ForumCommentVO;
import com.zephyr.croj.model.vo.ForumPostVO;
import com.zephyr.croj.model.vo.SolutionVO;
import java.util.List;

public interface CommunityContentService {
    List<ForumCategory> listCategories();
    IPage<ForumPostVO> listPosts(Long categoryId, long current, long size);
    ForumPostVO getPost(Long postId);
    long createPost(CreateForumPostDTO request, Long actorId);
    void deletePost(Long postId, Long actorId);
    IPage<ForumCommentVO> listComments(Long postId, long current, long size);
    long createComment(Long postId, CreateForumCommentDTO request, Long actorId);
    void deleteComment(Long commentId, Long actorId);
    IPage<SolutionVO> listSolutions(Long problemId, long current, long size);
    SolutionVO getSolution(Long problemId, Long solutionId);
    long publishSolution(Long problemId, PublishSolutionDTO request, Long actorId);
    void deleteSolution(Long problemId, Long solutionId, Long actorId);
}
