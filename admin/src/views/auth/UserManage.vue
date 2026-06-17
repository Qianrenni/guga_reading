<template>
  <div class="container-column gap">
    <h4>用户管理</h4>

    <!-- 搜索条 -->
    <div class="inner-container gap">
      <QSearch
        v-model="keyword"
        placeholder="搜索用户名或邮箱"
        @search="search"
      />
      <QFormButton class="button-primary" @click="search">搜索</QFormButton>
    </div>

    <!-- 用户列表 -->
    <div class="bg-card radius-half-rem">
      <table class="user-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.user.id">
            <td>{{ user.user.id }}</td>
            <td>
              <div class="inner-container container-align-center gap-half">
                <img
                  v-if="user.user.avatar"
                  :src="user.user.avatar"
                  class="user-avatar"
                />
                <span>{{ user.user.userName }}</span>
              </div>
            </td>
            <td class="text-description">{{ user.user.email }}</td>
            <td>
              <span
                v-for="role in user.roles"
                :key="role.roleId"
                class="role-badge"
                >{{ getRoleName(role.roleId) }}</span
              >
              <span
                v-if="user.roles.length === 0"
                class="text-muted text-085rem"
                >无角色</span
              >
            </td>
            <td>
              <span
                :class="
                  user.user.isActive ? 'status-active' : 'status-inactive'
                "
              >
                {{ user.user.isActive ? '已激活' : '已禁用' }}
              </span>
            </td>
            <td>
              <div class="inner-container gap-half">
                <QFormButton class="button-small" @click="openRoleDialog(user)"
                  >编辑角色</QFormButton
                >
                <QFormButton
                  class="button-small"
                  :class="
                    user.user.isActive ? 'button-warning' : 'button-primary'
                  "
                  @click="toggleUserStatus(user)"
                >
                  {{ user.user.isActive ? '禁用' : '激活' }}
                </QFormButton>
              </div>
            </td>
          </tr>
          <tr v-if="users.length === 0">
            <td colspan="6" class="text-center text-muted padding-rem">
              暂无用户数据
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="inner-container container-align-center container-space-between">
      <span class="text-description text-085rem">共 {{ total }} 条</span>
      <div class="inner-container gap-half container-align-center">
        <QFormButton
          class="button-small"
          :disabled="page <= 1"
          @click="changePage(page - 1)"
          >上一页</QFormButton
        >
        <span class="text-085rem">{{ page }} / {{ totalPages }}</span>
        <QFormButton
          class="button-small"
          :disabled="page >= totalPages"
          @click="changePage(page + 1)"
          >下一页</QFormButton
        >
      </div>
    </div>

    <!-- 编辑角色对话框 -->
    <div
      v-if="showRoleDialog && editingUser"
      class="dialog-overlay"
      @click.self="closeRoleDialog"
    >
      <div class="dialog-content bg-card radius-half-rem">
        <h4>编辑用户角色 - {{ editingUser?.user?.userName }}</h4>
        <div class="container-column gap">
          <div>
            <label>当前角色</label>
            <div class="inner-container container-wrap gap-half">
              <span
                v-for="ur in editingUserRoles"
                :key="ur.roleId"
                class="role-tag"
              >
                {{ getRoleName(ur.roleId) }}
                <span
                  class="role-tag-remove"
                  @click="handleRemoveUserRole(ur.roleId)"
                  >&times;</span
                >
              </span>
              <span v-if="editingUserRoles.length === 0" class="text-muted"
                >无角色</span
              >
            </div>
          </div>
          <div>
            <label>添加角色</label>
            <div class="inner-container gap-half">
              <select v-model="addRoleId" class="input-select">
                <option :value="0" disabled>选择角色...</option>
                <option
                  v-for="r in availableUserRoles"
                  :key="r.id"
                  :value="r.id"
                >
                  {{ r.name }} ({{ r.code }})
                </option>
              </select>
              <QFormButton class="button-small" @click="handleAddUserRole"
                >添加</QFormButton
              >
            </div>
          </div>
          <div style="text-align: right">
            <QFormButton @click="closeRoleDialog">关闭</QFormButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onBeforeMount } from 'vue';
import { useMessage, QFormButton, QSearch } from 'qyani-components';
import { useApiRight } from '@guga-reading/shares';
import type { AdminUserResponse, Role, UserRole } from '@guga-reading/types';

defineOptions({ name: 'UserManage' });

const users = ref<AdminUserResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const showRoleDialog = ref(false);
const editingUser = ref<AdminUserResponse | null>(null);
const editingUserRoles = ref<UserRole[]>([]);
const allRoles = ref<Role[]>([]);
const addRoleId = ref(0);

