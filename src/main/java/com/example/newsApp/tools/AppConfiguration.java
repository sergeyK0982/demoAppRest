package com.example.newsApp.tools;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;


@AllArgsConstructor
@Getter
@ToString
public class  AppConfiguration {

    // stop words for news headlines
    private List<String> blackList;
    //main upload limit
    private int limit;
    //upload limit for each pull
    private int pullLimit;
    // size of thread pull
    private int pullSize;
    //site limit for buffer. after exceeding the limit, articles are downloaded and written to the database. are removed from the buffer
    private int siteLimit;

}
