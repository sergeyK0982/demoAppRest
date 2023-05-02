package com.example.newsApp.tools;

import com.example.newsApp.model.DBEntry;
import com.example.newsApp.model.News;

import java.util.List;

public interface DBAPI {
    void pushToDataBase(List<News> newsBySite);

    DBEntry getEntry(String id);

    List<DBEntry> getBySite(String site);

    List<DBEntry> getAll();
}
