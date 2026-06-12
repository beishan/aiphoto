<script setup lang="ts">
import { onMounted, onActivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDialog, useMessage } from 'naive-ui'
import { peopleApi } from '@/api/peopleApi'
import type { Person } from '@/types'
import type { Face } from '@/api/peopleApi'

const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const people = ref<Person[]>([])
const unnamedFaces = ref<Face[]>([])
const loading = ref(false)
const reclustering = ref(false)
const refreshing = ref(false)

// Merge dialog state
const showMergeDialog = ref(false)
const mergeTarget = ref<number | null>(null)
const mergeSource = ref<number | null>(null)

// Assign dialog state
const showAssignDialog = ref(false)
const assignFaceId = ref<number | null>(null)

async function fetchPeople() {
  loading.value = true
  try {
    const [peopleRes, facesRes] = await Promise.all([
      peopleApi.list(),
      peopleApi.getUnnamedFaces(),
    ])
    people.value = peopleRes.data
    unnamedFaces.value = facesRes.data
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await fetchPeople()
  } finally {
    refreshing.value = false
  }
}

onMounted(fetchPeople)
onActivated(fetchPeople)

function goToPerson(person: Person) {
  router.push(`/people/${person.id}`)
}

async function handleRecluster() {
  if (reclustering.value) return
  dialog.warning({
    title: '自动合并',
    content: '将自动合并相似的人物，此操作不可撤销。确定继续？',
    positiveText: '合并',
    negativeText: '取消',
    onPositiveClick: async () => {
      reclustering.value = true
      try {
        const { data } = await peopleApi.recluster()
        message.success(`合并完成，共合并了 ${data.merged} 个人物`)
        await fetchPeople()
      } catch (e) {
        message.error('重聚类失败')
      } finally {
        reclustering.value = false
      }
    },
  })
}

