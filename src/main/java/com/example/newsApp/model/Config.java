package com.example.newsApp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;


import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    String limit;
    String pullSize;
    String pullLimit;
    List<String> blackList;
    String siteLimit;
}
