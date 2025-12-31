package com.paramjot.scan_document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class RagConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

    private String vectorStoreName = "vectorstore.json";

    @Value("classpath:/docs/k8s_resource_types_readme_content/resource_type_in_kubernetes.md")
    private Resource pdf_doc;

    @Bean
    SimpleVectorStore buildSimpleVectorStore(EmbeddingModel embeddingModel) {

        SimpleVectorStore simpleVectorStore  = SimpleVectorStore.builder(embeddingModel).build();

        var vectorStoreFile = getVectorStoreFile();

        if (vectorStoreFile.exists()) {
            log.info("Vector Store File Exists,");
            simpleVectorStore.load(vectorStoreFile);
        } else {
            log.info("Vector Store File Does Not Exist, loading documents");
            TextReader textReader = new TextReader(pdf_doc);
            textReader.getCustomMetadata().put("filename", "resource_type_in_kubernetes.md");
            List<Document> documents = textReader.get();
            TextSplitter textSplitter = TokenTextSplitter.builder().withChunkSize(256).withMaxNumChunks(256).build();
            List<Document> splitDocuments = textSplitter.apply(documents);
            simpleVectorStore.add(splitDocuments);
            simpleVectorStore.save(vectorStoreFile);
        }

        return simpleVectorStore;

    }

    private File getVectorStoreFile() {
        Path path = Paths.get("src", "main", "resources");
        String absolutePath = path.toFile().getAbsolutePath() + "/" + vectorStoreName;
        return new File(absolutePath);
    }
}