function confirmDelete(e: Event, person: Person) {
  e.stopPropagation()
  dialog.warning({
    title: '删除人物',
    content: `确定要删除"${person.name || '未命名'}"吗？此操作不会删除照片，只会移除此人物标记。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await peopleApi.delete(person.id)
        message.success('已删除')
        people.value = people.value.filter(p => p.id !== person.id)
      } catch (e) {
        message.error('删除失败')
      }
    },
  })
}

// Merge functions
function openMergeDialog() {
  mergeTarget.value = null
  mergeSource.value = null
  showMergeDialog.value = true
}

async function executeMerge() {
  if (!mergeTarget.value || !mergeSource.value) {
    message.warning('请选择要合并的两个人物')
    return
  }
  if (mergeTarget.value === mergeSource.value) {
    message.warning('不能合并同一个人物')
    return
  }

  const target = people.value.find(p => p.id === mergeTarget.value)
  const source = people.value.find(p => p.id === mergeSource.value)

  dialog.warning({
    title: '确认合并',
    content: `确定将"${source?.name || '未命名'}"合并到"${target?.name || '未命名'}"吗？合并后不可撤销。`,
    positiveText: '合并',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await peopleApi.merge(mergeTarget.value!, mergeSource.value!)
        message.success('合并成功')
        showMergeDialog.value = false
        await fetchPeople()
      } catch (e) {
        message.error('合并失败')
      }
    },
  })
}

// Assign face functions
function openAssignDialog(face: Face) {
  assignFaceId.value = face.id
  showAssignDialog.value = true
}

async function assignToPerson(personId: number) {
  if (!assignFaceId.value) return
  try {
    await peopleApi.assignFace(assignFaceId.value, personId)
    message.success('已分配')
    showAssignDialog.value = false
    await fetchPeople()
  } catch (e) {
    message.error('分配失败')
  }
}

function getBboxStyle(bboxJson: string) {
  try {
    const bbox = JSON.parse(bboxJson)
    return {
      left: `${bbox.x * 100}%`,
      top: `${bbox.y * 100}%`,
      width: `${bbox.w * 100}%`,
      height: `${bbox.h * 100}%`,
    }
  } catch {
    return {}
  }
}
</script>

<template>
  <div class="people-view">
    <!-- Header -->
    <div class="people-header">
      <div class="header-left">
        <button class="refresh-btn" @click="handleRefresh" :disabled="refreshing" title="刷新">
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" :class="{ spinning: refreshing }">
            <path d="M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0112 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
          </svg>
        </button>
      </div>
      <div class="header-right">
        <button v-if="people.length > 1" class="action-btn" @click="openMergeDialog" title="合并人物">
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
            <path d="M15 8c0-1.42-.5-2.73-1.33-3.76.42-.14.86-.24 1.33-.24 2.21 0 4 1.79 4 4s-1.79 4-4 4c-.47 0-.91-.1-1.33-.24C14.5 10.73 15 9.42 15 8zm5 8c-1.42 0-2.73.5-3.76 1.33.14.42.24.86.24 1.33 0 2.21-1.79 4-4 4s-4-1.79-4-4c0-.47.1-.91.24-1.33C7.5 16.5 6.58 17 5 17c-2.21 0-4-1.79-4-4s1.79-4 4-4c.47 0 .91.1 1.33.24C7.27 6.5 8.58 6 10 6c2.21 0 4 1.79 4 4s-1.79 4-4 4c-.47 0-.91-.1-1.33-.24C9.27 14.73 10.58 15.2 12 15.2c2.21 0 4-1.79 4-4 0-.47-.1-.91-.24-1.33C17.27 11.5 18.58 12 20 12c2.21 0 4-1.79 4-4s-1.79-4-4-4c-.47 0-.91.1-1.33.24C18.73 3.27 17.42 2.8 16 2.8c-2.21 0-4 1.79-4 4s1.79 4 4 4c.47 0 .91-.1 1.33-.24"/>
          </svg>
          合并
        </button>
        <button v-if="people.length > 1" class="action-btn" @click="handleRecluster" :disabled="reclustering">
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
            <path d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z" />
          </svg>
          {{ reclustering ? '合并中...' : '自动合并' }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <!-- Empty -->
    <div v-else-if="people.length === 0 && unnamedFaces.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a3.5 3.5 0 110 7 3.5 3.5 0 010-7zm0 14.2a7.2 7.2 0 01-6-3.22c.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08a7.2 7.2 0 01-6 3.22z" />
      </svg>
      <h3>暂无人物</h3>
      <p>上传照片后，AI 会自动识别照片中的人物</p>
    </div>

    <template v-else>
      <!-- Unnamed faces section -->
      <div v-if="unnamedFaces.length > 0" class="section">
        <div class="section-header">
          <h3 class="section-title">未命名人脸 ({{ unnamedFaces.length }})</h3>
          <p class="section-hint">点击人脸可分配给已有或新建人物</p>
        </div>
        <div class="faces-scroll">
          <div
            v-for="face in unnamedFaces"
            :key="face.id"
            class="face-item"
            @click="openAssignDialog(face)"
          >
            <div class="face-thumb">
              <img v-if="face.photoUrl" :src="face.photoUrl" alt="" />
              <div v-if="face.bboxJson" class="face-crop" :style="getBboxStyle(face.bboxJson)"></div>
            </div>
            <span class="face-label">未命名</span>
          </div>
        </div>
      </div>

      <!-- Named people section -->
      <div v-if="people.length > 0" class="section">
        <div class="section-header">
          <h3 class="section-title">已命名人物 ({{ people.length }})</h3>
        </div>
        <div class="people-grid">
          <div
            v-for="person in people"
            :key="person.id"
            class="person-item"
            @click="goToPerson(person)"
          >
            <div class="person-avatar-wrapper">
              <div class="person-avatar">
                <img v-if="person.coverPhotoUrl" :src="person.coverPhotoUrl" alt="" />
                <span v-else class="avatar-fallback">{{ person.name?.[0] || '?' }}</span>
              </div>
              <button class="person-delete-btn" @click="confirmDelete($event, person)" title="删除">
                <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                  <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                </svg>
              </button>
            </div>
            <span class="person-name">{{ person.name || '未命名' }}</span>
            <span class="person-count">{{ person.photoCount }} 张</span>
          </div>
        </div>
      </div>
    </template>

    <!-- Merge Dialog -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showMergeDialog" class="modal-overlay" @click.self="showMergeDialog = false">
          <div class="modal-panel">
            <div class="modal-header">
              <h3>合并人物</h3>
              <button class="modal-close" @click="showMergeDialog = false">
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z"/>
                </svg>
              </button>
            </div>
            <div class="modal-body">
              <div class="merge-section">
                <label class="merge-label">合并到（保留的人物）</label>
                <select v-model="mergeTarget" class="merge-select">
                  <option :value="null">请选择</option>
                  <option v-for="p in people" :key="p.id" :value="p.id">
                    {{ p.name || '未命名' }} ({{ p.photoCount }} 张)
                  </option>
                </select>
              </div>
              <div class="merge-arrow">
                <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
                  <path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"/>
                </svg>
              </div>
              <div class="merge-section">
                <label class="merge-label">合并源（将被删除的人物）</label>
                <select v-model="mergeSource" class="merge-select">
                  <option :value="null">请选择</option>
                  <option v-for="p in people" :key="p.id" :value="p.id">
                    {{ p.name || '未命名' }} ({{ p.photoCount }} 张)
                  </option>
                </select>
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn-cancel" @click="showMergeDialog = false">取消</button>
              <button class="btn-confirm" @click="executeMerge" :disabled="!mergeTarget || !mergeSource">合并</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Assign Dialog -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showAssignDialog" class="modal-overlay" @click.self="showAssignDialog = false">
          <div class="modal-panel">
            <div class="modal-header">
              <h3>分配人脸</h3>
              <button class="modal-close" @click="showAssignDialog = false">
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z"/>
                </svg>
              </button>
            </div>
            <div class="modal-body">
              <p class="assign-hint">选择此人脸所属的人物：</p>
              <div class="assign-list">
                <button
                  v-for="person in people"
                  :key="person.id"
                  class="assign-item"
                  @click="assignToPerson(person.id)"
                >
                  <div class="assign-avatar">
                    <img v-if="person.coverPhotoUrl" :src="person.coverPhotoUrl" alt="" />
                    <span v-else class="assign-fallback">{{ person.name?.[0] || '?' }}</span>
                  </div>
                  <span class="assign-name">{{ person.name || '未命名' }}</span>
                  <span class="assign-count">{{ person.photoCount }} 张</span>
                </button>
                <div v-if="people.length === 0" class="assign-empty">
                  暂无已命名人物，请先创建人物
                </div>
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn-cancel" @click="showAssignDialog = false">取消</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.people-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  padding: 16px;
}

.people-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-left, .header-right {
  display: flex;
  gap: 8px;
}

.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-secondary);
  border: 1px solid var(--glass-border);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.refresh-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.refresh-btn svg.spinning {
  animation: spin 1s linear infinite;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Sections */
.section {
  margin-bottom: 24px;
}

.section-header {
  margin-bottom: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.section-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* Unnamed faces scroll */
.faces-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 4px 0 8px;
  -webkit-overflow-scrolling: touch;
}

.faces-scroll::-webkit-scrollbar {
  height: 4px;
}

.faces-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.faces-scroll::-webkit-scrollbar-thumb {
  background: var(--bg-tertiary);
  border-radius: 2px;
}

.face-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  flex-shrink: 0;
}

.face-thumb {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--bg-secondary);
  position: relative;
}

.face-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.face-crop {
  position: absolute;
  border: 2px solid var(--accent);
  border-radius: 4px;
}

.face-label {
  font-size: 11px;
  color: var(--text-tertiary);
}

/* People grid */
.people-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px 16px;
}

@media (min-width: 480px) {
  .people-grid { grid-template-columns: repeat(4, 1fr); }
}

@media (min-width: 768px) {
  .people-grid { grid-template-columns: repeat(5, 1fr); }
}

@media (min-width: 1024px) {
  .people-grid { grid-template-columns: repeat(6, 1fr); }
}

.person-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.person-avatar-wrapper {
  position: relative;
}

.person-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
}

.person-delete-btn {
  position: absolute;
  top: 0;
  right: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
}

.person-item:hover .person-delete-btn {
  opacity: 1;
}

.person-delete-btn:hover {
  background: #e74c3c;
  transform: scale(1.1);
}

.person-item:active .person-avatar {
  transform: scale(0.95);
}

.person-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-tertiary);
}

.person-name {
  font-size: 13px;
  font-weight: 500;
  text-align: center;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.person-count {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Loading */
.loading-state {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2.5px solid var(--bg-tertiary);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 8px;
  color: var(--text-secondary);
}

.empty-icon { color: var(--text-tertiary); }
.empty-state h3 { font-size: 20px; font-weight: 600; color: var(--text-primary); }
.empty-state p { font-size: 14px; }

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-panel {
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 400px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.modal-header h3 {
  font-size: 17px;
  font-weight: 600;
}

.modal-close {
  color: var(--text-secondary);
  padding: 4px;
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--border);
}

.btn-cancel, .btn-confirm {
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  border: none;
}

.btn-cancel {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.btn-confirm {
  background: var(--accent);
  color: white;
}

.btn-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Merge dialog */
.merge-section {
  margin-bottom: 12px;
}

.merge-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.merge-select {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  outline: none;
}

.merge-select:focus {
  border-color: var(--accent);
}

.merge-arrow {
  display: flex;
  justify-content: center;
  color: var(--text-tertiary);
  padding: 8px 0;
}

/* Assign dialog */
.assign-hint {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.assign-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 300px;
  overflow-y: auto;
}

.assign-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: none;
  background: none;
  color: var(--text-primary);
  font-size: 14px;
  cursor: pointer;
  border-radius: var(--radius-md);
  text-align: left;
  transition: background 0.15s;
  width: 100%;
}

.assign-item:hover {
  background: var(--bg-secondary);
}

.assign-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.assign-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.assign-fallback {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-tertiary);
}

.assign-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assign-count {
  font-size: 12px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.assign-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--text-tertiary);
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 480px) {
  .person-avatar {
    width: 64px;
    height: 64px;
  }

  .person-delete-btn {
    width: 20px;
    height: 20px;
  }

  .person-delete-btn svg {
    width: 12px;
    height: 12px;
  }

  .avatar-fallback {
    font-size: 22px;
  }
}
</style>
