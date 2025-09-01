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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 当服务启动时，自动加载所有博客文章到知识库中
 */
@Service
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService {

    @Resource
    private IBlogService blogService;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    //private EmbeddingStoreIngestor ingestor;

    private DocumentSplitter documentSplitter;

    // 使用 @PostConstruct 初始化 ingestor，避免重复创建
    /*@PostConstruct
    private void init() {
        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 0))//设置文本分割器
                .embeddingModel(embeddingModel) //将文本转为数学向量
                .embeddingStore(embeddingStore) //将向量保存到向量数据库中
                .build();
        
        // 系统启动时，执行全量加载
        ingestAllBlogs();
    }*/
    @PostConstruct
    private void init() {
        // 只初始化分割器
        this.documentSplitter = DocumentSplitters.recursive(300, 0);

        // 系统启动时，执行全量加载
        ingestAllBlogs();
    }

    @Override
    public void ingestAllBlogs() {
        List<Blog> blogs = blogService.list();
        for (Blog blog : blogs) {
            ingestSingleBlog(blog);
        }
        System.out.println("知识库全量加载完成，共处理 " + blogs.size() + " 篇博客。");
    }

/*    @Override
    public void ingestSingleBlog(Blog blog) {
        Document document = Document.from(blog.getTitle() + "\n" + blog.getContent());
        document.metadata().put("blog_id", blog.getId());
        document.metadata().put("user_id", blog.getUserId());

        // 1. 手动分割文档
        List<TextSegment> segments = documentSplitter.split(document);

        // 2. 为每个 segment 生成唯一的、可重复的ID，并进行向量化和存储
        for (TextSegment segment : segments) {
            // 使用内容生成哈希值作为ID
            String id = generateIdFrom(segment.text());
            segment.metadata().put("text", segment.text()); // 可选：将ID也存入元数据

            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(id, embedding);
        }

        System.out.println("博客 [ID: " + blog.getId() + "] 已加载到知识库。");
    }*/

    @Override
    public void ingestSingleBlog(Blog blog) {
        Document document = Document.from(blog.getTitle() + "\n" + blog.getContent());
        document.metadata().put("blog_id", String.valueOf(blog.getId()));
        document.metadata().put("user_id", String.valueOf(blog.getUserId()));
        // 1. 手动分割文档
        List<TextSegment> segments = documentSplitter.split(document);
        // 2. 为每个 segment 生成唯一的、可重复的ID，并进行向量化和存储
        for (TextSegment segment : segments) {
            segment.metadata().put("text", segment.text());

            // 1. 生成 Embedding，这个 Embedding 对象内部会携带 segment 的元数据
            Embedding embedding = embeddingModel.embed(segment).content();

            // 2. 生成唯一的 ID
            String id = generateIdFrom(segment.text());

            // 3. 使用正确的、IDE提示的 add 方法！
            embeddingStore.add(id, embedding);
        }
        System.out.println("博客 [ID: " + blog.getId() + "] 已加载到知识库。");
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