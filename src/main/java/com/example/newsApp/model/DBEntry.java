package com.example.newsApp.model;


import lombok.Data;

@Data
public class DBEntry {
    private String id;
    private String title;
    private String site;
    private String publishedAt;
    private String articles;
}
