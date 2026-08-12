<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { userApi } from '@/api/userApi'
import type { User } from '@/types'

const message = useMessage()
const user = ref<User | null>(null)
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)

const form = reactive({
  nickname: '',
  mood: '',
  birthDate: '',
  photoPreferences: '',
  notes: '',
})

const userInitial = computed(() => user.value?.username?.charAt(0).toUpperCase() || 'U')
const yesterday = new Date()
yesterday.setDate(yesterday.getDate() - 1)
const maxBirthDate = [
  yesterday.getFullYear(),
  String(yesterday.getMonth() + 1).padStart(2, '0'),
  String(yesterday.getDate()).padStart(2, '0'),
].join('-')

function applyUser(nextUser: User) {
  user.value = nextUser
  form.nickname = nextUser.nickname || ''
  form.mood = nextUser.mood || ''
  form.birthDate = nextUser.birthDate || ''
  form.photoPreferences = nextUser.photoPreferences || ''
  form.notes = nextUser.notes || ''
}

function syncSession(nextUser: User) {
  sessionStorage.setItem('user', JSON.stringify(nextUser))
  window.dispatchEvent(new CustomEvent('user-profile-updated', { detail: nextUser }))
}

async function loadProfile() {
  loading.value = true
  try {
    const { data } = await userApi.me()
    applyUser(data)
    syncSession(data)
  } catch (e: any) {
    message.error(e.response?.data?.message || '个人资料加载失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    const { data } = await userApi.updateMe({
      nickname: form.nickname.trim() || null,
      mood: form.mood.trim() || null,
      birthDate: form.birthDate || null,
      photoPreferences: form.photoPreferences.trim() || null,
      notes: form.notes.trim() || null,
    })
    applyUser(data)
    syncSession(data)
    message.success('个人设置已保存')
  } catch (e: any) {
    message.error(e.response?.data?.message || '个人设置保存失败')
  } finally {
    saving.value = false
  }
}

async function handleAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    message.warning('头像图片不能超过 5MB')
    return
  }
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    message.warning('仅支持 JPG、PNG、WebP 或 GIF 图片')
    return
  }

  uploading.value = true
  try {
    const { data } = await userApi.uploadAvatar(file)
    applyUser(data)
    syncSession(data)
    message.success('头像已更新')
  } catch (e: any) {
    message.error(e.response?.data?.message || '头像上传失败')
  } finally {
    uploading.value = false
  }
}

