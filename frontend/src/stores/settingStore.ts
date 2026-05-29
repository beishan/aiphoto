import { defineStore } from 'pinia'
import { ref } from 'vue'
import { settingApi } from '@/api/settingApi'

export const useSettingStore = defineStore('setting', () => {
  const settings = ref<Record<string, string>>({})
  const loaded = ref(false)

  async function fetchSettings() {
    try {
      const { data } = await settingApi.getAll()
      settings.value = data
      loaded.value = true
    } catch (e) {
      console.error('Failed to load settings', e)
    }
  }

  async function updateSettings(newSettings: Record<string, string>) {
    await settingApi.update(newSettings)
    settings.value = { ...settings.value, ...newSettings }
  }

  function getSetting(key: string, defaultValue = ''): string {
    return settings.value[key] ?? defaultValue
  }

  return { settings, loaded, fetchSettings, updateSettings, getSetting }
})
