<template>
  <el-text class="mx-1" size="large">{{ studentName }}</el-text>
  <el-tag v-if="student" data-cy="student-status">{{ student.enabled ? 'Active' : 'Inactive' }}</el-tag>
  <el-button v-if="student?.enabled && (userInfo.isAdmin || userInfo.isInstructor)"
    data-cy="deactivate-open" type="danger" @click="warning = true">Deactivate</el-button>
  <el-dialog v-model="warning" title="Deactivate student?" width="min(540px, 90vw)"
    :close-on-click-modal="!pending" :close-on-press-escape="!pending" :show-close="!pending">
    <p>This student will lose access, including an existing login, and cannot submit weekly activity reports or peer evaluations.</p>
    <p>The account, team membership, and submitted work will be retained. The student will no longer owe work or receive new peer evaluations. The account can be reactivated in the future.</p>
    <template #footer>
      <el-button data-cy="deactivate-cancel" :disabled="pending" @click="warning = false">Cancel</el-button>
      <el-button data-cy="deactivate-confirm" type="danger" :loading="pending" :disabled="pending" @click="confirmDeactivation">Confirm deactivation</el-button>
    </template>
  </el-dialog>
  <StudentActivities :studentId="studentId" />
  <el-divider />
  <StudentEvaluations :studentId="studentId" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import StudentActivities from '../activities/admin/StudentActivities.vue'
import StudentEvaluations from '../evaluations/admin/StudentEvaluations.vue'

import { useRoute } from 'vue-router'
import { findStudentById, deactivateStudent } from '@/apis/student'
import { useUserInfoStore } from '@/stores/userInfo'
import { ElMessage } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FindStudentByIdResponse } from '@/apis/student/types'

const route = useRoute()

const studentId: number = Number(route.params.studentId)
const studentName = ref<string>('')

const userInfo = useUserInfoStore()
const student = ref<Student>()
const warning = ref(false)
const pending = ref(false)
async function loadStudent() {
  const result: FindStudentByIdResponse = await findStudentById(studentId)
  student.value = result.data
  studentName.value = result.data.firstName + ' ' + result.data.lastName
}
onMounted(loadStudent)
async function confirmDeactivation() {
  if (pending.value) return
  pending.value = true
  try {
    const result = await deactivateStudent(studentId)
    student.value = result.data
    warning.value = false
    ElMessage.success('Student deactivated successfully')
    await loadStudent()
  } catch {
    // The shared request handler presents errors, including an already-inactive conflict.
    warning.value = false
    await loadStudent().catch(() => {})
  } finally {
    pending.value = false
  }
}
</script>

<style scoped></style>
