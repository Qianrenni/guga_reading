<template>
  <div class="container-column gap">
    <!-- 工具条 -->
    <div class="inner-container container-space-between container-align-center">
      <h4>角色管理</h4>
      <QFormButton class="button-primary" @click="openCreateRoleDialog"
        >新增角色</QFormButton
      >
    </div>

    <!-- 角色列表 + 详情面板 -->
    <div class="inner-container gap" style="align-items: flex-start">
      <!-- 角色列表 -->
      <div class="bg-card radius-half-rem" style="flex: 0 0 360px">
        <div
          v-for="role in roles"
          :key="role.id"
          class="role-item"
          :class="{ 'role-item-active': selectedRole?.id === role.id }"
          @click="selectRole(role)"
        >
          <div class="container-column" style="flex: 1">
            <div class="inner-container container-align-center gap-half">
              <strong>{{ role.name }}</strong>
              <span class="text-muted text-085rem">({{ role.code }})</span>
            </div>
            <div v-if="role.description" class="text-085rem text-description">
              {{ role.description }}
            </div>
          </div>
          <QFormButton class="button-small" @click.stop="handleEditRole(role)"
            >编辑</QFormButton
          >
          <QFormButton
            v-if="role.code !== 'super_admin'"
            class="button-small button-danger"
            @click.stop="handleDeleteRole(role)"
            >删除</QFormButton
          >
        </div>
        <div
          v-if="roles.length === 0"
          class="text-center text-muted padding-rem"
        >
          暂无角色数据
        </div>
      </div>

      <!-- 详情面板 -->
      <div
        v-if="selectedRole"
        class="bg-card radius-half-rem container-flex-1"
        style="min-height: 400px"
      >
        <div class="padding-rem">
          <h4>
            {{ selectedRole.name }}
            <span class="text-muted text-085rem"
              >({{ selectedRole.code }})</span
            >
          </h4>
          <p v-if="selectedRole.description" class="text-description">
            {{ selectedRole.description }}
          </p>
        </div>

        <!-- 权限分配 -->
        <div
          class="padding-rem"
          style="border-top: 1px solid var(--border-color, #eee)"
        >
          <div
            class="inner-container container-space-between container-align-center"
          >
            <h5>权限分配</h5>
            <QFormButton
              class="button-primary button-small"
              @click="savePermissions"
              >保存权限</QFormButton
            >
          </div>
          <div
            v-for="(perms, resource) in groupedPermissions"
            :key="resource"
            class="permission-group"
          >
            <div
              class="text-085rem text-muted"
              style="font-weight: 600; margin-bottom: 4px"
            >
              {{ resourceLabels[resource] || resource }}
            </div>
            <div class="inner-container container-wrap gap-half">
              <label
                v-for="perm in perms"
                :key="perm.id"
                class="permission-checkbox"
                :class="{ active: tempPermissionIds.has(perm.id) }"
              >
                <input
                  type="checkbox"
                  :checked="tempPermissionIds.has(perm.id)"
                  @change="togglePermission(perm.id)"
                />
                <span
                  >{{ actionLabels[perm.action] || perm.action }}.{{
                    scopeLabels[perm.scope] || perm.scope
                  }}</span
                >
              </label>
            </div>
          </div>
        </div>

        <!-- 角色继承 -->
        <div
          class="padding-rem"
          style="border-top: 1px solid var(--border-color, #eee)"
        >
          <h5>角色继承</h5>
          <div
            class="inner-container container-wrap gap-half"
            style="margin-bottom: 8px"
          >
            <span
              v-for="parent in roleParents"
              :key="parent.id"
              class="role-tag"
            >
              {{ parent.name }}
              <span
                class="role-tag-remove"
                @click="handleRemoveParent(parent.id)"
                >&times;</span
              >
            </span>
            <span
              v-if="roleParents.length === 0"
              class="text-description text-085rem"
              >无继承</span
            >
          </div>
          <div class="inner-container gap-half">
            <select v-model="newParentId" class="input-select">
              <option :value="0" disabled>选择父角色...</option>
              <option v-for="r in availableParents" :key="r.id" :value="r.id">
                {{ r.name }} ({{ r.code }})
              </option>
            </select>
            <QFormButton class="button-small" @click="handleAddParent"
              >添加</QFormButton
            >
          </div>
        </div>
      </div>
      <div
        v-else
        class="bg-card radius-half-rem container-flex-1 container-center text-muted"
      >
        请选择一个角色查看详情
      </div>
    </div>

    <!-- 创建/编辑角色对话框 -->
    <div
      v-if="showRoleDialog"
      class="dialog-overlay"
      @click.self="closeRoleDialog"
    >
      <div class="dialog-content bg-card radius-half-rem">
        <h4>{{ isEditing ? '编辑角色' : '新增角色' }}</h4>
        <div class="container-column gap">
          <div>
            <label>角色名称</label>
            <input
              v-model="roleForm.name"
              class="input-field"
              placeholder="输入角色名称"
            />
          </div>
          <div>
            <label>角色编码</label>
            <select
              v-model="roleForm.code"
              class="input-select"
              :disabled="isEditing"
            >
              <option value="" disabled>选择角色编码...</option>
              <option value="user">USER - 普通用户</option>
              <option value="reviewer">REVIEWER - 审核员</option>
              <option value="author">AUTHOR - 作者</option>
              <option value="admin">ADMIN - 管理员</option>
              <option value="super_admin">SUPER_ADMIN - 超级管理员</option>
            </select>
          </div>
          <div>
            <label>描述</label>
            <textarea
              v-model="roleForm.description"
              class="input-field"
              placeholder="角色描述（可选）"
              rows="2"
            ></textarea>
          </div>
          <div class="inner-container gap" style="justify-content: flex-end">
            <QFormButton @click="closeRoleDialog">取消</QFormButton>
            <QFormButton class="button-primary" @click="saveRole">{{
              isEditing ? '保存' : '创建'
            }}</QFormButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onBeforeMount } from 'vue';
