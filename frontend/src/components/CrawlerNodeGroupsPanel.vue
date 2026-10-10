<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { settingApi, type CrawlerProxy, type MihomoNodeGroup, type MihomoNodeGroupRequest, type MihomoProxyNode } from '@/api/settingApi'
const props = defineProps<{ proxy: CrawlerProxy | null }>()
const visible = defineModel<boolean>({ default: false })
const groups = ref<MihomoNodeGroup[]>([])
const catalog = ref<Record<string, MihomoProxyNode>>({})
const saving = ref(false)
const editingId = ref<number | null>(null)
const editing = ref(false)
const query = ref('')
const form = reactive<MihomoNodeGroupRequest>({ name: '', controlGroup: '', host: '', port: 7895, nodes: [] })
const selectors = computed(() => Object.entries(catalog.value).filter(([, n]) => n.type === 'Selector').map(([name]) => name))
const nodes = computed(() => (catalog.value[form.controlGroup]?.all || []).filter(name => {
  const node = catalog.value[name]
  return node && !node.all && !['DIRECT','REJECT','REJECT-DROP','PASS','COMPATIBLE'].includes(node.type.toUpperCase()) && name.toLowerCase().includes(query.value.toLowerCase())
}))
const error = (e: any) => ElMessage.error(e?.response?.data?.message || '节点组操作失败')
async function load() {
  if (!props.proxy) return
  try {
    const [saved, available] = await Promise.all([settingApi.getMihomoGroups(props.proxy.id), settingApi.getMihomoNodes(props.proxy.id)])
    groups.value = saved.data; catalog.value = available.data.proxies
  } catch (e) { error(e) }
}
function edit(group?: MihomoNodeGroup) {
  editingId.value = group?.id ?? null; editing.value = true; query.value = ''
  Object.assign(form, group ? { ...group, nodes: [...group.nodes] } : { name: '', controlGroup: '', host: props.proxy?.host || '', port: props.proxy?.port || 7895, nodes: [] })
}
function move(index: number, direction: number) {
  const next = [...form.nodes]; const target = index + direction
  if (target < 0 || target >= next.length) return
  ;[next[index], next[target]] = [next[target], next[index]]; form.nodes = next
}
async function save() {
  if (!props.proxy) return
  saving.value = true
  try { await settingApi.saveMihomoGroup(props.proxy.id, editingId.value, { ...form }); editing.value = false; await load(); ElMessage.success('备用节点组已保存') }
  catch (e) { error(e) } finally { saving.value = false }
}
async function remove(group: MihomoNodeGroup) {
  if (!props.proxy) return
  try { await ElMessageBox.confirm(`删除备用节点组“${group.name}”？`, '删除节点组', { type: 'warning' }) } catch { return }
  try { await settingApi.deleteMihomoGroup(props.proxy.id, group.id); await load() } catch (e) { error(e) }
}
async function test(node: string) {
  if (!props.proxy) return
  try { const { data } = await settingApi.testMihomoNode(props.proxy.id, node); ElMessage.success(`${node}：${data.delay} ms`) } catch (e) { error(e) }
}
watch(visible, open => { if (open) { editing.value = false; load() } })
</script>
<template>
  <el-dialog v-model="visible" append-to-body :title="`${proxy?.name || 'Mihomo'} · 节点与备用组`" width="min(820px, 96vw)">
    <div class="group-toolbar"><el-button @click="edit()">新增备用节点组</el-button><el-button @click="load">刷新节点</el-button></div>
    <el-alert type="info" :closable="false" title="节点组保存于相册系统，引用已有 Selector 与专用 HTTP 入口；不会修改 Mihomo 配置。已被执行器引用的组需先解除引用才能编辑。" />
    <el-table :data="groups"><el-table-column prop="name" label="备用节点组" /><el-table-column prop="controlGroup" label="受控组" /><el-table-column label="入口"><template #default="{ row }">{{ row.host }}:{{ row.port }}</template></el-table-column><el-table-column label="节点"><template #default="{ row }">{{ row.nodes.length }} 个</template></el-table-column><el-table-column label="操作" width="140"><template #default="{ row }"><el-button text @click="edit(row)">编辑</el-button><el-button text type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
    <section v-if="editing" class="group-editor">
      <el-form label-position="top">
        <el-form-item label="节点组名称"><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="已有 Selector 受控组"><el-select v-model="form.controlGroup" @change="form.nodes = []"><el-option v-for="group in selectors" :key="group" :value="group" :label="group" /></el-select></el-form-item>
        <div class="group-grid"><el-form-item label="专用 HTTP 入口主机"><el-input v-model="form.host" /></el-form-item><el-form-item label="入口端口"><el-input-number v-model="form.port" :min="1" :max="65535" /></el-form-item></div>
        <el-input v-model="query" placeholder="搜索具体节点" clearable />
        <el-checkbox-group v-model="form.nodes" class="node-list"><div v-for="node in nodes" :key="node"><el-checkbox :value="node">{{ node }}</el-checkbox><el-button text size="small" @click="test(node)">检测延迟</el-button></div></el-checkbox-group>
        <h4>已选节点与顺序</h4>
        <div v-for="(node, index) in form.nodes" :key="node" class="selected-node"><span>{{ index + 1 }}. {{ node }}</span><el-button text :disabled="index === 0" @click="move(index, -1)">上移</el-button><el-button text :disabled="index === form.nodes.length - 1" @click="move(index, 1)">下移</el-button></div>
      </el-form>
      <el-button @click="editing = false">取消编辑</el-button><el-button type="primary" :loading="saving" @click="save">保存节点组</el-button>
    </section>
  </el-dialog>
</template>
<style scoped>
.group-toolbar{display:flex;gap:10px;margin-bottom:16px}.group-editor{padding:18px;margin-top:18px;border:1px solid var(--separator);border-radius:14px}.group-grid{display:grid;grid-template-columns:1fr 180px;gap:16px}.node-list{display:grid;max-height:240px;overflow:auto;margin-top:12px}.node-list>div,.selected-node{display:flex;align-items:center;gap:12px}.node-list .el-checkbox,.selected-node>span{flex:1;min-width:0}.el-select{width:100%}@media(max-width:600px){.group-grid{grid-template-columns:1fr}}
</style>
