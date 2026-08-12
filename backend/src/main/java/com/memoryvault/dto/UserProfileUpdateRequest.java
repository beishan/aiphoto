package com.memoryvault.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserProfileUpdateRequest {

    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    @Size(max = 100, message = "心情不能超过100个字符")
    private String mood;

    @Past(message = "出生日期必须早于今天")
    private LocalDate birthDate;

    @Size(max = 1000, message = "照片偏好不能超过1000个字符")
    private String photoPreferences;

    @Size(max = 1000, message = "备注不能超过1000个字符")
    private String notes;
}
