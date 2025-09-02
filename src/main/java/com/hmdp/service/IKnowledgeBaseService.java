package com.hmdp.service;

import com.hmdp.entity.Blog;

public interface IKnowledgeBaseService {

    /**
     * 将所有的博客文章加载（摄入）到知识库中。
     * 这个方法通常在系统启动时调用。
     */
    void ingestAllBlogs();

    /**
     * 将单篇博客文章加载到知识库中。
     * 这可以在用户发布新博客时调用，实现增量更新。
     * @param blog 新发布的博客文章
     */
    void ingestSingleBlog(Blog blog);

    /**
     * 从知识库中移除一篇博客文章。
     * 这可以在用户删除博客时调用。
     * @param blogId 被删除的博客ID
     */
    void removeFromKnowledgeBase(Long blogId);
}