package com.example.newsApp.tools;

import com.example.newsApp.model.DBEntry;
import com.example.newsApp.model.News;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;



@Service
@Slf4j
public class DataBase implements DBAPI {

    // JDBC driver name and database URL
    static final String JDBC_DRIVER = "org.h2.Driver";
    static final String DB_URL = "jdbc:h2:~/test";

    //  Database credentials
    static final String USER = "sa";
    static final String PASS = "";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private List<News> checkExists(List<News> newsBySite) {
        log.info("Проверка существования записей по сайту - " + newsBySite.get(0).getNewsSite() + "  входное количество - " + newsBySite.size());
        List<News> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS); Statement stmt = conn.createStatement()) {
            // STEP 1: Register JDBC driver
            Class.forName(JDBC_DRIVER);
            for (News news : newsBySite) {
                try {
                    //проверка существовании в базе записи с таким же айди
                    //таблица базы имеет установку primary key на столбец ID и не даст записать дубликат,
                    //но обработка эксепшонов от базы дольше чем проверка существования
                    String checkExists = "SELECT EXISTS(SELECT id FROM articles WHERE id = " + news.getId() + ")";
                    ResultSet checkResult = stmt.executeQuery(checkExists);
                    boolean testRes = false;
                    while (checkResult.next()) {
                        testRes = checkResult.getBoolean(1);
                    }
                    if(!testRes) result.add(news);
                } catch (Exception e) {
                    log.info("error in checkExists"+e.getMessage());
                }
            }
        } catch (Exception e) {
            log.info("error in checkExists" + e.getMessage());
        }
        log.info("Проверка существования записей по сайту - " + newsBySite.get(0).getNewsSite() + "  выходное количество - " + result.size());
        return result;
    }

    private HashMap<String, String> articleTxtBuffer;
    private HashMap<String, String> resultArticleTxtBuffer;

    private HashMap<String, String> up(List<News> newsBySite) {
        HashMap<String, String> buffer = new HashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        for(int offset = 0;offset<newsBySite.size();offset++){
            int finalOffset = offset;
            CompletableFuture.supplyAsync(() -> useJsoup(newsBySite.get(finalOffset).getUrl()), executorService).
                    whenComplete((input, exception)-> {
                        if (exception != null) {
                            log.info("exception occurs during using jsoup - " + exception.getMessage());
                        } else {
                            buffer.put(newsBySite.get(finalOffset).getId(),input);
                        }
                    });

        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(10,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.info("InterruptedException");
        }
        return  buffer;
    }

    private String useJsoup(String url) {
        Document document;
        String text = "";
        try {
            document = Jsoup.connect(url).get();
            text = document.getElementsByTag("body").text().replace("'", "`");
        }catch (Exception e) {
            log.info("Error in useJsoup - " + e.getMessage());
        }
        return text;
    }

    private HashMap<String, String> uploadArticleText(List<News> newsBySite) {
        log.info("Скачивание статей по сайту - " + newsBySite.get(0).getNewsSite() + "  началось.");
        articleTxtBuffer = new HashMap<>();
        String first = "";
        String second = "";
        articleTxtBuffer = up(newsBySite);
        if (articleTxtBuffer.size() > 2) {
            first = articleTxtBuffer.get(newsBySite.get(0).getId());
            second = articleTxtBuffer.get(newsBySite.get(1).getId());
        }
        try {
            log.info("Скачивание статей по сайту - " + newsBySite.get(0).getNewsSite() + "  закончилось.");
            log.info("Обрезка статей по сайту - " + newsBySite.get(0).getNewsSite() + "  началась.");
            int index = indexOfDiff(first,second);
            log.info("index = " + index);
            resultArticleTxtBuffer = new HashMap<>();
            articleTxtBuffer.keySet().forEach(key -> {
                String articleTxt;
                try {
                    articleTxt = articleTxtBuffer.get(key).substring((Math.max((index - 1), 0)));
                } catch (Exception e) {
                    articleTxt = "error during parsing";
                }
                resultArticleTxtBuffer.put(key, articleTxt);
            });

        }catch (Exception e) {
            log.info("error in uploadArticleText" + e.getMessage());
        }
        log.info("Обрезка статей по сайту - " + newsBySite.get(0).getNewsSite() + "  закончилась.");
        return resultArticleTxtBuffer;
    }

    private int indexOfDiff(String s1, String s2) {
        try {
            var byte1 = s1.toCharArray();
            var byte2 = s2.toCharArray();
            if(s1.isEmpty() || s2.isEmpty()) return 0;
            for (int i = 0; i < (byte1.length < byte2.length ? byte1.length - 1 : byte2.length - 1); i++) {
                if (byte1[i] != byte2[i]) {
                    return i;
                }
            }
        }catch (Exception e) {
            log.info("Error in indexOfDiff");
        }
        return 0;
    }

    private void dbWriteTask(List<News> newsBySite) {
        log.info("Start dbWriteTask for site - " + newsBySite.get(0).getNewsSite());
        var filteredNewsList = checkExists(newsBySite);
        var articleTextMap = uploadArticleText(filteredNewsList);
            try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS); Statement stmt = conn.createStatement()) {
                // STEP 1: Register JDBC driver
                Class.forName(JDBC_DRIVER);
                    for (News news : filteredNewsList) {
                        try {
                                String text = articleTextMap.get(news.getId());
                                String sql = "INSERT INTO ARTICLES VALUES ('"
                                        + news.getId() +
                                        "', '" + news.getTitle().replace("'", "`") +
                                        "', '" + news.getNewsSite().replace("'", "`") +
                                        "', '" + news.getPublishedAt().substring(0, news.getPublishedAt().indexOf('T')) +
                                        "', '" + text + "')";
                                stmt.executeUpdate(sql);
                        } catch (Exception e) {
                            log.info(news.getId());
                            log.info("error in dbWriteTask - " + e.getMessage());
                        }
                    }
            } catch (Exception e) {
                // Handle errors for Class.forName
                e.printStackTrace();
                log.info("error in dbWriteTask - " + e.getMessage());
            }
        log.info("Completed push to DB for site - " + newsBySite.get(0).getNewsSite());
    }

    @Override
    public void pushToDataBase(List<News> newsBySite) {
        log.info("start DB task for site - " + newsBySite.get(0).getNewsSite());
        executorService.execute(()->dbWriteTask(newsBySite));
    }

    @Override
    public DBEntry getEntry(String id) {
        DBEntry result = new DBEntry();
        List<DBEntry> tempList = getListFromDB("SELECT * FROM articles WHERE id = " + id);
        if(tempList.size()>=1) {
            result = tempList.get(0);
        }
        return result;

    }

    @Override
    public List<DBEntry> getBySite(String site) {
        return getListFromDB("SELECT * FROM articles WHERE news_site = '" + site +"'");
    }

    private List<DBEntry> getListFromDB(String sql) {
        List<DBEntry> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS); Statement stmt = conn.createStatement()) {
            // STEP 1: Register JDBC driver
            Class.forName(JDBC_DRIVER);
            try {
                ResultSet resultSet = stmt.executeQuery(sql);
                while (resultSet.next()) {
                    DBEntry tempEntry = new DBEntry();
                    tempEntry.setId(resultSet.getString("id"));
                    tempEntry.setTitle(resultSet.getString("title"));
                    tempEntry.setSite(resultSet.getString("news_site"));
                    tempEntry.setPublishedAt(resultSet.getString("published_date"));
                    tempEntry.setArticles(resultSet.getString("article"));
                    result.add(tempEntry);
                }
            } catch (Exception e) {
                log.info("error in getEntry - " + e.getMessage());
            }
        } catch (Exception e) {
            // Handle errors for Class.forName
            e.printStackTrace();
            log.info("error in getEntry - " + e.getMessage());
        }
        return result;
    }

    @Override
    public List<DBEntry> getAll() {
        return getListFromDB("SELECT * FROM articles");
    }
}