function getRoleName(roleId: number): string {
  return allRoles.value.find((r) => r.id === roleId)?.name ?? '';
}

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize.value)),
);

const availableUserRoles = computed(() => {
  const currentIds = new Set(editingUserRoles.value.map((r) => r.roleId));
  return allRoles.value.filter((r) => !currentIds.has(r.id));
});

async function loadUsers() {
  const { success, data } = await useApiRight.getUsers(
    page.value,
    pageSize.value,
    keyword.value || undefined,
  );
  if (success && data) {
    users.value = data.items || [];
    total.value = data.total || 0;
  }
}

async function loadRoles() {
  const { success, data } = await useApiRight.getRoles();
  if (success) allRoles.value = data || [];
}

function search() {
  page.value = 1;
  loadUsers();
}

function changePage(newPage: number) {
  page.value = newPage;
  loadUsers();
}

async function openRoleDialog(user: AdminUserResponse) {
  editingUser.value = user;
  const { success, data } = await useApiRight.getUserRoles(user.user.id);
  if (success) editingUserRoles.value = data || [];
  addRoleId.value = 0;
  showRoleDialog.value = true;
}

function closeRoleDialog() {
  showRoleDialog.value = false;
  editingUser.value = null;
  editingUserRoles.value = [];
}

async function handleAddUserRole() {
  if (!editingUser.value || !addRoleId.value) return;
  const { success, message } = await useApiRight.addUserRole(
    editingUser.value.user.id,
    addRoleId.value,
  );
  if (success) {
    useMessage.success('角色添加成功');
    const { data } = await useApiRight.getUserRoles(editingUser.value!.user.id);
    if (data) editingUserRoles.value = data;
    addRoleId.value = 0;
    await loadUsers();
  } else {
    useMessage.error(message);
  }
}

async function handleRemoveUserRole(roleId: number) {
  if (!editingUser.value) return;
  const { success, message } = await useApiRight.removeUserRole(
    editingUser.value.user.id,
    roleId,
  );
  if (success) {
    useMessage.success('角色已移除');
    const { data } = await useApiRight.getUserRoles(editingUser.value!.user.id);
    if (data) editingUserRoles.value = data;
    await loadUsers();
  } else {
    useMessage.error(message);
  }
}

async function toggleUserStatus(user: AdminUserResponse) {
  const action = user.user.isActive ? '禁用' : '激活';
  if (!confirm(`确定要${action}用户「${user.user.userName}」吗？`)) return;
  const { success, message } = await useApiRight.updateUserStatus(
    user.user.id,
    !user.user.isActive,
  );
  if (success) {
    useMessage.success(`用户已${action}`);
    await loadUsers();
  } else {
    useMessage.error(message);
  }
}

onBeforeMount(() => {
  loadUsers();
  loadRoles();
});
</script>

<style scoped>
.user-table {
  width: 100%;
  border-collapse: collapse;
}
.user-table th,
.user-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-color, #eee);
  font-size: 0.9rem;
}
.user-table th {
  font-weight: 600;
  color: var(--text-muted, #888);
  font-size: 0.8rem;
  text-transform: uppercase;
}
.user-table tbody tr:hover {
  background: var(--bg-hover, rgba(0, 0, 0, 0.02));
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}

.role-badge {
  display: inline-block;
  padding: 2px 8px;
  margin: 2px 4px 2px 0;
  background: var(--bg-tag, #e8f0fe);
  border-radius: 10px;
  font-size: 0.8rem;
}

.role-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  background: var(--bg-tag, #e8f0fe);
  border-radius: 12px;
  font-size: 0.85rem;
}
.role-tag-remove {
  cursor: pointer;
  font-weight: bold;
  color: #999;
  margin-left: 2px;
}
.role-tag-remove:hover {
  color: #e33;
}

.status-active {
  color: #389e0d;
  font-size: 0.85rem;
}
.status-inactive {
  color: #cf1322;
  font-size: 0.85rem;
}

.input-field,
.input-select {
  padding: 8px 10px;
  border: 1px solid var(--border-color, #ddd);
  border-radius: 6px;
  font-size: 0.9rem;
  background: var(--bg-input, #fff);
  color: var(--text-color, #333);
}
.input-select {
  cursor: pointer;
  min-width: 160px;
}
label {
  display: block;
  margin-bottom: 4px;
  font-size: 0.85rem;
  font-weight: 500;
}

/* Dialog */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog-content {
  width: 480px;
  max-width: 90%;
  padding: 20px;
}
</style>