import { useMessage, QFormButton } from 'qyani-components';
import { useApiRight } from '@guga-reading/shares';
import type { Permission, Role } from '@guga-reading/types';

defineOptions({ name: 'RightManage' });

const roles = ref<Role[]>([]);
const permissions = ref<Permission[]>([]);
const selectedRole = ref<Role | null>(null);
const tempPermissionIds = ref<Set<number>>(new Set());
const roleParents = ref<Role[]>([]);
const newParentId = ref(0);
const showRoleDialog = ref(false);
const isEditing = ref(false);
const roleForm = ref({ name: '', code: '', description: '' });

const resourceLabels: Record<string, string> = {
  BOOK: '书籍',
  USER: '用户',
  PERMISSION: '权限',
  CHAPTER: '章节',
  SHELF: '书架',
};

const actionLabels: Record<string, string> = {
  READ: '读取',
  CREATE: '创建',
  UPDATE: '更新',
  DELETE: '删除',
  AUDIT: '审核',
  MANAGE: '管理',
};

const scopeLabels: Record<string, string> = {
  OWN: '自己',
  ALL: '全部',
};

const groupedPermissions = computed(() => {
  const groups: Record<Permission['resourceType'], Permission[]> = {} as Record<
    Permission['resourceType'],
    Permission[]
  >;
  for (const perm of permissions.value) {
    if (!groups[perm.resourceType]) {
      groups[perm.resourceType] = [];
    }
    groups[perm.resourceType].push(perm);
  }
  return groups;
});

const availableParents = computed(() => {
  if (!selectedRole.value) return [];
  return roles.value.filter(
    (r) =>
      r.id !== selectedRole.value!.id &&
      !roleParents.value.some((p) => p.id === r.id),
  );
});

async function loadRoles() {
  const { success, data } = await useApiRight.getRoles();
  if (success) roles.value = data || [];
}

async function loadPermissions() {
  const { success, data } = await useApiRight.getPermissions();
  if (success) permissions.value = data || [];
}

async function selectRole(role: Role) {
  selectedRole.value = role;
  // Load role permissions
  const { success, data } = await useApiRight.getRolePermissions(role.id);
  if (success) {
    tempPermissionIds.value = new Set((data || []).map((p) => p.id));
  }
  // Load role parents
  const res = await useApiRight.getRoleParents(role.id);
  if (res.success) roleParents.value = res.data || [];
  newParentId.value = 0;
}

function togglePermission(permId: number) {
  const newSet = new Set(tempPermissionIds.value);
  if (newSet.has(permId)) {
    newSet.delete(permId);
  } else {
    newSet.add(permId);
  }
  tempPermissionIds.value = newSet;
}

async function savePermissions() {
  if (!selectedRole.value) return;
  // Get current permissions
  const { data: currentPerms } = await useApiRight.getRolePermissions(
    selectedRole.value.id,
  );
  const currentIds = new Set((currentPerms || []).map((p) => p.id));
  const newIds = tempPermissionIds.value;

  const toAdd = [...newIds].filter((id) => !currentIds.has(id));
  const toRemove = [...currentIds].filter((id) => !newIds.has(id));

  if (toRemove.length > 0) {
    await useApiRight.revokeRolePermissions(selectedRole.value.id, toRemove);
  }
  if (toAdd.length > 0) {
    await useApiRight.assignRolePermissions(selectedRole.value.id, toAdd);
  }
  useMessage.success('权限保存成功');
}

