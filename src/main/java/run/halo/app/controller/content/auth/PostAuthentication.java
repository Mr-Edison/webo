package run.halo.app.controller.content.auth;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import run.halo.app.cache.AbstractStringCacheStore;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.PostCategory;
import run.halo.app.model.enums.EncryptTypeEnum;
import run.halo.app.service.CategoryService;
import run.halo.app.service.PostCategoryService;
import run.halo.app.service.PostService;

/**
 * Authentication for post.
 *
 * @author guqing
 * @date 2022-02-24
 */
@Component
public class PostAuthentication implements ContentAuthentication {

    private final PostService postService;
    private final CategoryService categoryService;
    private final PostCategoryService postCategoryService;
    private final AbstractStringCacheStore cacheStore;
    private final CategoryAuthentication categoryAuthentication;

    public PostAuthentication(PostService postService,
        CategoryService categoryService,
        PostCategoryService postCategoryService,
        AbstractStringCacheStore cacheStore,
        CategoryAuthentication categoryAuthentication) {
        this.postService = postService;
        this.categoryService = categoryService;
        this.postCategoryService = postCategoryService;
        this.cacheStore = cacheStore;
        this.categoryAuthentication = categoryAuthentication;
    }

    @Override
    public Object getPrincipal() {
        return EncryptTypeEnum.POST.getName();
    }

    @Override
    public boolean isAuthenticated(Integer postId) {
        Post post = postService.getById(postId);
        if (StringUtils.isBlank(post.getPassword())) {
            List<PostCategory> postCategories = postCategoryService.listByPostId(postId);
            boolean categoryEncrypted = postCategories.stream()
                .anyMatch(postCategory -> categoryService.isPrivate(postCategory.getCategoryId()));
            if (!categoryEncrypted) {
                return true;
            }

            boolean anyCategoryAuthenticated = postCategories.stream()
                .anyMatch(postCategory ->
                    categoryAuthentication.isAuthenticated(postCategory.getCategoryId()));
            if (anyCategoryAuthenticated) {
                return true;
            }
        }

        String sessionId = getSessionId();
        // No session is represent a client request
        if (StringUtils.isEmpty(sessionId)) {
            return false;
        }

        String cacheKey =
            buildCacheKey(sessionId, getPrincipal().toString(), String.valueOf(postId));
        return cacheStore.get(cacheKey).isPresent();
    }

    @Override
    public void setAuthenticated(Integer resourceId, boolean isAuthenticated) {
        String sessionId = getSessionId();
        // No session is represent a client request
        if (StringUtils.isEmpty(sessionId)) {
            return;
        }

        String cacheKey =
            buildCacheKey(sessionId, getPrincipal().toString(), String.valueOf(resourceId));
        if (isAuthenticated) {
            cacheStore.putAny(cacheKey, StringUtils.EMPTY, 1, TimeUnit.DAYS);
            return;
        }
        cacheStore.delete(cacheKey);
    }

    @Override
    public void clearByResourceId(Integer resourceId) {
        String resourceCachePrefix =
            StringUtils.joinWith(":", CACHE_PREFIX, getPrincipal(), resourceId);
        cacheStore.toMap().forEach((key, value) -> {
            if (StringUtils.startsWith(key, resourceCachePrefix)) {
                cacheStore.delete(key);
            }
        });
    }
}
