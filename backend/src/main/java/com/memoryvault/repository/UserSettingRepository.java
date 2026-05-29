package com.memoryvault.repository;

import com.memoryvault.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {
    List<UserSetting> findAllByUserId(Long userId);
    Optional<UserSetting> findByUserIdAndSettingKey(Long userId, String settingKey);
}
