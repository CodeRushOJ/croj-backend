package com.zephyr.croj.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.mapper.ForumCategoryMapper;
import com.zephyr.croj.mapper.ForumCommentMapper;
import com.zephyr.croj.mapper.ForumPostMapper;
import com.zephyr.croj.mapper.SolutionMapper;
import com.zephyr.croj.model.dto.CreateForumPostDTO;
import com.zephyr.croj.model.dto.PublishSolutionDTO;
import com.zephyr.croj.model.entity.ForumCategory;
import com.zephyr.croj.model.entity.ForumPost;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.Solution;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.service.ProblemService;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.service.impl.CommunityContentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityContentServiceTest {

    @Mock private ForumCategoryMapper categories;
    @Mock private ForumPostMapper posts;
    @Mock private ForumCommentMapper comments;
    @Mock private SolutionMapper solutions;
    @Mock private UserService users;
    @Mock private ProblemService problems;

    private CommunityContentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunityContentServiceImpl(categories, posts, comments, solutions, users, problems);
    }

    @Test
    void creatingAPostUsesTheAuthenticatedAuthorAndEscapesRenderedHtml() {
        User actor = new User();
        actor.setId(7L);
        actor.setStatus(0);
        when(users.getById(7L)).thenReturn(actor);
        ForumCategory category = new ForumCategory();
        category.setId(3L);
        when(categories.selectById(3L)).thenReturn(category);
        when(posts.insert(any(ForumPost.class))).thenAnswer(invocation -> {
            invocation.<ForumPost>getArgument(0).setId(41L);
            return 1;
        });
        CreateForumPostDTO request = new CreateForumPostDTO();
        request.setCategoryId(3L);
        request.setTitle("Segment tree discussion");
        request.setContentMarkdown("<script>alert(1)</script> **idea**");

        long id = service.createPost(request, 7L);

        assertEquals(41L, id);
        ArgumentCaptor<ForumPost> saved = ArgumentCaptor.forClass(ForumPost.class);
        verify(posts).insert(saved.capture());
        assertEquals(7L, saved.getValue().getAuthorId());
        assertEquals("PUBLISHED", saved.getValue().getStatus());
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt; **idea**", saved.getValue().getContentHtml());
    }

    @Test
    void anotherUserCannotDeleteAForumPost() {
        ForumPost post = new ForumPost();
        post.setId(9L);
        post.setAuthorId(7L);
        post.setStatus("PUBLISHED");
        when(posts.selectById(9L)).thenReturn(post);
        User actor = new User();
        actor.setId(8L);
        actor.setRole(0);
        when(users.getById(8L)).thenReturn(actor);

        assertThrows(BusinessException.class, () -> service.deletePost(9L, 8L));
    }

    @Test
    void publishingASolutionPinsTheCurrentlyPublishedProblemVersion() {
        User actor = new User();
        actor.setId(7L);
        actor.setStatus(0);
        when(users.getById(7L)).thenReturn(actor);
        Problem problem = new Problem();
        problem.setId(11L);
        problem.setStatus(0);
        problem.setPublishedVersionId(23L);
        when(problems.getById(11L)).thenReturn(problem);
        when(solutions.insert(any(Solution.class))).thenAnswer(invocation -> {
            invocation.<Solution>getArgument(0).setId(57L);
            return 1;
        });
        PublishSolutionDTO request = new PublishSolutionDTO();
        request.setTitle("Linear-time proof");
        request.setContentMarkdown("Proof by invariant.");

        assertEquals(57L, service.publishSolution(11L, request, 7L));
        ArgumentCaptor<Solution> saved = ArgumentCaptor.forClass(Solution.class);
        verify(solutions).insert(saved.capture());
        assertEquals(23L, saved.getValue().getProblemVersionId());
        assertEquals("PUBLISHED", saved.getValue().getStatus());
    }

    @Test
    void aDisabledAccountCannotPublishWithAnOtherwiseValidToken() {
        User actor = new User();
        actor.setId(7L);
        actor.setStatus(1);
        when(users.getById(7L)).thenReturn(actor);
        CreateForumPostDTO request = new CreateForumPostDTO();
        request.setCategoryId(3L);
        request.setTitle("Should be rejected");
        request.setContentMarkdown("Disabled account content");

        assertThrows(BusinessException.class, () -> service.createPost(request, 7L));
    }
}
