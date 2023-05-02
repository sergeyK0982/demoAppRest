package com.example.newsApp.service;


import com.example.newsApp.model.DBEntry;
import com.example.newsApp.model.News;
import com.example.newsApp.tools.DBAPI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.example.newsApp.tools.AppConfiguration;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;




import static java.util.stream.Collectors.groupingBy;


@Service
@Slf4j
public class NewsServiceImpl implements NewsService {

    private ConcurrentHashMap<String, List<News>> buffer = new ConcurrentHashMap<>();

    DBAPI db;

    @Autowired
    public NewsServiceImpl(DBAPI db) {
        this.db = db;
    }

    @Override
    public void uploadNews(AppConfiguration config) {
        log.info(config.toString());

        ExecutorService executorService = Executors.newFixedThreadPool(config.getPullSize());
        for(int offset = 0;offset<=config.getLimit();offset+=config.getPullLimit()){
            int finalOffset = offset;
            CompletableFuture.supplyAsync(() -> get(config.getPullLimit(), finalOffset, config.getBlackList()), executorService)
                    .whenComplete((input, exception)-> {
                        if (exception != null) {
                            log.info("exception occurs" + exception.getMessage());
                        } else {
                            log.info("Start sorting and group for thread - " + Thread.currentThread().getName());
                            handleThreadResult(input.stream()
                                            .collect(groupingBy(News::getNewsSite)),
                                    config.getSiteLimit());

                        }
                    });

        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(10, TimeUnit.MINUTES);
            log.info("print all to DB");
            buffer.forEach((key, value) -> db.pushToDataBase(value));
        } catch (Exception e) {
            log.info("Exception during executorService Termination - " + e.getMessage());
        }
    }

    private void handleThreadResult(Map<String, List<News>> newsBySite, int siteLimit) {
        for(String site:newsBySite.keySet()){
            log.info("handling site - " + site);
            var tempList = buffer.get(site);
            // удаление обрабатываемого сектора из буфера чтобы предовратить дублирование записей,
            // поскольку два и более потоков могут одновременно забрать себе пул по данному сайту
            buffer.remove(site);
            if(tempList == null) {
                buffer.put(site, newsBySite.get(site).
                        stream().
                        sorted(new DateComparator()).
                        collect(Collectors.toList()));
                log.info("add new list in buffer for site - " + site);
            } else {
                log.info("Size of list in buffer - " + tempList.size());
                tempList.addAll(newsBySite.get(site));
                buffer.put(site, tempList.
                        stream().
                        sorted(new DateComparator()).
                        collect(Collectors.toList()));
                log.info("Updated list for site - " + site + "   size is - " + tempList.size());
                if(tempList.size()>siteLimit) {
                    log.info("Need to write in DB for site - " + site);
                    //если не удалять сектор при взятии его из буфера, то вот здесь возможен сценарий когда один поток набрал лимит и удалил,
                    // а второй поток записал удаленное обратно со своими добавками
                    buffer.remove(site);
                    db.pushToDataBase(tempList);
                }
            }
        }
    }

    @Override
    public void printNews() {
        log.info("Print");
        buffer.forEach((key, value) -> {
            log.info("********************************");
            log.info(key);
            value.forEach(System.out::println);
        });
    }

    @Override
    public DBEntry getEntry(String id) {
        return db.getEntry(id);
    }

    @Override
    public List<DBEntry> getBySite(String site) {
        return db.getBySite(site);
    }

    @Override
    public List<DBEntry> getAll() {
        return db.getAll();
    }


    private List<News> get(int limit, int offset, List<String> blackList) {
        Thread.currentThread().setName("Thread_offset - " + offset);
        RestTemplate restTemplate = new RestTemplate();
        String requestURI = "https://api.spaceflightnewsapi.net/v3/articles?_limit="+limit+"&_start="+offset;
        News[] posts = restTemplate.getForObject(requestURI, News[].class);
        return Arrays.stream(posts).
                filter(value -> blackList.stream().
                        noneMatch(stopWord -> value.getTitle().
                                contains(stopWord)))
                .collect(Collectors.toList());
    }

    class DateComparator implements Comparator<News> {

        @Override
        public int compare(News o1, News o2) {
            LocalDateTime dateTime1 = LocalDateTime.parse(o1.getPublishedAt().substring(0,o1.getPublishedAt().length()-1));
            LocalDateTime dateTime2 = LocalDateTime.parse(o2.getPublishedAt().substring(0,o1.getPublishedAt().length()-1));
            return dateTime1.compareTo(dateTime2);
        }
    }
}

