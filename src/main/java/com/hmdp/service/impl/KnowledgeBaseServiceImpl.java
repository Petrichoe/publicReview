package com.hmdp.service.impl;

import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IKnowledgeBaseService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 当服务启动时，自动加载所有博客文章到知识库中
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService {

    @Resource
    private IBlogService blogService;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    private EmbeddingStoreIngestor ingestor;

    private DocumentSplitter documentSplitter;



    // --- 2. 使用 @PostConstruct 初始化 Ingestor ---
    @PostConstruct
    private void init() {
        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 0))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // 系统启动时，执行全量加载,手动调用
        //ingestAllBlogs();

    }

    @Override
    public void ingestAllBlogs() {
        List<Blog> blogs = blogService.list();
        for (Blog blog : blogs) {
            // 为避免阻塞，我们让它也异步执行
            ingestSingleBlog(blog);
        }
        System.out.println("知识库全量加载完成，共处理 " + blogs.size() + " 篇博客。");
    }


    /**
     * 使用 @Async 注解，使其成为一个异步方法。
     * Spring 会在独立的线程中执行它，不会阻塞调用者（例如Controller）。
     */
    @Async("taskExecutor") // 指定使用我们稍后定义的线程池
    @Override
    public void ingestSingleBlog(Blog blog) {
        try {
            log.info("Starting async ingestion for blog ID: {}", blog.getId());
            Document document = Document.from(blog.getTitle() + "\n" + blog.getContent());
            document.metadata().put("blog_id", String.valueOf(blog.getId()));
            document.metadata().put("user_id", String.valueOf(blog.getUserId()));
            document.metadata().put("text", document.text());

            ingestor.ingest(document);

            log.info("Successfully ingested blog ID: {}", blog.getId());
        } catch (Exception e) {
            log.error("Error during async ingestion for blog ID: {}", blog.getId(), e);
        }
    }

    // 新增一个私有方法，用于根据内容生成SHA-266哈希值作为ID
    private static String generateIdFrom(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void removeFromKnowledgeBase(Long blogId) {
        // Langchain4j 的 EmbeddingStore 目前没有直接通过元数据删除的API
        // 实际生产中，若使用专业向量数据库（如 Milvus, Pinecone），它们会提供基于ID或元数据的删除接口。
        // 此处为功能占位，具体实现依赖于所选的 EmbeddingStore。
        System.out.println("请求从知识库删除博客 [ID: " + blogId + "]，具体实现需根据向量数据库API。");
    }
}