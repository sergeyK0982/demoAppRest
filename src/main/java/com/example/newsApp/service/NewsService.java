package com.example.newsApp.service;



import com.example.newsApp.model.DBEntry;
import com.example.newsApp.tools.AppConfiguration;

import java.util.List;

public interface NewsService {
    void uploadNews(AppConfiguration config);
    void printNews();

    DBEntry getEntry(String id);

    List<DBEntry> getBySite(String site);

    List<DBEntry> getAll();
}
