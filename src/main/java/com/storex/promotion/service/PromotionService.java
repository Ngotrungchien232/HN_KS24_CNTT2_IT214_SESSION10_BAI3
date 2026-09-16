package com.storex.promotion.service;

import com.storex.promotion.model.Banner;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class PromotionService {

    private final WebClient webClient;

    // Inject WebClient da duoc cau hinh timeout
    public PromotionService(WebClient webClient) {
        this.webClient = webClient;
    }

    // Lay banner active non-blocking voi timeout 2 giay va fallback an toan
    public Mono<Banner> getActiveBanner() {
        String url = "/api/banners/active";

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Banner.class)
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(throwable -> Mono.just(getDefaultBanner()))
                .defaultIfEmpty(getDefaultBanner());
    }

    // Banner mac dinh khi timeout hoac promotion-service gap su co
    public Banner getDefaultBanner() {
        return Banner.builder()
                .id("default")
                .title("Khuyến mãi đang được cập nhật")
                .content("Khuyến mãi đang được cập nhật")
                .imageUrl("/images/default-banner.png")
                .status("DEFAULT")
                .build();
    }
}
