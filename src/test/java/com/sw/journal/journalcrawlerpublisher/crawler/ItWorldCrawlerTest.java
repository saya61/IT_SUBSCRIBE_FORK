package com.sw.journal.journalcrawlerpublisher.crawler;

import com.sw.journal.journalcrawlerpublisher.domain.*;
import com.sw.journal.journalcrawlerpublisher.repository.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Random;


@SpringBootTest
class ItWorldCrawlerTest {
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ImageRepository imageRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private TagArticleRepository tagArticleRepository;
    @Autowired
    private ArticleRankRepository articleRankRepository;

    public boolean crawlArticles(String articleUrl){
        Connection conn = Jsoup.connect(articleUrl);
        try {
            Document doc = conn.get();
            Elements elem = doc.select(".row>.section-content"); //set 자료형으로 관리해도 됨
            // 카테고리 1개
//            String articleCategory = elem.select(".font-color-primary-1").first().text();
            // 기사 제목
            String articleTitle = elem.select(".node-title").text();
            // 기사 이미지
            //String imgUrl = elem.select(".node-body img").attr("src");
            // 기사 내용
            String articleContent = elem.select(".node-body").text();

            // 1. 기사 중복 검사
            //assert ourArticleRepository.findBySource(articleUrl) == null;   // 서비스에서는 쓰지 않는게 좋다
            // 서비스에서는 junit을 사용하지 않는다
            // return null로 반환하고 null을 받는곳을 만든다
            // count로 중복 값이 들어오면 저장해서 insite를 만든다
            if(articleRepository.findBySource(articleUrl).isPresent()){
                return false;
            }

            // 2. 카테고리 기존 내역 없을 경우만 저장
            // for 카테고리 string literal 배열
            // 있는 경우
//            Optional<Category> optionalCategory = categoryRepository.findByName(articleCategory);
//            Category category = new Category();
//            if(optionalCategory.isEmpty()) {
//                category.setName(articleCategory);  // 카테고리 생성 (유니크) 이미 있는 값이면 Repository로 save할 때 판담됨 (try - catch로 사용해라)
//                try {
//                    category = categoryRepository.save(category);  // 카테고리 저장
//                } catch (Exception ex) {
//                    System.out.println(ex.getMessage());
//                    // 카테고리 중복은 종료사유 아님
//                }
//            } else {
//                category = optionalCategory.get();
//            }
            // 정해진 카테고리에서 랜덤하게 가져옴
            Random randomCategory = new Random();
            Optional<Category> optionalCategory = categoryRepository.findById(randomCategory.nextLong(9)+1L);
            Category category = new Category();
            if(optionalCategory.isPresent()){
                category = optionalCategory.get();
            }

            // 3. 기사 객체 생성 및 저장
            Article article = new Article();
            article.setSource(articleUrl);   // 기사 Url 저장
            // 카테고리 찾기
            article.setCategory(category);   // 기사 카테고리 설정 위에서 유무 검사 후 저장까지 완료했으므로 .get()사용
            article.setTitle(articleTitle);  // 기사 제목
            article.setContent(articleContent);  // 기사 내용
            article.setPostDate(LocalDateTime.now());  // 게시 날짜
            Article savedArticle;
            try {
                savedArticle = articleRepository.save(article);
            } catch (DataIntegrityViolationException ex) {
                System.out.println(ex.getMessage());
                return false;
            }

            // 4. 이미지 저장
            // 이미지가 없는 기사도 있음
            for(Element e : doc.select(".node-body .image")) {
                String imgUrl = e.select("img").attr("src");
                if(!imgUrl.isEmpty()) {
                    Image image = new Image();
                    image.setImgUrl("https://www.itworld.co.kr" + imgUrl);
                    image.setArticle(savedArticle);
                    imageRepository.save(image);
                }
                else {
                    break;
                }
            }

            // 5-1. 태그 각각 저장
            for(Element e : doc.select(".row>.section-content>.py-5>.ms-2>a")) {
                String tagName = e.select("span").text();
                if(tagRepository.findByName(tagName).isEmpty()) {
                    Tag tag = new Tag();
                    tag.setName(tagName);
                    try {
                        tagRepository.save(tag);
                    } catch (DataIntegrityViolationException ex) {
                        System.out.println(ex.getMessage());
                        return false;
                    }
                }
                // 5-2. 태그와 기사 중계 테이블 저장
                TagArticle tagArticle = new TagArticle();
                tagArticle.setArticle(savedArticle);
                tagArticle.setTag(tagRepository.findByName(tagName).get()); // 위에서 유무 검사 후 저장된 값 가져오기
                tagArticleRepository.save(tagArticle);
            }

            // 6. 기사 랭크(조회수) 저장
            if(articleRankRepository.findByArticle(savedArticle).isEmpty()) {
                Random random = new Random();
                ArticleRank articleRank = new ArticleRank();
                articleRank.setArticle(savedArticle);
                articleRank.setViews((long) random.nextInt(100));
                articleRank.setIsActive(true);
                try {
                    articleRankRepository.save(articleRank);
                } catch (DataIntegrityViolationException ex) {
                    System.out.println(ex.getMessage());
                    return false;
                }
            }

            return true;

        } catch (IOException e) {
            System.err.println("페이지 요청 중 에러 발생");
            e.printStackTrace();
            return false;
        }
    }

    //@Scheduled(cron = "0 0 4 * * *")
    @Test
    public void crawlingItWorld(){
        String URL = "https://www.itworld.co.kr/rss/feed/index.php";
        String compareURL = "https://www.itworld.co.kr/news/";  // 문자 0~30

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime today = LocalDateTime.parse(LocalDateTime.now().format(formatter), formatter);
        LocalDateTime yesterday = today.minusDays(7);

        Connection conn = Jsoup.connect(URL);
        try {
            Document doc = conn.get();
            Elements elems = doc.select("item"); //set 자료형으로 관리해도 됨
            //System.out.println(elems);
            for (Element elem : elems) {
                //System.out.println(elem.select("pubDate").text());
                String link = elem.select("link").text();   // 기사의 링크
                String pubDate = elem.select("pubDate").text(); // 기사 발행 시간
                LocalDateTime articleTime = LocalDateTime.parse(pubDate, formatter);
                // 시간 비교
                if(articleTime.isBefore(today) && articleTime.isAfter(yesterday)) {
                    // 크롤링 가능한 기간일 경우 실행됨
                    // 저장 유무를 전달받음 이후 count로 중복 기사가 몇번 발생했는지 저장하여 insite를 만듬 로그 파일 만들기 logforj
                    // rss에서 크롤링을 수행할 때 원하지 않는 url을 수행하지 않기 위함
                    if(compareURL.regionMatches(0, link, 0, 31)) {
                        boolean saved = crawlArticles(link);
                    }
                }
                else {
                    //System.out.println("날짜가 지났습니다.");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("페이지 요청 중 에러 발생");
            e.printStackTrace();
        }

    }
}