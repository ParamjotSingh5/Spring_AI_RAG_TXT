package com.paramjot.scan_document.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ScanDocument {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("classpath:/prompts/rag-prompt-template.st")
    private Resource ragPromptTemplate;

    public ScanDocument(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
                .build();
        this.vectorStore = vectorStore;
    }

    @PostMapping("/query_document")
    public ResponseEntity<String> queryDocument(@RequestBody String message) {
        List<Document> similarDocuments = vectorStore.similaritySearch(SearchRequest.builder().query(message).topK(2).build());
        assert similarDocuments != null;
        List<String> addOnContext = similarDocuments.stream().map(Document::getText).toList();


        PromptTemplate promptTemplate = new PromptTemplate(ragPromptTemplate);
        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("input", message);
        promptParameters.put("documents", addOnContext);

        Prompt prompt = promptTemplate.create(promptParameters);

        return new ResponseEntity<>(chatClient.prompt(prompt).call().content(), org.springframework.http.HttpStatus.OK);
    }



}
