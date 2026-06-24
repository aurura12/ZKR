package com.smartlab.erp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NaturalLanguageParseRequest {
    @NotBlank(message = "自然语言文本不能为空")
    private String text;
}