async function removeAvatar() {
  if (!confirm('确定移除当前头像吗？')) return
  uploading.value = true
  try {
    const { data } = await userApi.deleteAvatar()
    applyUser(data)
    syncSession(data)
    message.success('头像已移除')
  } catch (e: any) {
    message.error(e.response?.data?.message || '头像移除失败')
  } finally {
    uploading.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="panel-card profile-card">
    <div v-if="loading" class="profile-loading">
      <span class="profile-spinner"></span>
      <span>正在加载个人资料…</span>
    </div>

    <template v-else-if="user">
      <section class="avatar-section">
        <div class="profile-avatar">
          <img v-if="user.avatar" :src="user.avatar" alt="当前头像" />
          <span v-else>{{ userInitial }}</span>
        </div>
        <div class="avatar-copy">
          <strong>个人头像</strong>
          <p>支持 JPG、PNG、WebP、GIF，图片大小不超过 5MB。</p>
          <div class="profile-button-row">
            <input
              ref="avatarInput"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              hidden
              @change="handleAvatarSelected"
            />
            <button class="btn-primary" :disabled="uploading" @click="avatarInput?.click()">
              {{ uploading ? '处理中…' : '选择头像' }}
            </button>
            <button v-if="user.avatar" class="btn-secondary danger-button" :disabled="uploading" @click="removeAvatar">
              移除头像
            </button>
          </div>
        </div>
      </section>

      <section class="profile-form">
        <div class="profile-grid">
          <label class="profile-field readonly-field">
            <span>用户名</span>
            <input :value="user.username" class="dialog-input" disabled />
            <small>用户名只能由管理员维护。</small>
          </label>

          <label class="profile-field readonly-field">
            <span>账户角色</span>
            <input :value="user.role === 'ADMIN' ? '管理员' : '普通用户'" class="dialog-input" disabled />
            <small>角色权限由管理员统一分配。</small>
          </label>

          <label class="profile-field">
            <span>昵称</span>
            <input v-model="form.nickname" class="dialog-input" maxlength="50" placeholder="你希望显示的名字" />
            <small class="field-counter">{{ form.nickname.length }}/50</small>
          </label>

          <label class="profile-field">
            <span>出生日期</span>
            <input v-model="form.birthDate" class="dialog-input" type="date" :max="maxBirthDate" />
          </label>
        </div>

        <label class="profile-field">
          <span>心情或个性签名</span>
          <input v-model="form.mood" class="dialog-input" maxlength="100" placeholder="写下此刻的心情" />
          <small class="field-counter">{{ form.mood.length }}/100</small>
        </label>

        <label class="profile-field">
          <span>照片偏好</span>
          <textarea
            v-model="form.photoPreferences"
            class="dialog-input profile-textarea"
            maxlength="1000"
            rows="4"
            placeholder="例如：家庭合影、旅行、人像；希望重点整理的人物或主题"
          ></textarea>
          <small class="field-counter">{{ form.photoPreferences.length }}/1000</small>
        </label>

        <label class="profile-field">
          <span>备注</span>
          <textarea
            v-model="form.notes"
            class="dialog-input profile-textarea"
            maxlength="1000"
            rows="4"
            placeholder="记录与个人相册相关的其他信息"
          ></textarea>
          <small class="field-counter">{{ form.notes.length }}/1000</small>
        </label>

        <div class="profile-actions">
          <button class="btn-primary" :disabled="saving" @click="saveProfile">
            {{ saving ? '保存中…' : '保存个人设置' }}
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.btn-primary,
.btn-secondary {
  min-height: 38px;
  padding: 8px 15px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 650;
  transition: transform .16s ease, filter .16s ease, opacity .16s ease;
}

.btn-primary {
  background: var(--accent);
  color: white;
}

.btn-secondary {
  border: 1px solid var(--separator);
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.btn-primary:disabled,
.btn-secondary:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.dialog-input {
  width: 100%;
  min-height: 40px;
  padding: 10px 12px;
  border: 1px solid var(--separator);
  border-radius: 10px;
  outline: none;
  background: var(--bg-primary);
  color: var(--text-primary);
  font: inherit;
  font-size: 14px;
  font-weight: 400;
  transition: border-color .16s ease, box-shadow .16s ease;
}

.dialog-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 13%, transparent);
}

.profile-card {
  padding: 0;
  overflow: hidden;
}

.profile-loading {
  display: flex;
  min-height: 320px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 14px;
}

.profile-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--separator);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: profile-spin .8s linear infinite;
}

@keyframes profile-spin {
  to { transform: rotate(360deg); }
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 24px;
  border-bottom: 1px solid var(--separator);
}

.profile-avatar {
  display: grid;
  width: 96px;
  height: 96px;
  flex: 0 0 96px;
  place-items: center;
  overflow: hidden;
  border: 3px solid color-mix(in srgb, var(--bg-secondary) 88%, white);
  border-radius: 50%;
  background: linear-gradient(145deg, #5ac8fa, #007aff 58%, #5e5ce6);
  box-shadow: 0 12px 28px color-mix(in srgb, var(--accent) 24%, transparent);
  color: white;
  font-size: 34px;
  font-weight: 750;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-copy strong {
  font-size: 15px;
}

.avatar-copy p {
  margin: 5px 0 13px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.profile-button-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.danger-button {
  color: var(--danger);
}

.profile-form {
  display: grid;
  gap: 18px;
  padding: 24px;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.profile-field {
  position: relative;
  display: grid;
  gap: 7px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.profile-field small {
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 400;
}

.profile-field .field-counter {
  position: absolute;
  right: 10px;
  bottom: 8px;
  padding-left: 8px;
  background: var(--bg-primary);
}

.profile-field:has(.profile-textarea) .field-counter {
  bottom: 10px;
}

.readonly-field input {
  opacity: .68;
  cursor: not-allowed;
}

.profile-textarea {
  min-height: 106px;
  padding-bottom: 26px;
  resize: vertical;
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

@media (max-width: 680px) {
  .avatar-section {
    align-items: flex-start;
    padding: 20px;
  }

  .profile-avatar {
    width: 76px;
    height: 76px;
    flex-basis: 76px;
    font-size: 28px;
  }

  .profile-form {
    padding: 20px;
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 430px) {
  .avatar-section {
    flex-direction: column;
  }

  .profile-actions .btn-primary {
    width: 100%;
  }
}
</style>