function openCreateRoleDialog() {
  isEditing.value = false;
  roleForm.value = { name: '', code: '', description: '' };
  showRoleDialog.value = true;
}

function handleEditRole(role: Role) {
  isEditing.value = true;
  roleForm.value = {
    name: role.name,
    code: role.code,
    description: role.description || '',
  };
  showRoleDialog.value = true;
}

function closeRoleDialog() {
  showRoleDialog.value = false;
}

async function saveRole() {
  if (!roleForm.value.name || !roleForm.value.code) {
    useMessage.error('请填写角色名称和编码');
    return;
  }
  if (isEditing.value && selectedRole.value) {
    const { success, message } = await useApiRight.updateRole(
      selectedRole.value.id,
      {
        name: roleForm.value.name,
        description: roleForm.value.description || undefined,
      },
    );
    if (success) {
      useMessage.success('角色更新成功');
      closeRoleDialog();
      await loadRoles();
      selectedRole.value =
        roles.value.find((r) => r.id === selectedRole.value!.id) || null;
    } else {
      useMessage.error(message);
    }
  } else {
    const { success, message } = await useApiRight.createRole({
      name: roleForm.value.name,
      code: roleForm.value.code,
      description: roleForm.value.description || undefined,
    });
    if (success) {
      useMessage.success('角色创建成功');
      closeRoleDialog();
      await loadRoles();
    } else {
      useMessage.error(message);
    }
  }
}

async function handleDeleteRole(role: Role) {
  if (!confirm(`确定要删除角色「${role.name}」吗？`)) return;
  const { success, message } = await useApiRight.deleteRole(role.id);
  if (success) {
    useMessage.success('角色已删除');
    if (selectedRole.value?.id === role.id) {
      selectedRole.value = null;
      roleParents.value = [];
      tempPermissionIds.value = new Set();
    }
    await loadRoles();
  } else {
    useMessage.error(message);
  }
}

function handleAddParent() {
  if (!selectedRole.value || !newParentId.value) return;
  useApiRight
    .addRoleParent(selectedRole.value.id, newParentId.value)
    .then(({ success, message }) => {
      if (success) {
        useMessage.success('继承关系添加成功');
        // Reload parents
        useApiRight.getRoleParents(selectedRole.value!.id).then((res) => {
          if (res.success) roleParents.value = res.data || [];
        });
        newParentId.value = 0;
      } else {
        useMessage.error(message);
      }
    });
}

function handleRemoveParent(parentId: number) {
  if (!selectedRole.value) return;
  useApiRight
    .removeRoleParent(selectedRole.value.id, parentId)
    .then(({ success, message }) => {
      if (success) {
        useMessage.success('继承关系已移除');
        useApiRight.getRoleParents(selectedRole.value!.id).then((res) => {
          if (res.success) roleParents.value = res.data || [];
        });
      } else {
        useMessage.error(message);
      }
    });
}

onBeforeMount(() => {
  loadRoles();
  loadPermissions();
});
</script>

<style scoped>
.role-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-color, #eee);
  transition: background 0.15s;
}
.role-item:hover {
  background: var(--bg-hover, rgba(0, 0, 0, 0.04));
}
.role-item-active {
  background: var(--bg-active, rgba(64, 128, 255, 0.08));
  border-left: 3px solid var(--primary, #4090ff);
}

.permission-group {
  margin: 10px 0;
}
.permission-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid var(--border-color, #ddd);
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  user-select: none;
}
.permission-checkbox.active {
  border-color: var(--primary, #4090ff);
  background: rgba(64, 128, 255, 0.08);
}
.permission-checkbox input {
  display: none;
}

.role-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
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
  width: 420px;
  max-width: 90%;
  padding: 20px;
}

.input-field,
.input-select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--border-color, #ddd);
  border-radius: 6px;
  font-size: 0.9rem;
  background: var(--bg-input, #fff);
  color: var(--text-color, #333);
  box-sizing: border-box;
}
.input-select {
  cursor: pointer;
}
textarea.input-field {
  resize: vertical;
}
label {
  display: block;
  margin-bottom: 4px;
  font-size: 0.85rem;
  font-weight: 500;
}
</style>
