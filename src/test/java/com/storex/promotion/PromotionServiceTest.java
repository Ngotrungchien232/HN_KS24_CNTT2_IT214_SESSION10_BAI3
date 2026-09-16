package com.storex.promotion;

import com.storex.promotion.model.Banner;
import com.storex.promotion.service.PromotionService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class PromotionServiceTest {

    private MockWebServer mockWebServer;
    private PromotionService promotionService;

    // Khoi tao MockWebServer truoc moi test case
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        promotionService = new PromotionService(webClient);
    }

    // Dong MockWebServer sau khi test xong
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // Test truong hop promotion service tra ve banner thanh cong
    @Test
    void testGetActiveBanner_Success() {
        String jsonResponse = "{\"id\":\"b1\",\"title\":\"Sale 50%\",\"content\":\"Giam gia toan san\",\"imageUrl\":\"/img/sale50.png\",\"status\":\"ACTIVE\"}";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse));

        Mono<Banner> bannerMono = promotionService.getActiveBanner();

        StepVerifier.create(bannerMono)
                .expectNextMatches(banner -> 
                        "b1".equals(banner.getId()) &&
                        "Sale 50%".equals(banner.getTitle()) &&
                        "ACTIVE".equals(banner.getStatus())
                )
                .verifyComplete();
    }

    // Test truong hop promotion service phan hoi cham qua 2 giay se fallback banner mac dinh
    @Test
    void testGetActiveBanner_Timeout_ReturnsDefaultBanner() {
        String jsonResponse = "{\"id\":\"b2\",\"title\":\"Flash Sale\",\"content\":\"Uu dai\",\"imageUrl\":\"/img/sale.png\",\"status\":\"ACTIVE\"}";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
                .setBodyDelay(3, TimeUnit.SECONDS));

        Mono<Banner> bannerMono = promotionService.getActiveBanner();

        StepVerifier.create(bannerMono)
                .expectNextMatches(banner -> 
                        "default".equals(banner.getId()) &&
                        banner.getTitle().contains("Khuyến mãi đang được cập nhật") &&
                        "DEFAULT".equals(banner.getStatus())
                )
                .verifyComplete();
    }

    // Test truong hop promotion service bi loi 500 se fallback banner mac dinh ma khong crash
    @Test
    void testGetActiveBanner_ServiceError_ReturnsDefaultBanner() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        Mono<Banner> bannerMono = promotionService.getActiveBanner();

        StepVerifier.create(bannerMono)
                .expectNextMatches(banner -> 
                        "default".equals(banner.getId()) &&
                        banner.getTitle().contains("Khuyến mãi đang được cập nhật")
                )
                .verifyComplete();
    }

    // Test truong hop promotion service tra ve rong
    @Test
    void testGetActiveBanner_EmptyResponse_ReturnsDefaultBanner() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(""));

        Mono<Banner> bannerMono = promotionService.getActiveBanner();

        StepVerifier.create(bannerMono)
                .expectNextMatches(banner -> "default".equals(banner.getId()))
                .verifyComplete();
    }
}
