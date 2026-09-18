package com.shiguang.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateVisibilityRequest {

    @NotBlank(message = "可见性不能为空")
    private String visibility;
}
