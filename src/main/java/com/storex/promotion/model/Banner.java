package com.storex.promotion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Model bieu dien thong tin banner khuyen mai
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {
    private String id;
    private String title;
    private String content;
    private String imageUrl;
    private String status;
}
