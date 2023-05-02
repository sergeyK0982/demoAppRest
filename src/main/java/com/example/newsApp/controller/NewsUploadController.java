package com.example.newsApp.controller;

import com.example.newsApp.model.Config;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.newsApp.service.NewsService;
import com.example.newsApp.tools.AppConfiguration;
import java.util.List;

@RestController
@Slf4j
public class NewsUploadController {

    private NewsService service;

    @Autowired
    public NewsUploadController(NewsService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload")
    public
    ResponseEntity<?> config(@RequestBody Config config) {
        try {
            int limit = Integer.parseInt(config.getLimit());
            int pullSize = Integer.parseInt(config.getPullSize());
            int pullLimit = Integer.parseInt(config.getPullLimit());
            List<String> blackList = config.getBlackList();
            int siteLimit = Integer.parseInt(config.getSiteLimit());
            service.uploadNews(new AppConfiguration(blackList, limit, pullLimit, pullSize, siteLimit));
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(),HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping(value = "/check/")
    public ResponseEntity<?> test()
    {
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping(value = "/getEntry/{id}")
    public ResponseEntity<?> getEntry(@PathVariable String id)
    {
        log.info(id);
        return new ResponseEntity<>(service.getEntry(id),HttpStatus.OK);
    }

    @GetMapping(value = "/getBySite/{site}")
    public ResponseEntity<?> getBySite(@PathVariable String site)
    {
        log.info(site);
        return new ResponseEntity<>(new DataList(service.getBySite(site)),HttpStatus.OK);
    }

    @GetMapping(value = "/printNews/")
    public ResponseEntity<?> printNews()
    {
        service.printNews();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping(value = "/getAllNews/")
    public ResponseEntity<?> getAll()
    {
        log.info("getAll");
       return new ResponseEntity<>(new DataList(service.getAll()), HttpStatus.OK);
    }

    @Data
    public class DataList<T> {
        private List<T> items;
        private int count;

        DataList(List<T> items) {
            this.items = items;
            this.count = items.size();
        }
    }
}
