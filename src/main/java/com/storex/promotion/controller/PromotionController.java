package com.storex.promotion.controller;

import com.storex.promotion.model.Banner;
import com.storex.promotion.service.PromotionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/banners")
public class PromotionController {

    private final PromotionService promotionService;

    // Inject PromotionService
    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    // Endpoint lay banner trang chu cho StoreX
    @GetMapping("/active")
    public Mono<Banner> getActiveBanner() {
        return promotionService.getActiveBanner();
    }
}
