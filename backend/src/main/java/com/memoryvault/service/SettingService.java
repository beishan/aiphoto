package com.memoryvault.service;

import com.memoryvault.entity.UserSetting;
import com.memoryvault.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingService {

    private final UserSettingRepository settingRepository;

    public Map<String, String> getAllSettings(Long userId) {
        return settingRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(UserSetting::getSettingKey, s -> s.getSettingValue() != null ? s.getSettingValue() : ""));
    }

    @Transactional
    public void updateSettings(Long userId, Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            UserSetting setting = settingRepository.findByUserIdAndSettingKey(userId, entry.getKey())
                    .orElseGet(() -> {
                        UserSetting s = new UserSetting();
                        s.setUserId(userId);
                        s.setSettingKey(entry.getKey());
                        return s;
                    });
            setting.setSettingValue(entry.getValue());
            settingRepository.save(setting);
        }
    }

    public String getSetting(Long userId, String key) {
        return settingRepository.findByUserIdAndSettingKey(userId, key)
                .map(UserSetting::getSettingValue)
                .orElse(null);
    }
}
