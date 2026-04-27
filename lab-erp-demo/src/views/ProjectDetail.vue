<template>
  <div v-if="loading" class="loading-state">
    <div class="spinner"></div> 加载项目中...
  </div>

  <div v-else-if="project" class="detail-container animate-fade-in">
    <div class="header-section">
      <div class="project-title-row">
        <div class="type-icon-wrapper" :class="project.flowType">
  <span class="flow-type-icon">
    {{ project.flowType === 'PRODUCT' ? '💻' : (project.flowType === 'RESEARCH' ? '🧪' : '📊') }}
  </span>
          <span class="type-text-tiny">
    {{ project.flowType === 'PRODUCT' ? '产品研发' : (project.flowType === 'RESEARCH' ? '科研创新' : '项目交付') }}
  </span>
        </div>
        <h1 class="p-name">{{ project.name }}</h1>

        <el-dropdown v-if="isManager && !isProductFlow && !isResearchFlow" trigger="click" @command="handleStatusChange" class="status-dropdown">
          <span class="status-badge tech-badge" :class="getActiveStatus(project)">
            {{ formatDynamicStatus(project) }} <i class="el-icon-arrow-down"></i>
          </span>
          <template #dropdown>
            <el-dropdown-menu class="tech-menu">
              <el-dropdown-item v-for="(label, value) in getStatusOptions(project.flowType)"
                                :key="value" :command="value"
                                :disabled="isProjectStatusOptionDisabled(value)">
                {{ label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span v-else class="status-badge static-badge" :class="getActiveStatus(project)">
          {{ formatDynamicStatus(project) }}
        </span>
      </div>
      <p class="p-desc">{{ displayDescription }}</p>
      <div v-if="String(project.flowType || '').toUpperCase() === 'PROJECT'" class="report-link-row">
        <span class="report-link-label">项目评级：</span>
        <span class="report-link">{{ syncedProjectTier }}</span>
      </div>
      <div v-if="project.feasibilityReportUrl" class="report-link-row">
        <span class="report-link-label">可行性报告：</span>
        <button type="button" class="report-link report-link-button" @click="openLatestFeasibilityReport">
          {{ latestFeasibilityAssetEntry?.name || '打开可行性报告' }}
        </button>
      </div>
      <div v-if="userStore.isErpLoggedIn" class="detail-cta-row">
        <el-button type="primary" plain @click="showTravelReimbursementDialog = true">✈️ 提交出差报销</el-button>
        <el-button v-if="canDeleteCurrentProject" type="danger" plain @click="deleteCurrentProject">删除项目</el-button>
      </div>
    </div>

    <div v-if="isProductFlow" class="panel product-stepper-panel">
      <div class="panel-header-row">
        <h3 class="panel-title">🧭 产品研发状态机</h3>
        <span class="execution-tag">进度 {{ productProgress }}%</span>
      </div>
      <div class="product-stepper-track">
        <div
          v-for="(step, idx) in productFlowSteps"
          :key="step.status"
          class="product-stepper-node"
          :class="{
            done: isProductStepDone(idx),
            current: isProductStepCurrent(idx),
            locked: isProductStepLocked(idx),
            active: selectedProductStatus === step.status
          }"
          :title="isProductStepLocked(idx) ? getProductStepLockReason(step.status) : ''"
          @click="selectProductStep(step.status, idx)"
        >
          <div class="node-dot">{{ isProductStepDone(idx) ? '✓' : (idx + 1) }}</div>
          <div class="node-label">{{ step.label }}</div>
          <div class="node-owner">负责人：{{ getStageOwnerName(step.status) }}</div>
        </div>
      </div>
    </div>

    <div v-else-if="isResearchFlow" class="panel product-stepper-panel">
      <div class="panel-header-row">
        <h3 class="panel-title">🧭 科研创新状态机</h3>
        <span class="execution-tag">进度 {{ researchProgress }}%</span>
      </div>
      <div class="product-stepper-track">
        <div
          v-for="(step, idx) in researchFlowSteps"
          :key="step.status"
          class="product-stepper-node"
          :class="{
            done: isResearchStepDone(idx),
            current: isResearchStepCurrent(idx),
            locked: isResearchStepLocked(idx),
            active: selectedResearchStatus === step.status
          }"
          :title="isResearchStepLocked(idx) ? getResearchStepLockReason(step.status) : ''"
          @click="selectResearchStep(step.status, idx)"
        >
          <div class="node-dot">{{ isResearchStepDone(idx) ? '✓' : (idx + 1) }}</div>
          <div class="node-label">{{ step.label }}</div>
          <div class="node-owner">负责人：{{ getStageOwnerName(step.status) }}</div>
        </div>
      </div>
    </div>

    <div v-else class="panel product-stepper-panel">
      <div class="panel-header-row">
        <h3 class="panel-title">🧭 项目交付状态机</h3>
        <span class="execution-tag">进度 {{ projectProgress }}%</span>
      </div>
      <div class="product-stepper-track project-stepper-track">
        <div
          v-for="(step, idx) in projectFlowSteps"
          :key="step.status"
          class="product-stepper-node"
          :class="{
            done: isProjectStepDone(idx),
            current: isProjectStepCurrent(idx),
            locked: isProjectStepLocked(idx),
            active: selectedProjectStatus === step.status
          }"
          :title="isProjectStepLocked(idx) ? getProjectStepLockReason(step.status) : ''"
          @click="selectProjectStep(step.status, idx)"
        >
          <div class="node-dot">{{ isProjectStepDone(idx) ? '✓' : (idx + 1) }}</div>
          <div class="node-label">{{ step.label }}</div>
          <div class="node-owner">负责人：{{ getStageOwnerName(step.status) }}</div>
        </div>
      </div>
    </div>

    <div v-if="isProductFlow" class="detail-body-grid">
      <div class="flow-column left-column">
        <div class="panel smart-info-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">🧠 动态信息面板</h3>
            <span class="execution-tag">{{ currentProductStepLabel }}</span>
          </div>

          <div v-if="selectedProductStatus === 'IDEA'" class="smart-block-list">
            <div class="smart-block"><div class="execution-label">预计项目投入</div><div class="execution-text">{{ project.budget || 0 }}</div></div>
            <div class="smart-block"><div class="execution-label">目标用户群</div><div class="execution-text">{{ ideaFields.targetUsers }}</div></div>
            <div class="smart-block"><div class="execution-label">主打功能点</div><div class="execution-text">{{ ideaFields.coreFeatures }}</div></div>
            <div class="smart-block"><div class="execution-label">用途</div><div class="execution-text">{{ ideaFields.useCase }}</div></div>
            <div class="smart-block"><div class="execution-label">针对的问题</div><div class="execution-text">{{ ideaFields.problemStatement }}</div></div>
            <div class="smart-block"><div class="execution-label">技术栈和深度</div><div class="execution-text">{{ ideaFields.techStackDesc }}</div></div>
          </div>

          <div v-else-if="selectedProductStatus === 'DEMO_EXECUTION'" class="smart-block-list">
            <div class="smart-block"><div class="execution-label">Demo 工程师人数</div><div class="execution-text">{{ demoEngineerCount }}/4</div></div>
            <div class="smart-block"><div class="execution-label">上传槽位进度</div><div class="execution-text">{{ demoUploadedCount }}/4 已完成</div></div>
            <div v-for="item in demoResponsibilitySummary" :key="`demo-summary-${item.category}`" class="smart-block">
              <div class="execution-label">{{ item.label }}</div>
              <div class="execution-text">{{ item.ownerName }} · {{ item.uploaded ? '已上传' : '待上传' }}</div>
            </div>
          </div>

          <div v-else-if="selectedProductStatus === 'MEETING_DECISION'" class="smart-block-list">
            <div class="smart-block"><div class="execution-label">会议参会成员（关联用户）</div><div class="execution-text">{{ meetingParticipantSummary }}</div></div>
          </div>

          <div v-else class="smart-block-list">
            <div class="smart-block"><div class="execution-label">阶段说明</div><div class="execution-text">{{ currentStepDescription }}</div></div>
          </div>
        </div>

        <div class="panel assets-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">📂 最新成果</h3>
          </div>
          <div v-if="!project.uploads || project.uploads.length === 0" class="empty-panel-text">暂无文件上传</div>
          <div v-else class="file-list">
            <div v-for="(file, idx) in project.uploads" :key="`product-upload-${idx}`" class="file-item">
              <div class="file-icon" :class="file.type">{{ (file.type || 'FILE').substring(0,4).toUpperCase() }}</div>
              <div class="file-info">
                <div class="file-name">{{ file.name }}</div>
                <div class="file-meta">{{ file.user }} · {{ file.time }}</div>
              </div>
              <button class="download-btn" @click.stop="handleDownload(file)">↓</button>
            </div>
          </div>
        </div>

        <div class="panel git-repo-panel">
          <div class="panel-header-row compact-header">
            <h3 class="panel-title">🧩 Git 仓库模块</h3>
          </div>
          <div class="git-form-grid">
            <el-input v-model="gitRepoForm.repositoryUrl" placeholder="仓库地址（例如：https://github.com/org/repo）" />
            <el-input v-model="gitRepoForm.accessToken" type="password" show-password placeholder="Token 密钥" />
            <el-input v-model="gitRepoForm.branch" placeholder="分支（默认 main）" />
            <el-button type="primary" :loading="gitRepoSubmitting" @click="createGitRepositoryConfig">新增仓库配置</el-button>
          </div>
          <div v-if="!gitRepositories.length" class="empty-panel-text">暂无 Git 仓库配置</div>
          <div v-else class="git-repo-list">
            <div
              v-for="repo in gitRepositories"
              :key="`git-repo-${repo.id}`"
              class="git-repo-item"
              :class="{ active: selectedGitRepoId === repo.id }"
              @click="selectGitRepository(repo.id)"
            >
              <div class="git-repo-head">
                <strong>{{ repo.repositoryUrl }}</strong>
                <span class="schedule-role">{{ repo.provider || 'GITHUB' }} · {{ repo.branch || 'main' }}</span>
              </div>
              <div class="execution-text">创建人：{{ repo.createdBy || '未知' }} · 创建时间：{{ formatMilestoneTimestamp(repo.createdAt) }}</div>
              <div class="execution-text">测试状态：{{ repo.lastTestStatus || 'NOT_TESTED' }} · {{ repo.lastTestMessage || '尚未测试链接' }}</div>
              <div class="file-action-row">
                <button class="text-action" @click.stop="testGitRepository(repo)">测试链接</button>
                <button class="text-action" @click.stop="fetchGitRepositoryLogs(repo.id)">刷新日志</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="flow-column right-column">
        <div class="panel action-engine-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">⚙️ 动作引擎</h3>
          </div>

          <div class="execution-text">{{ currentActionHint }}</div>
          <div class="action-row">
            <el-button
              type="primary"
              :disabled="!canMoveToNextProductStage"
              :title="nextStageDisabledReason"
              @click="moveToNextProductStage"
            >
              {{ nextStageButtonText }}
            </el-button>
            <el-button v-if="showAssignEngineerAction" @click="openPromotionSetupDialog">指派工程师</el-button>
            <el-button v-if="showPromoteAction" type="success" @click="openTestingDecisionDialog">发起公测</el-button>
          </div>
          <div v-if="nextStageDisabledReason" class="lock-reason">{{ nextStageDisabledReason }}</div>
        </div>

        <div class="panel project-task-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">🧩 任务分配</h3>
            <el-button v-if="canManageProductTaskAssignments" type="primary" size="small" plain :loading="productTaskAssignmentSaving" @click="saveProductTaskAssignments">保存分配</el-button>
          </div>
          <div v-if="productTaskAssignmentLoading" class="empty-panel-text">任务分配加载中...</div>
          <div v-else-if="!productTaskAssignments.length" class="empty-panel-text">无安排</div>
          <div v-else class="schedule-list">
            <div v-for="item in productTaskAssignments" :key="`assign-${item.userId}`" class="schedule-item">
              <div class="schedule-header">
                <strong>{{ item.name }}</strong>
                <span class="schedule-role">{{ formatRole(item.role) }}</span>
              </div>
              <template v-if="canManageProductTaskAssignments">
                <div class="schedule-editor-row">
                  <el-input v-model="item.taskName" placeholder="分配任务（未填则显示无安排）" />
                  <el-input v-model="item.expectedOutput" placeholder="预期产出（可选）" />
                </div>
                <div class="schedule-editor-row">
                  <el-date-picker v-model="item.expectedEndDate" type="datetime" format="YYYY-MM-DD HH:mm" value-format="x" placeholder="截止时间（必填）" style="width: 100%" />
                </div>
              </template>
              <template v-else>
                <div class="execution-text">任务：{{ item.taskName || '无安排' }}</div>
                <div class="execution-text">产出：{{ item.expectedOutput || '无安排' }}</div>
                <div class="execution-text">截止：{{ item.expectedEndDate ? formatMilestoneTimestamp(item.expectedEndDate) : '无安排' }}</div>
              </template>
            </div>
          </div>
        </div>

        <div class="panel team-panel">
          <div class="panel-header-row compact-header">
            <h3 class="panel-title">👥 产品成员</h3>
            <el-button v-if="canManageProductMembers" type="primary" size="small" plain @click="openProductMemberDialog">成员管理</el-button>
          </div>
          <div class="avatar-group">
            <div v-for="(m, idx) in sortedSquadMembers" :key="m.userId" class="member-item" :class="{ prioritized: idx === 0 && isLeadMember(m) }">
              <img :src="m.hiddenAvatar ? hiddenAvatar : (m.avatar || defaultAvatar)" class="avatar" :title="m.name">
              <div v-if="showResponsibilityRatio(m)" class="ratio-badge-group">
                <span v-if="isMergedDataManagerMember(m)" class="ratio-badge ratio-badge-manager">总计 {{ getMemberCombinedResponsibility(m) }}</span>
                <template v-else>
                  <span v-if="Number(m.managerResponsibilityRatio || 0) > 0" class="ratio-badge ratio-badge-manager">管理 {{ m.managerResponsibilityRatio }}</span>
                  <span v-if="Number(m.executionResponsibilityRatio || 0) > 0" class="ratio-badge ratio-badge-exec">执行 {{ m.executionResponsibilityRatio }}</span>
                </template>
              </div>
              <span class="role-badge">{{ formatMemberIdentityTag(m) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="isResearchFlow" class="detail-body-grid">
      <div class="flow-column left-column">
        <div class="panel smart-info-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">🧠 动态信息面板</h3>
            <span class="execution-tag">{{ currentResearchStepLabel }}</span>
          </div>
          <div class="smart-block-list">
            <div class="smart-block"><div class="execution-label">创新点</div><div class="execution-text">{{ project.description || '未填写' }}</div></div>
            <div class="smart-block"><div class="execution-label">预算</div><div class="execution-text">{{ project.budget || 0 }}</div></div>
            <div class="smart-block"><div class="execution-label">主持人</div><div class="execution-text">{{ hostName }}</div></div>
            <div class="smart-block"><div class="execution-label">总工程师</div><div class="execution-text">{{ chiefEngineerName }}</div></div>
            <div class="smart-block"><div class="execution-label">阶段说明</div><div class="execution-text">{{ currentResearchStepDescription }}</div></div>
          </div>
        </div>

        <div class="panel timeline-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">🗓 进度节点</h3>
            <el-button v-if="isManager" type="primary" size="small" plain @click="showAddMilestone = true">新增</el-button>
          </div>
          <div v-if="!project.milestones || project.milestones.length === 0" class="empty-panel-text">暂无里程碑计划</div>
          <el-timeline v-else style="padding-left: 0;">
            <el-timeline-item
              v-for="(node, index) in project.milestones"
              :key="index"
              :timestamp="formatMilestoneTimestamp(node.date)"
              :type="node.status === 'done' ? 'success' : 'primary'"
            >
              <span class="node-title" :class="node.status">{{ node.title }}</span>
            </el-timeline-item>
          </el-timeline>
        </div>

        <div class="panel assets-panel research-key-assets-panel">
          <div class="panel-header-row compact-header">
            <h3 class="panel-title">🗂 科研关键文件</h3>
          </div>
          <div class="smart-block-list">
            <div v-for="item in researchKeyFileSummary" :key="item.category" class="smart-block">
              <div class="execution-label">{{ item.label }}</div>
              <div class="execution-text">责任人：{{ item.ownerName }}</div>
              <div class="execution-text">状态：{{ item.uploaded ? '已上传' : '待上传' }}</div>
              <el-button v-if="item.canUpload" type="primary" :loading="uploading && pendingAssetCategory === item.category" size="small" plain @click="triggerResearchKeyFileUpload(item.category)">上传{{ item.label }}</el-button>
            </div>
          </div>
        </div>

        <div class="panel assets-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">📂 最新成果</h3>
          </div>
          <div v-if="!project.uploads || project.uploads.length === 0" class="empty-panel-text">暂无文件上传</div>
          <div v-else class="file-list">
            <div v-for="(file, idx) in project.uploads" :key="`research-upload-${idx}`" class="file-item">
              <div class="file-icon" :class="file.type">{{ (file.type || 'FILE').substring(0,4).toUpperCase() }}</div>
              <div class="file-info">
                <div class="file-name">{{ file.name }}</div>
                <div class="file-meta">{{ file.user }} · {{ file.time }}</div>
              </div>
              <button class="download-btn" @click.stop="handleDownload(file)">↓</button>
            </div>
          </div>
        </div>

        <div class="panel git-repo-panel">
          <div class="panel-header-row compact-header">
            <h3 class="panel-title">🧩 Git 仓库模块</h3>
          </div>
          <div class="git-form-grid">
            <el-input v-model="gitRepoForm.repositoryUrl" placeholder="仓库地址（例如：https://github.com/org/repo）" />
            <el-input v-model="gitRepoForm.accessToken" type="password" show-password placeholder="Token 密钥" />
            <el-input v-model="gitRepoForm.branch" placeholder="分支（默认 main）" />
            <el-button type="primary" :loading="gitRepoSubmitting" @click="createGitRepositoryConfig">新增仓库配置</el-button>
          </div>
          <div v-if="!gitRepositories.length" class="empty-panel-text">暂无 Git 仓库配置</div>
          <div v-else class="git-repo-list">
            <div v-for="repo in gitRepositories" :key="`research-git-${repo.id}`" class="git-repo-item" :class="{ active: selectedGitRepoId === repo.id }" @click="selectGitRepository(repo.id)">
              <div class="git-repo-head">
                <strong>{{ repo.repositoryUrl }}</strong>
                <span class="schedule-role">{{ repo.provider || 'GITHUB' }} · {{ repo.branch || 'main' }}</span>
              </div>
              <div class="execution-text">创建人：{{ repo.createdBy || '未知' }} · 创建时间：{{ formatMilestoneTimestamp(repo.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="flow-column right-column">
        <div class="panel action-engine-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">⚙️ 动作引擎</h3>
          </div>
          <div class="execution-text">{{ currentResearchActionHint }}</div>
          <div class="action-row">
            <el-button type="primary" :disabled="!canMoveToNextResearchStage" :title="nextResearchStageDisabledReason" @click="moveToNextResearchStage">
              {{ nextResearchStageButtonText }}
            </el-button>
            <el-button v-if="selectedResearchStatus === 'DESIGN' || selectedResearchStatus === 'EXECUTION'" @click="setResearchExecutionMode('MODE_A_PARALLEL')">并行模式A</el-button>
            <el-button v-if="selectedResearchStatus === 'DESIGN' || selectedResearchStatus === 'EXECUTION'" @click="setResearchExecutionMode('MODE_B_ITERATIVE')">迭代模式B</el-button>
            <el-button v-if="selectedResearchStatus === 'ARCHIVE'" type="success" @click="archiveResearchMiddleware">入库中间件</el-button>
          </div>
          <div v-if="nextResearchStageDisabledReason" class="lock-reason">{{ nextResearchStageDisabledReason }}</div>
        </div>

        <div class="panel team-panel">
          <div class="panel-header-row compact-header">
            <h3 class="panel-title">👥 科研成员</h3>
            <el-button v-if="canManageResearchMembers" type="primary" size="small" plain @click="openResearchMemberDialog">成员管理</el-button>
          </div>
          <div class="avatar-group">
            <div v-for="(m, idx) in sortedSquadMembers" :key="m.userId" class="member-item" :class="{ prioritized: idx === 0 && isLeadMember(m) }">
              <img :src="m.hiddenAvatar ? hiddenAvatar : (m.avatar || defaultAvatar)" class="avatar" :title="m.name">
              <span class="role-badge">{{ formatMemberIdentityTag(m) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="product-flow-grid">
        <div class="panel smart-info-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">🧠 动态信息面板</h3>
            <span class="execution-tag">{{ currentProjectStepLabel }}</span>
          </div>
          <div class="smart-block-list">
            <div class="smart-block"><div class="execution-label">可行性报告状态</div><div class="execution-text">{{ feasibilityReportStatusText }}</div></div>
            <div class="smart-block">
              <div class="execution-label">项目评级</div>
              <template v-if="canEditProjectDynamicInfo">
                <el-select v-model="dynamicInfoForm.projectTier" placeholder="请选择项目评级" style="width: 100%">
                  <el-option v-for="item in projectTierOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </template>
              <div v-else class="execution-text">{{ syncedProjectTier }}</div>
            </div>
            <div class="smart-block">
              <div class="execution-label">关键目标</div>
              <template v-if="canEditProjectDynamicInfo">
                <el-input
                  v-model="dynamicInfoForm.goalDescription"
                  type="textarea"
                  :rows="3"
                  maxlength="300"
                  show-word-limit
                  placeholder="请输入项目关键目标"
                />
              </template>
              <div v-else class="execution-text">{{ dynamicGoalDescription }}</div>
            </div>
            <div class="smart-block">
              <div class="execution-label">技术栈与深度</div>
              <template v-if="canEditProjectDynamicInfo">
                <el-input
                  v-model="dynamicInfoForm.techStackDescription"
                  type="textarea"
                  :rows="3"
                  maxlength="300"
                  show-word-limit
                  placeholder="请输入技术栈与深度说明"
                />
              </template>
              <div v-else class="execution-text">{{ dynamicTechStackDescription }}</div>
            </div>
            <div class="smart-block">
              <div class="execution-label">实施状态（数据工程师维护）</div>
              <div v-if="canEditProjectDynamicInfo" class="schedule-editor-row">
                <el-input
                  v-model="dynamicInfoForm.implementationStatus"
                  placeholder="例如：已完成实施准备确认 / 依赖已就绪"
                  maxlength="120"
                  show-word-limit
                />
              </div>
              <div v-else class="execution-text">{{ implementationStatusText }}</div>
            </div>
            <div class="smart-block">
              <div class="execution-label">预计收入（元）</div>
              <template v-if="canEditProjectDynamicInfo">
                <el-input
                  v-model="dynamicInfoForm.estimatedRevenue"
                  type="number"
                  placeholder="请输入预计收入金额"
                />
              </template>
              <div v-else class="execution-text">{{ formatMoney(project.value?.estimatedRevenue || project.value?.budget) }}</div>
            </div>
            <div v-if="canEditProjectDynamicInfo" class="smart-block smart-block-actions">
              <div class="action-row">
                <el-button type="primary" size="small" :loading="projectDynamicInfoSaving" @click="saveProjectDynamicInfo">保存动态信息</el-button>
              </div>
            </div>
        </div>
      </div>

      <div class="panel action-engine-panel">
        <div class="panel-header-row">
          <h3 class="panel-title">⚙️ 动作引擎</h3>
        </div>
        <div class="execution-text">{{ currentProjectActionHint }}</div>
        <div class="action-row">
          <el-button
            type="primary"
            :disabled="!canMoveToNextProjectStage"
            :title="nextProjectStageDisabledReason"
            @click="moveToNextProjectStage"
          >
            {{ nextProjectStageButtonText }}
          </el-button>
        </div>
        <div v-if="nextProjectStageDisabledReason" class="lock-reason">{{ nextProjectStageDisabledReason }}</div>
      </div>
    </div>

    <div v-if="!isProductFlow" class="main-grid">
      <div class="left-col">
      <div v-if="!isProductFlow && !isResearchFlow" class="panel project-task-panel project-task-panel-left">
          <div class="panel-header-row">
            <h3 class="panel-title">🧩 任务分配</h3>
            <el-button
              v-if="canManageProjectTaskAssignments"
              type="primary"
              size="small"
              :loading="projectTaskAssignmentSaving"
              @click="saveProjectTaskAssignments"
            >保存分配</el-button>
            <span v-else class="permission-hint">仅项目负责人可编辑</span>
          </div>
          <div v-if="projectTaskAssignmentLoading" class="empty-panel-text">任务分配加载中...</div>
          <div v-else-if="!projectTaskAssignments.length" class="empty-panel-text">无安排</div>
          <div v-else class="schedule-list">
            <div v-for="item in projectTaskAssignments" :key="`project-assign-${item.userId}`" class="schedule-item">
              <div class="schedule-header">
                <strong>{{ item.name }}</strong>
                <span class="schedule-role">{{ formatRole(item.role) }}</span>
              </div>
              <template v-if="canManageProjectTaskAssignments">
                <div class="schedule-editor-row">
                  <el-input v-model="item.taskName" placeholder="任务内容（未填则显示无安排）" />
                  <el-input v-model="item.expectedOutput" placeholder="预期产出（可选）" />
                </div>
                <div class="schedule-editor-row">
                  <el-date-picker
                    v-model="item.expectedEndDate"
                    type="datetime"
                    format="YYYY-MM-DD HH:mm"
                    value-format="x"
                    placeholder="截止时间"
                    style="width: 100%"
                  />
                </div>
              </template>
              <template v-else>
                <div class="execution-text">任务：{{ item.taskName || '无安排' }}</div>
                <div class="execution-text">产出：{{ item.expectedOutput || '待定义产出' }}</div>
                <div class="execution-text">截止：{{ item.expectedEndDate ? formatMilestoneTimestamp(item.expectedEndDate) : '无安排' }}</div>
              </template>
            </div>
          </div>
        </div>

      <div v-if="isProductFlow" class="panel timeline-panel">
        <div class="panel-header-row">
          <h3 class="panel-title">🧩 任务分配</h3>
          <el-button v-if="canManageProductTaskAssignments" type="primary" size="small" plain :loading="productTaskAssignmentSaving" @click="saveProductTaskAssignments">保存分配</el-button>
        </div>

        <div v-if="productTaskAssignmentLoading" class="empty-panel-text">任务分配加载中...</div>
        <div v-else-if="!productTaskAssignments.length" class="empty-panel-text">无安排</div>
        <div v-else class="schedule-list">
          <div v-for="item in productTaskAssignments" :key="`assign-${item.userId}`" class="schedule-item">
            <div class="schedule-header">
              <strong>{{ item.name }}</strong>
              <span class="schedule-role">{{ formatRole(item.role) }}</span>
            </div>
            <template v-if="canManageProductTaskAssignments">
              <div class="schedule-editor-row">
                <el-input v-model="item.taskName" placeholder="分配任务（未填则显示无安排）" />
                <el-input v-model="item.expectedOutput" placeholder="预期产出（可选）" />
              </div>
              <div class="schedule-editor-row">
                <el-date-picker v-model="item.expectedEndDate" type="datetime" format="YYYY-MM-DD HH:mm" value-format="x" placeholder="截止时间（必填）" style="width: 100%" />
              </div>
            </template>
            <template v-else>
              <div class="execution-text">任务：{{ item.taskName || '无安排' }}</div>
              <div class="execution-text">产出：{{ item.expectedOutput || '无安排' }}</div>
              <div class="execution-text">截止：{{ item.expectedEndDate ? formatMilestoneTimestamp(item.expectedEndDate) : '无安排' }}</div>
            </template>
          </div>
        </div>
      </div>

      <div v-else-if="!isResearchFlow" class="panel timeline-panel">
        <div class="panel-header-row">
          <h3 class="panel-title">🗓 进度节点</h3>
        </div>

        <div class="execution-text">仅展示窗口，不可编辑</div>
        <div v-if="!projectTaskAssignmentsForTimeline.length" class="empty-panel-text">暂无任务分配节点</div>
        <el-timeline v-else style="padding-left: 0;">
          <el-timeline-item
              v-for="node in projectTaskAssignmentsForTimeline"
              :key="`timeline-${node.userId}`"
              :timestamp="node.expectedEndDate ? formatMilestoneTimestamp(node.expectedEndDate) : '截止未设置'"
              type="primary"
          >
            <span class="node-title">{{ node.name }} · {{ node.taskName || '无安排' }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>

      <div v-else class="panel timeline-panel">
        <div class="panel-header-row">
          <h3 class="panel-title">🗓 进度节点</h3>
          <el-button v-if="isManager" type="primary" size="small" plain @click="showAddMilestone = true">新增</el-button>
        </div>

        <div v-if="!project.milestones || project.milestones.length === 0" class="empty-panel-text">暂无里程碑计划</div>
        <el-timeline v-else style="padding-left: 0;">
          <el-timeline-item
              v-for="(node, index) in project.milestones"
              :key="index"
              :timestamp="formatMilestoneTimestamp(node.date)"
              :type="node.status === 'done' ? 'success' : 'primary'"
          >
            <span class="node-title" :class="node.status">{{ node.title }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>
      </div>

      <div class="right-col">
        <div class="panel team-panel">
          <div class="panel-header-row compact-header">
            <h3 class="panel-title">👥 {{ squadTitle }}</h3>
            <el-button v-if="canManageProductMembers" type="primary" size="small" plain @click="openProductMemberDialog">成员管理</el-button>
            <el-button v-else-if="canManageProjectMembers" type="primary" size="small" plain @click="openProjectMemberDialog">成员管理</el-button>
          </div>
          <div class="avatar-group">
            <div
              v-for="(m, idx) in sortedSquadMembers"
              :key="m.userId"
              class="member-item"
              :class="{ prioritized: idx === 0 && isLeadMember(m) }"
            >
              <img :src="m.hiddenAvatar ? hiddenAvatar : (m.avatar || defaultAvatar)" class="avatar" :title="m.name">
              <div v-if="showResponsibilityRatio(m)" class="ratio-badge-group">
                <span v-if="Number(m.managerResponsibilityRatio || 0) > 0" class="ratio-badge ratio-badge-manager">管理 {{ m.managerResponsibilityRatio }}</span>
                <span v-if="Number(m.executionResponsibilityRatio || 0) > 0" class="ratio-badge ratio-badge-exec">执行 {{ m.executionResponsibilityRatio }}</span>
              </div>
              <span class="role-badge">{{ formatMemberIdentityTag(m) }}</span>
            </div>
            <div v-if="canBuildTeam" class="add-member-btn" @click="inviteMember">+</div>
          </div>
        </div>

        <div v-if="isProjectFlow" class="panel earnings-panel">
          <div class="panel-header-row compact-header earnings-header">
            <div>
              <h3 class="panel-title">💸 我的预计分红</h3>
              <div class="earnings-caption">基于项目等级、人力成本跑批与当前成员分配规则</div>
            </div>
            <span v-if="projectEarnings?.tierLabel" class="earnings-tier-pill">{{ projectEarnings.tierLabel }}</span>
          </div>

          <div v-if="projectEarningsLoading" class="empty-panel-text">正在测算当前项目的预计分红...</div>
          <div v-else-if="projectEarnings" class="earnings-body">
            <div class="earnings-hero">
              <span class="earnings-pool-pill" :class="earningsPoolTone">{{ projectEarnings.poolLabel }}</span>
              <div class="earnings-amount">¥{{ formatMoney(projectEarnings.predictedAmount) }}</div>
              <div class="earnings-footnote">{{ projectEarnings.explanation }}</div>
            </div>

            <div class="earnings-grid">
              <div class="smart-block">
                <div class="execution-label">预计收入</div>
                <div class="execution-text">¥{{ formatMoney(projectEarnings.estimatedRevenue) }}</div>
              </div>
              <div class="smart-block">
                <div class="execution-label">最新人力成本</div>
                <div class="execution-text">¥{{ formatMoney(projectEarnings.humanCost) }}</div>
              </div>
              <div class="smart-block">
                <div class="execution-label">剩余利润</div>
                <div class="execution-text">¥{{ formatMoney(projectEarnings.remainingProfit) }}</div>
              </div>
              <div class="smart-block">
                <div class="execution-label">分红池比例</div>
                <div class="execution-text">{{ formatPercent(projectEarnings.poolRatio) }}</div>
              </div>
              <div class="smart-block">
                <div class="execution-label">我的分池占比</div>
                <div class="execution-text">{{ formatPercent(projectEarnings.shareRatio) }}</div>
              </div>
              <div class="smart-block">
                <div class="execution-label">{{ projectEarnings.poolType === 'EXECUTION' ? '我的权责比' : '分池人数' }}</div>
                <div class="execution-text">{{ projectEarnings.poolType === 'EXECUTION' ? formatResponsibilityShare(projectEarnings) : `${projectEarnings.participantCount || 0} 人` }}</div>
              </div>
              <div class="smart-block">
                <div class="execution-label">项目成员数</div>
                <div class="execution-text">{{ `${projectEarnings.projectMemberCount || 0} 人` }}</div>
              </div>
            </div>

            <div class="earnings-meta-row">
              <span class="execution-tag">分红池金额 ¥{{ formatMoney(projectEarnings.poolAmount) }}</span>
              <span v-if="projectEarnings.lastCostBatchAt" class="execution-tag">最近跑批 {{ formatDateTimeLabel(projectEarnings.lastCostBatchAt) }}</span>
            </div>
          </div>
          <div v-else class="empty-panel-text">当前暂无预计分红数据。</div>
        </div>



        <div class="panel git-repo-panel">
          <div class="panel-header-row compact-header">
            <h3 class="panel-title">🧩 Git 仓库模块</h3>
          </div>

          <div class="git-form-grid">
            <el-input v-model="gitRepoForm.repositoryUrl" placeholder="仓库地址（例如：https://github.com/org/repo）" />
            <el-input v-model="gitRepoForm.accessToken" type="password" show-password placeholder="Token 密钥" />
            <el-input v-model="gitRepoForm.branch" placeholder="分支（默认 main）" />
            <el-button type="primary" :loading="gitRepoSubmitting" @click="createGitRepositoryConfig">新增仓库配置</el-button>
          </div>

          <div v-if="!gitRepositories.length" class="empty-panel-text">暂无 Git 仓库配置</div>
          <div v-else class="git-repo-list">
            <div
              v-for="repo in gitRepositories"
              :key="`git-repo-${repo.id}`"
              class="git-repo-item"
              :class="{ active: selectedGitRepoId === repo.id }"
              @click="selectGitRepository(repo.id)"
            >
              <div class="git-repo-head">
                <strong>{{ repo.repositoryUrl }}</strong>
                <span class="schedule-role">{{ repo.provider || 'GITHUB' }} · {{ repo.branch || 'main' }}</span>
              </div>
              <div class="execution-text">创建人：{{ repo.createdBy || '未知' }} · 创建时间：{{ formatMilestoneTimestamp(repo.createdAt) }}</div>
              <div class="execution-text">测试状态：{{ repo.lastTestStatus || 'NOT_TESTED' }} · {{ repo.lastTestMessage || '尚未测试链接' }}</div>
              <div class="file-action-row">
                <button class="text-action" @click.stop="testGitRepository(repo)">测试链接</button>
                <button class="text-action" @click.stop="fetchGitRepositoryLogs(repo.id)">刷新日志</button>
              </div>
            </div>
          </div>

          <div class="git-log-header">
            <strong>仓库操作日志</strong>
            <span class="execution-text">记录成员在某日某刻提交代码的行为</span>
          </div>
          <div v-if="gitRepoLogsLoading" class="empty-panel-text">日志加载中...</div>
          <div v-else-if="!gitRepoLogs.length" class="empty-panel-text">暂无可读取的仓库日志</div>
          <div v-else class="git-log-list">
            <div v-for="log in gitRepoLogs" :key="`${log.sha}-${log.pushedAt}`" class="git-log-item">
              <div class="git-log-meta">
                <strong>{{ log.authorName || '未知提交者' }}</strong>
                <span class="schedule-role">{{ formatMilestoneTimestamp(log.pushedAt) }}</span>
              </div>
              <div class="execution-text">提交：{{ log.message || '-' }}</div>
              <a v-if="log.commitUrl" class="report-link" :href="log.commitUrl" target="_blank" rel="noopener noreferrer">查看提交详情</a>
            </div>
          </div>
        </div>

      </div>
    </div>

    <div v-if="showExecutionManagementPanel || showExecutionWorkspacePanel" class="execution-grid">
      <div v-if="showExecutionManagementPanel" class="panel execution-plan-panel">
        <div class="panel-header-row">
          <h3 class="panel-title">🎯 实施管理</h3>
          <div class="action-row" v-if="isManager">
            <el-button v-if="canManageProjectMembers" type="primary" size="small" plain @click="openProjectMemberDialog">成员管理</el-button>
            <el-button type="primary" size="small" plain @click="openExecutionPlanDialog">{{ executionOverview?.plan ? '更新计划' : '设定计划' }}</el-button>
          </div>
        </div>

        <div v-if="executionOverview?.plan" class="execution-plan-content">
          <div class="execution-meta-row">
            <span class="execution-tag">评级：{{ syncedProjectTier }}</span>
          </div>
          <div class="execution-section">
            <div class="execution-label">目标描述</div>
            <div class="execution-text">{{ executionOverview.plan.goalDescription || '未设置目标' }}</div>
          </div>
          <div class="execution-section">
            <div class="execution-label">技术栈与深度</div>
            <div class="execution-text">{{ executionOverview.plan.techStackDescription || '未设置技术栈说明' }}</div>
          </div>
          <div class="execution-section">
            <div class="panel-header-row inline-subtask-header">
              <div class="execution-label">任务拆解</div>
              <el-button v-if="isManager" type="primary" size="small" plain @click="openSubtaskDialog()">新增</el-button>
            </div>
            <div v-if="!project.subtasks || project.subtasks.length === 0" class="empty-panel-text compact">暂无拆解任务</div>
            <div v-else class="schedule-list">
              <div v-for="task in project.subtasks" :key="task.id" class="schedule-item">
                <div class="schedule-header">
                  <strong>{{ task.title }}</strong>
                  <span class="schedule-role">{{ task.assigneeName || '未指派负责人' }}</span>
                </div>
                <div class="execution-text">{{ task.description || '暂无说明' }}</div>
                <div class="schedule-status-row">
                  <span class="schedule-state">{{ task.completed ? '已完成' : '待完成' }}</span>
                  <div class="file-action-row">
                    <button v-if="isManager" class="text-action" @click="openSubtaskDialog(task)">编辑</button>
                    <button v-if="isManager && !task.completed" class="text-action" @click="completeSubtask(task)">确认完成</button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="execution-section">
            <div class="execution-label">成员任务执行（全员）</div>
            <div v-if="!memberTaskCards.length" class="empty-panel-text compact">暂无成员任务规划</div>
            <div v-else class="schedule-list">
              <div v-for="item in memberTaskCards" :key="`exec-${item.userId}`" class="schedule-item">
                <div class="schedule-header">
                  <strong>{{ item.name }}</strong>
                  <span class="schedule-role">{{ formatRole(item.role) }}</span>
                </div>
                <div class="execution-text">任务：{{ item.taskName || '待规划任务' }}</div>
                <div class="execution-text">产出：{{ item.expectedOutput || '待定义产出' }}</div>
                <div class="execution-text">截止：{{ item.expectedEndDate || '待定' }}</div>
                <div class="schedule-status-row">
                  <span class="schedule-state">{{ item.managerConfirmed ? '经理已确认' : (item.completed ? '成员已提交' : '进行中') }}</span>
                  <div class="file-action-row">
                    <button v-if="isManager && !item.managerConfirmed" class="text-action" @click="confirmMemberTask(item, true)">✓ 经理确认</button>
                    <button v-if="isManager && item.managerConfirmed" class="text-action danger" @click="confirmMemberTask(item, false)">取消确认</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-panel-text">尚未设定实施目标、评级和技术栈说明</div>
      </div>

      <div v-if="showExecutionWorkspacePanel" class="panel execution-files-panel">
        <div class="panel-header-row">
          <h3 class="panel-title">🗂 文件管理</h3>
        </div>
        <div class="file-folder-grid" :class="{ 'single-folder-grid': !canManageExecutionFiles }">
          <div v-if="canManageExecutionFiles" class="file-folder-column folder-a-column">
            <div class="folder-header-row">
              <div class="folder-title">
                <strong>文件夹 A（管理层）</strong>
                <p class="folder-desc">Manager 可同时查看 A/B，并在 A 内创建子文件夹、移动文件、下载和删除。</p>
              </div>
              <div class="folder-header-actions">
                <el-button size="small" plain :disabled="!managerArchiveActivePath" @click="goToManagerArchiveParent">返回上级</el-button>
                <el-button size="small" plain @click="createManagerArchiveFolder">新建子文件夹</el-button>
                <el-button v-if="canUploadManagerExecutionFiles" size="small" type="primary" @click="triggerExecutionUpload('A_MANAGER_ARCHIVE')">上传到当前目录</el-button>
              </div>
            </div>
            <div class="archive-path-row">
              <span class="execution-tag">当前目录：{{ managerArchiveActivePathLabel }}</span>
              <button v-if="managerArchiveActivePath" class="text-action" @click="selectManagerArchiveFolder('')">回到根目录</button>
            </div>
            <div class="archive-breadcrumb-row">
              <button class="text-action" @click="selectManagerArchiveFolder('')">根目录</button>
              <template v-for="crumb in managerArchiveBreadcrumbs" :key="`crumb-${crumb.folderPath}`">
                <span class="archive-breadcrumb-sep">/</span>
                <button class="text-action" @click="selectManagerArchiveFolder(crumb.folderPath)">{{ crumb.folderName }}</button>
              </template>
            </div>
            <div v-if="managerArchiveChildFolders.length" class="archive-folder-list">
              <button v-for="folder in managerArchiveChildFolders" :key="`folder-${folder.folderPath}`" type="button" class="archive-folder-item" @click="selectManagerArchiveFolder(folder.folderPath)">
                <span class="archive-folder-name">📁 {{ folder.folderName }}</span>
                <span class="archive-folder-path">{{ folder.folderPath }}</span>
              </button>
            </div>
            <div v-if="!managerArchiveChildFolders.length && !managerArchiveVisibleFiles.length" class="empty-panel-text">当前目录暂无文件或子文件夹</div>
            <div v-if="managerArchiveVisibleFiles.length" class="execution-file-list">
              <div v-for="file in managerArchiveVisibleFiles" :key="`m-${file.id}`" class="execution-file-item">
                <div>
                  <div class="file-name">{{ file.fileName }}</div>
                  <div class="file-meta">{{ formatManagerArchiveFileMeta(file) }}</div>
                </div>
                <div class="file-action-row">
                  <button v-if="file.canRecategorize" class="text-action" @click="recategorizeExecutionFile(file)">移动</button>
                  <button v-if="file.canDownload" class="text-action" @click="downloadExecutionFile(file)">下载</button>
                  <button v-if="file.canDelete" class="text-action danger" @click="deleteExecutionFile(file)">删除</button>
                </div>
              </div>
            </div>
          </div>

          <div class="file-folder-column folder-b-column" :class="{ 'folder-b-column-full': !canManageExecutionFiles }">
            <div class="folder-header-row">
              <div class="folder-title">
                <strong>文件夹 B（实施过程）</strong>
                <p class="folder-desc">{{ canManageExecutionFiles ? '工程师上传个人成果，工程师之间互不可见，Manager 可统筹查看。' : '当前仅显示你上传到文件夹 B 的实施成果。' }}</p>
              </div>
              <div v-if="canUploadEngineerExecutionFiles" class="folder-header-actions">
                <el-button size="small" type="primary" plain @click="triggerExecutionUpload('B_ENGINEER_WORK')">上传成果</el-button>
                <el-button size="small" plain @click="triggerExecutionFolderUpload('B_ENGINEER_WORK')">上传文件夹</el-button>
              </div>
            </div>
            <div v-if="!folderBVisibleFiles.length" class="empty-panel-text">暂无实施过程文件</div>
            <div v-else class="execution-file-list">
              <div v-for="file in folderBVisibleFiles" :key="file.renderKey" class="execution-file-item">
                <div>
                  <div class="file-name">{{ file.fileName }}</div>
                  <div class="file-meta">{{ file.metaText }}</div>
                </div>
                <div class="file-action-row">
                  <button v-if="file.canDownload" class="text-action" @click="handleFolderBDownload(file)">下载</button>
                  <button v-if="file.canDelete" class="text-action danger" @click="deleteExecutionFile(file)">删除</button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <input ref="executionFileInputRef" type="file" style="display: none" @change="handleExecutionFileChange">
        <input ref="executionFolderInputRef" type="file" webkitdirectory directory multiple style="display: none" @change="handleExecutionFolderChange">
      </div>
    </div>

    <div v-if="canUseTeamChat" class="panel chat-panel">
      <div class="panel-header-row">
        <h3 class="panel-title">💬 团队聊天</h3>
      </div>
      <div v-if="chatLoading" class="empty-panel-text">聊天加载中...</div>
      <div v-else-if="!chatMessages.length" class="empty-panel-text">当前阶段暂无聊天消息</div>
      <div v-else class="chat-message-list">
        <div v-for="msg in chatMessages" :key="msg.id" class="chat-message-item">
          <div class="chat-meta">{{ msg.senderName }} · {{ msg.createdAt }}</div>
          <div class="chat-content">{{ msg.content }}</div>
        </div>
      </div>
      <div class="chat-input-row">
        <div class="chat-editor-wrap">
          <el-input
            v-model="chatDraft"
            type="textarea"
            :rows="2"
            placeholder="输入团队消息，输入 @ 自动匹配团队成员"
            maxlength="1000"
            show-word-limit
            @input="handleChatDraftInput"
          />
          <div v-if="showMentionDropdown" class="mention-dropdown">
            <button v-for="member in mentionSuggestions" :key="`mention-${member.userId}`" type="button" class="mention-item" @click="selectMention(member)">
              <img :src="member.hiddenAvatar ? hiddenAvatar : (member.avatar || defaultAvatar)" class="mention-avatar" alt="avatar">
              <span>{{ member.name }}</span>
            </button>
          </div>
        </div>
        <el-button type="primary" :loading="chatSending" @click="sendChatMessage">发送</el-button>
      </div>
    </div>

    <el-dialog v-model="showAddMilestone" title="✨ 规划新里程碑" width="400px" custom-class="tech-dialog">
      <el-form :model="msForm" label-position="top">
        <el-form-item label="节点名称" required>
          <el-input v-model="msForm.title" placeholder="例如：原型机交付" />
        </el-form-item>
        <el-form-item label="截止日期时间" required>
          <el-date-picker v-model="msForm.date" type="datetime" placeholder="选择日期时间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMilestone = false">取消</el-button>
        <el-button type="primary" :loading="msLoading" @click="submitMilestone">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 新增：组队弹窗 -->
    <input
      ref="fileInputRef"
      type="file"
      style="display: none"
      accept=".pdf,.doc,.docx"
      @change="handleFileChange"
    >

    <el-dialog
        v-model="showBuildTeamDialog"
        :title="buildTeamDialogTitle"
        width="72vw"
        :style="{ maxWidth: '1080px' }"
        align-center
        custom-class="tech-dialog build-team-dialog"
    >
      <el-form label-position="top" :model="teamForm" class="build-team-form">
        <el-form-item :label="managerAssignLabel" required>
          <el-select
              v-model="teamForm.managerUserId"
              placeholder="从商务或数据工程师中选择"
              style="width: 100%"
              popper-class="project-manager-select-popper"
          >
            <el-option v-for="user in managerCandidates" :key="user.id" :label="user.label || user.name" :value="user.id">
              <div class="option-row manager-option-row">
                <img :src="user.avatar" class="avatar-small">
                <span class="option-name">{{ user.name }}</span>
                <span class="option-role">{{ user.role }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="Manager 权责比" required>
          <el-input-number v-model="teamForm.managerWeight" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="添加团队成员（开发/算法/数据）" required>
          <el-select
              v-model="teamForm.teamMembers"
              multiple
              placeholder="默认包含当前数据工程师，可继续选择开发、算法或其他数据成员"
              style="width: 100%"
              popper-class="project-team-select-popper"
          >
            <el-option v-for="user in memberCandidates" :key="user.id" :label="`${user.name} / ${formatRole(user.role)}`" :value="user.id">
                <div class="option-row member-option-row">
                 <img :src="user.avatar" class="avatar-small">
                <span class="option-name">{{ user.name }}</span>
                <span class="option-role">{{ formatRole(user.role) }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <div v-if="selectedMemberDetails.length" class="ratio-editor-list">
          <div v-for="member in selectedMemberDetails" :key="member.id" class="ratio-editor-item">
            <span>{{ member.name }} · {{ formatRole(member.role) }}<span v-if="String(member.id) === String(teamForm.managerUserId || '') && selectedManagerIsDataEngineer">（兼任 Manager）</span></span>
            <el-input-number v-model="teamMemberWeights[member.id]" :min="0" :max="100" />
          </div>
        </div>
        <div class="ratio-total-row" :class="{ invalid: teamResponsibilityTotal !== 100 }">
          权责比总和：{{ teamResponsibilityTotal }} / 100
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showBuildTeamDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBuildTeam">确认组建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showExecutionPlanDialog" title="实施计划设定" width="560px" custom-class="tech-dialog">
      <el-form label-position="top" :model="executionPlanForm">
        <el-form-item label="目标描述" required>
          <el-input v-model="executionPlanForm.goalDescription" type="textarea" :rows="3" placeholder="请强制描述项目实施目标" />
        </el-form-item>
        <el-form-item label="项目评级" required>
          <el-select v-model="executionPlanForm.projectTier" placeholder="请选择项目评级">
            <el-option label="S" value="S" />
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
            <el-option label="N" value="N" />
          </el-select>
        </el-form-item>
        <el-form-item label="技术栈与深度描述" required>
          <el-input v-model="executionPlanForm.techStackDescription" type="textarea" :rows="3" placeholder="描述可能涉及的技术栈和深度" />
        </el-form-item>
        <el-form-item label="成员任务与时间规划（精确到分钟）" required>
          <div class="schedule-editor-list">
            <div v-for="schedule in executionPlanForm.memberSchedules" :key="`plan-${schedule.userId}`" class="schedule-editor-item">
              <div class="schedule-header">
                <strong>{{ schedule.name }}</strong>
              </div>
              <div class="schedule-editor-row">
                <el-input v-model="schedule.taskName" placeholder="任务名称，例如：接口联调与回归" />
                <el-input v-model="schedule.expectedOutput" placeholder="预期产出，例如：联调报告/发布包" />
              </div>
              <div class="schedule-editor-row">
                <el-date-picker v-model="schedule.expectedStartDate" type="datetime" format="YYYY-MM-DD HH:mm" value-format="x" placeholder="开始时间" style="width: 100%" />
                <el-date-picker v-model="schedule.expectedEndDate" type="datetime" format="YYYY-MM-DD HH:mm" value-format="x" placeholder="结束时间" style="width: 100%" />
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showExecutionPlanDialog = false">取消</el-button>
        <el-button type="primary" :loading="executionPlanSubmitting" @click="submitExecutionPlan">保存计划</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSubtaskDialog" :title="editingSubtaskId ? '编辑子任务' : '新增子任务'" width="520px" custom-class="tech-dialog">
      <el-form label-position="top" :model="subtaskForm">
        <el-form-item label="子任务标题" required>
          <el-input v-model="subtaskForm.title" />
        </el-form-item>
        <el-form-item label="子任务说明">
          <el-input v-model="subtaskForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="subtaskForm.assigneeUserId" clearable placeholder="选择负责人">
            <el-option v-for="member in project?.members || []" :key="member.userId" :label="member.name" :value="member.userId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubtaskDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSubtask">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPromotionSetupDialog" title="推广阶段配置" width="620px" custom-class="tech-dialog">
      <el-form label-position="top" :model="promotionSetupForm">
        <el-form-item label="推广执行人 (Promotion IC)" required>
          <el-select v-model="promotionSetupForm.promotionIcUserId" filterable placeholder="选择推广执行人" style="width: 100%">
            <el-option v-for="user in productMemberAddCandidates" :key="`promotion-ic-${user.id}`" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="推广参与成员 (至少2人，含主理人)" required>
          <el-select v-model="promotionSetupForm.promotionMemberIds" multiple filterable placeholder="选择推广成员" style="width: 100%">
            <el-option v-for="user in productMemberAddCandidates" :key="`promotion-${user.id}`" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Demo 工程师 (精确4人)" required>
          <el-select v-model="promotionSetupForm.demoEngineerIds" multiple filterable placeholder="选择Demo工程师" style="width: 100%">
            <el-option v-for="user in productMemberAddCandidates" :key="`demo-${user.id}`" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工程文件责任人" required>
          <el-select v-model="promotionSetupForm.demoEngineeringOwnerUserId" filterable placeholder="选择工程文件上传责任人" style="width: 100%">
            <el-option v-for="user in demoResponsibleCandidates" :key="`owner-engineering-${user.id}`" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Demo 演示责任人" required>
          <el-select v-model="promotionSetupForm.demoFileOwnerUserId" filterable placeholder="选择 Demo 演示上传责任人" style="width: 100%">
            <el-option v-for="user in demoResponsibleCandidates" :key="`owner-demo-${user.id}`" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述文档责任人" required>
          <el-select v-model="promotionSetupForm.demoDescriptionOwnerUserId" filterable placeholder="选择描述文档上传责任人" style="width: 100%">
            <el-option v-for="user in demoResponsibleCandidates" :key="`owner-description-${user.id}`" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可行性验证责任人" required>
          <el-select v-model="promotionSetupForm.demoFeasibilityOwnerUserId" filterable placeholder="选择可行性验证上传责任人" style="width: 100%">
            <el-option v-for="user in demoResponsibleCandidates" :key="`owner-feasibility-${user.id}`" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目评级" required>
          <el-select v-model="promotionSetupForm.projectTier" placeholder="推广阶段必须评级" style="width: 100%">
            <el-option label="S" value="S" />
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
            <el-option label="N" value="N" />
          </el-select>
        </el-form-item>
        <el-form-item label="行业分类" required>
          <el-select v-model="promotionSetupForm.projectType" placeholder="选择行业分类" style="width: 100%">
            <el-option v-for="item in productIndustryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPromotionSetupDialog = false">取消</el-button>
        <el-button type="primary" :loading="productActionLoading" @click="submitPromotionSetup">确认并进入 Demo 阶段</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDemoUploadDialog" title="Demo 文件上传矩阵" width="680px" custom-class="tech-dialog">
      <div class="execution-text">需由各自责任人上传 4 类核心文件后，系统自动推进到会议阶段。</div>
      <div class="demo-upload-grid">
        <div class="smart-block">
          <div class="execution-label">工程文件</div>
          <div class="execution-text">责任人：{{ getDemoOwnerName('ENGINEERING') }}</div>
          <input type="file" @change="onDemoFileChange('ENGINEERING', $event)" />
        </div>
        <div class="smart-block">
          <div class="execution-label">Demo 演示</div>
          <div class="execution-text">责任人：{{ getDemoOwnerName('DEMO_FILE') }}</div>
          <input type="file" @change="onDemoFileChange('DEMO_FILE', $event)" />
        </div>
        <div class="smart-block">
          <div class="execution-label">描述文档</div>
          <div class="execution-text">责任人：{{ getDemoOwnerName('DESCRIPTION') }}</div>
          <input type="file" @change="onDemoFileChange('DESCRIPTION', $event)" />
        </div>
        <div class="smart-block">
          <div class="execution-label">可行性验证</div>
          <div class="execution-text">责任人：{{ getDemoOwnerName('FEASIBILITY') }}</div>
          <input type="file" @change="onDemoFileChange('FEASIBILITY', $event)" />
        </div>
      </div>
      <template #footer>
        <el-button @click="showDemoUploadDialog = false">关闭</el-button>
        <el-button type="primary" :loading="productActionLoading" @click="submitDemoUploads">上传并校验阶段</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showResearchKeyDocUploadDialog" title="科研关键文档上传" width="680px" custom-class="tech-dialog">
      <div class="execution-text">根据当前科研阶段，需由对应责任人依次上传核心文档。系统将在文档齐全后自动推进阶段。</div>
      <div class="demo-upload-grid">
        <div v-for="item in currentStageRequiredResearchDocs" :key="item.category" class="smart-block">
          <div class="execution-label">{{ item.label }}</div>
          <div class="execution-text">
            责任人：{{ item.ownerName }}
            <span v-if="item.uploaded" style="color: green; margin-left: 8px;">✓ 已上传</span>
          </div>
          <input type="file" @change="onResearchDocChange(item.category, $event)" />
        </div>
      </div>
      <template #footer>
        <el-button @click="showResearchKeyDocUploadDialog = false">关闭</el-button>
        <el-button type="primary" :loading="productActionLoading" @click="submitResearchKeyDocUploads">上传并推进阶段</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showMeetingDecisionDialog" title="会议评审决策" width="620px" custom-class="tech-dialog">
      <el-form label-position="top" :model="meetingDecisionForm">
        <el-form-item label="参会成员（关联用户）" required>
          <el-select v-model="meetingDecisionForm.participantUserIds" multiple filterable placeholder="选择参会成员" style="width: 100%">
            <el-option v-for="member in project?.members || []" :key="`meeting-${member.userId}`" :label="member.name" :value="member.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="会议纪要文件" required>
          <input type="file" @change="onMeetingMinutesFileChange" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMeetingDecisionDialog = false">取消</el-button>
        <el-button type="danger" :loading="productActionLoading" @click="submitMeetingDecision('HOLD')">驳回/终止</el-button>
        <el-button type="primary" :loading="productActionLoading" @click="submitMeetingDecision('OK')">会议结论 OK</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTestingDecisionDialog" title="测试上线决策" width="620px" custom-class="tech-dialog">
      <el-form label-position="top" :model="testingDecisionForm">
        <el-form-item label="测试反馈" required>
          <el-input v-model="testingDecisionForm.testFeedback" type="textarea" :rows="4" placeholder="填写内测/公测反馈" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTestingDecisionDialog = false">取消</el-button>
        <el-button type="warning" :loading="productActionLoading" @click="submitTestingDecision(false)">搁置</el-button>
        <el-button type="success" :loading="productActionLoading" @click="submitTestingDecision(true)">通过并转正式项目</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showProductMemberDialog" title="产品成员管理" width="680px" custom-class="tech-dialog">
      <div class="execution-text">任意阶段可增减成员；发起者不可删除。</div>
      <div class="member-manage-section">
        <div class="execution-label">新增成员</div>
        <el-select v-model="productMemberForm.addUserIds" multiple filterable placeholder="选择要新增的成员" style="width: 100%">
          <el-option v-for="user in productMemberAddCandidates" :key="`add-${user.id}`" :label="user.label" :value="user.id" />
        </el-select>
        <div class="action-row">
          <el-button type="primary" :loading="productMemberLoading" @click="submitAddProductMembers">批量新增</el-button>
        </div>
      </div>
      <div class="member-manage-section">
        <div class="execution-label">当前成员</div>
        <div class="member-manage-list">
          <div v-for="member in project?.members || []" :key="`member-${member.userId}`" class="member-manage-item">
            <div class="option-row">
              <img :src="member.hiddenAvatar ? hiddenAvatar : (member.avatar || defaultAvatar)" class="avatar-small" alt="avatar">
                <div>
                  <div class="option-name">{{ member.name }}</div>
                  <div class="option-role">{{ formatRole(member.role) }}<span v-if="String(member.userId) === ideaOwnerId"> · 发起者</span></div>
                </div>
              </div>
            <el-button v-if="canRemoveProductMember(member)" size="small" type="danger" plain :loading="productMemberLoading" @click="removeProductMember(member)">移除</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showProductMemberDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showProjectMemberDialog" title="实施成员管理" width="680px" custom-class="tech-dialog">
      <div class="execution-text">确立 Manager 后，实施阶段可自由增减成员；新增成员会自动进入实施管理与成员任务看板。</div>
      <div class="member-manage-section">
        <div class="execution-label">新增成员</div>
        <el-select v-model="projectMemberForm.addUserIds" multiple filterable placeholder="选择要新增的成员" style="width: 100%">
          <el-option v-for="user in projectMemberAddCandidates" :key="`add-project-${user.id}`" :label="user.label" :value="user.id" />
        </el-select>
      </div>
      <div class="member-manage-section">
        <div class="execution-label">权责比分配</div>
        <div class="execution-text">实施阶段新增成员后，可在此同步调整管理/执行权责比，总和必须为 100。</div>
        <div class="ratio-editor-list member-ratio-editor-list">
          <div class="ratio-editor-item">
            <span>Manager 管理权责比</span>
            <el-input-number v-model="projectMemberForm.managerWeight" :min="0" :max="100" />
          </div>
          <div v-if="projectManagerSupportsExecutionRatio" class="ratio-editor-item">
            <span>Manager 执行权责比</span>
            <el-input-number v-model="projectMemberForm.managerExecutionWeight" :min="0" :max="100" />
          </div>
          <div v-for="member in projectResponsibilityMembers" :key="`project-ratio-${member.id}`" class="ratio-editor-item">
            <span>{{ member.name }} · {{ formatRole(member.role) }}</span>
            <el-input-number v-model="projectMemberWeights[member.id]" :min="0" :max="100" />
          </div>
        </div>
        <div class="ratio-total-row" :class="{ invalid: projectResponsibilityTotal !== 100 }">
          权责比总和：{{ projectResponsibilityTotal }} / 100
        </div>
      </div>
      <div class="member-manage-section">
        <div class="execution-label">当前成员</div>
        <div class="member-manage-list">
          <div v-for="member in project?.members || []" :key="`project-member-${member.userId}`" class="member-manage-item">
            <div class="option-row">
              <img :src="member.hiddenAvatar ? hiddenAvatar : (member.avatar || defaultAvatar)" class="avatar-small" alt="avatar">
                <div>
                  <div class="option-name">{{ member.name }}</div>
                  <div class="option-role">{{ formatRole(member.role) }}<span v-if="String(member.userId) === String(project?.managerId || '')"> · 当前经理</span></div>
                  <div v-if="projectMemberRatioText(member)" class="option-role">{{ projectMemberRatioText(member) }}</div>
                </div>
              </div>
            <el-button v-if="canRemoveProjectMember(member)" size="small" type="danger" plain :loading="projectMemberLoading" @click="removeProjectMember(member)">移除</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showProjectMemberDialog = false">关闭</el-button>
        <el-button type="primary" :loading="projectMemberLoading" @click="submitProjectMemberChanges">保存成员与权责比</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showResearchMemberDialog" title="科研成员管理" width="680px" custom-class="tech-dialog">
      <div class="execution-text">任意阶段可增减成员；发起者不可删除。</div>
      <div class="member-manage-section">
        <div class="execution-label">新增成员</div>
        <el-select v-model="researchMemberForm.addUserIds" multiple filterable placeholder="选择要新增的成员" style="width: 100%">
          <el-option v-for="user in researchMemberAddCandidates" :key="`add-research-${user.id}`" :label="user.label" :value="user.id" />
        </el-select>
        <div class="action-row">
          <el-button type="primary" :loading="researchMemberLoading" @click="submitAddResearchMembers">批量新增</el-button>
        </div>
      </div>
      <div class="member-manage-section">
        <div class="execution-label">当前成员</div>
        <div class="member-manage-list">
          <div v-for="member in project?.members || []" :key="`research-member-${member.userId}`" class="member-manage-item">
            <div class="option-row">
              <img :src="member.hiddenAvatar ? hiddenAvatar : (member.avatar || defaultAvatar)" class="avatar-small" alt="avatar">
                <div>
                  <div class="option-name">{{ member.name }}</div>
                  <div class="option-role">{{ formatRole(member.role) }}<span v-if="String(member.userId) === researchIdeaOwnerId"> · 发起者</span></div>
                </div>
              </div>
            <el-button v-if="canRemoveResearchMember(member)" size="small" type="danger" plain :loading="researchMemberLoading" @click="removeResearchMember(member)">移除</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showResearchMemberDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showArchiveMoveDialog" title="移动管理归档文件" width="480px" custom-class="tech-dialog">
      <div class="execution-text">选择文件夹 A 中的目标目录，留空表示移动到根目录。</div>
      <el-form label-position="top" style="margin-top: 14px;">
        <el-form-item label="目标目录">
          <el-select v-model="archiveMoveTargetPath" filterable clearable placeholder="选择目标目录" style="width: 100%">
            <el-option label="根目录" value="" />
            <el-option v-for="folder in managerArchiveFolders" :key="`move-folder-${folder.folderPath}`" :label="folder.folderPath" :value="folder.folderPath" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showArchiveMoveDialog = false">取消</el-button>
        <el-button type="primary" :loading="archiveMoveLoading" @click="submitArchiveMove">确认移动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTravelReimbursementDialog" title="提交出差报销" width="760px" custom-class="tech-dialog">
      <ExpenseSubmissionForm
        compact
        submission-type="PROJECT_TRAVEL_REIMBURSEMENT"
        :project-context="{ projectId: project?.id, projectName: project?.name, flowType: project?.flowType }"
        @submitted="handleTravelReimbursementSubmitted"
      />
    </el-dialog>

  </div>

  <div v-else class="error-state">未找到该项目信息，请重试。</div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/userStore'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import ExpenseSubmissionForm from '@/components/finance/ExpenseSubmissionForm.vue'
import { getErpLandingRoute } from '@/router/domainAccess'
import {
  getProjectWorkflowMemberRoles,
  getProductWorkflowMemberRoles,
  getResearchWorkflowMemberRoles
} from '@/api/workflowMemberRoles'

const route = useRoute()
const userStore = useUserStore()

// 1. Props 定义
const props = defineProps({
  projectId: {
    type: String,
    default: ''
  }
})

// 2. 核心状态定义
const project = ref(null)
const loading = ref(true)
const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix'
const hiddenAvatar = 'https://api.dicebear.com/7.x/shapes/svg?seed=masked'
const workflowMemberRoleRows = ref([])

// 里程碑相关状态
const showAddMilestone = ref(false)
const showBuildTeamDialog = ref(false) // 新增：控制组队弹窗的状态
const showExecutionPlanDialog = ref(false)
const showSubtaskDialog = ref(false)
const msLoading = ref(false)
const msForm = ref({
  title: '',
  date: ''
})

// 文件上传相关状态
const fileInputRef = ref(null) // 对应模板里的 ref="fileInputRef"
const executionFileInputRef = ref(null)
const executionFolderInputRef = ref(null)
const isDragOver = ref(false)
const uploading = ref(false)
const executionUploading = ref(false)
const executionUploadFolderType = ref('B_ENGINEER_WORK')
const executionOverview = ref(null)
const executionPlanSubmitting = ref(false)
const chatMessages = ref([])
const chatParticipants = ref([])
const chatDraft = ref('')
const chatLoading = ref(false)
const chatSending = ref(false)
const mentionSuggestions = ref([])
const showMentionDropdown = ref(false)
const showPromotionSetupDialog = ref(false)
const showDemoUploadDialog = ref(false)
const showResearchKeyDocUploadDialog = ref(false)
const researchKeyDocUploadForm = ref({
  RESEARCH_BLUEPRINT_DOC: null,
  RESEARCH_ARCHITECTURE_DOC: null,
  RESEARCH_TASK_BREAKDOWN_DOC: null,
  RESEARCH_EVALUATION_REPORT: null
})
const showMeetingDecisionDialog = ref(false)
const showTestingDecisionDialog = ref(false)
const showProductMemberDialog = ref(false)
const showProjectMemberDialog = ref(false)
const showResearchMemberDialog = ref(false)
const researchMemberForm = ref({ addUserIds: [] })
const researchMemberLoading = ref(false)
const showArchiveMoveDialog = ref(false)
const showTravelReimbursementDialog = ref(false)
const productActionLoading = ref(false)
const productMemberLoading = ref(false)
const productTaskAssignmentLoading = ref(false)
const productTaskAssignmentSaving = ref(false)
const productTaskAssignments = ref([])
const projectTaskAssignmentLoading = ref(false)
const projectTaskAssignmentSaving = ref(false)
const projectTaskAssignments = ref([])
const projectEarnings = ref(null)
const projectEarningsLoading = ref(false)
const managerArchiveActivePath = ref('')
const archiveMoveLoading = ref(false)
const archiveMoveTargetPath = ref('')
const archiveMoveFile = ref(null)
const implementationStatusDraft = ref('')
const implementationStatusSaving = ref(false)
const projectDynamicInfoSaving = ref(false)
const dynamicInfoForm = ref({
  goalDescription: '',
  projectTier: '',
  techStackDescription: '',
  implementationStatus: '',
  estimatedRevenue: ''
})
const projectMemberLoading = ref(false)
const gitRepoLoading = ref(false)
const gitRepoSubmitting = ref(false)
const gitRepoLogsLoading = ref(false)
const gitRepositories = ref([])
const gitRepoLogs = ref([])
const selectedGitRepoId = ref(null)
const gitRepoForm = ref({
  repositoryUrl: '',
  accessToken: '',
  branch: 'main',
  provider: 'GITHUB'
})
const productMemberForm = ref({
  addUserIds: []
})
const projectMemberForm = ref({
  addUserIds: [],
  managerWeight: 0,
  managerExecutionWeight: 0
})
const projectMemberWeights = ref({})
const promotionSetupForm = ref({
  promotionIcUserId: '',
  promotionMemberIds: [],
  demoEngineerIds: [],
  demoEngineeringOwnerUserId: '',
  demoFileOwnerUserId: '',
  demoDescriptionOwnerUserId: '',
  demoFeasibilityOwnerUserId: '',
  projectTier: '',
  projectType: ''
})
const productIndustryOptions = [
  { label: '业务', value: 'BUSINESS' },
  { label: '军工', value: 'MILITARY' },
  { label: 'AI FOR SCIENCE', value: 'AI_FOR_SCIENCE' },
  { label: '医药', value: 'MEDICAL' },
  { label: '工业', value: 'INDUSTRIAL' },
  { label: '群体智能', value: 'SWARM_INTEL' }
]
const demoUploadForm = ref({
  ENGINEERING: null,
  DEMO_FILE: null,
  DESCRIPTION: null,
  FEASIBILITY: null
})
const meetingDecisionForm = ref({
  participantUserIds: [],
  meetingMinutesFile: null
})
const testingDecisionForm = ref({
  testFeedback: ''
})
let chatPollingTimer = null
let gitRepoPollingTimer = null
const pendingAssetCategory = ref('')

// 组队弹窗相关状态
const allUsers = ref([])
const teamMemberWeights = ref({})
const teamForm = ref({
  managerUserId: '',
  managerWeight: 0,
  teamMembers: []
})
const executionPlanForm = ref({
  goalDescription: '',
  difficultyLevel: '',
  projectTier: '',
  techStackDescription: '',
  memberSchedules: []
})
const subtaskForm = ref({
  title: '',
  description: '',
  assigneeUserId: ''
})
const editingSubtaskId = ref(null)

const normalizeRole = (role) => String(role || '').toUpperCase()
const isProjectFlow = computed(() => String(project.value?.flowType || '').toUpperCase() === 'PROJECT')
const isProductFlow = computed(() => String(project.value?.flowType || '').toUpperCase() === 'PRODUCT')
const isResearchFlow = computed(() => String(project.value?.flowType || '').toUpperCase() === 'RESEARCH')
const earningsPoolTone = computed(() => {
  const poolType = String(projectEarnings.value?.poolType || '').toUpperCase()
  if (poolType === 'BUSINESS') return 'business'
  if (poolType === 'EXECUTION') return 'execution'
  return 'none'
})
const emptyDescriptionText = computed(() => isProductFlow.value ? '暂无产品描述' : (isResearchFlow.value ? '暂无科研描述' : '暂无项目描述'))
const displayDescription = computed(() => {
  const raw = String(project.value?.description || '').trim()
  if (!raw) return emptyDescriptionText.value
  if (!isProjectFlow.value) return raw
  const visible = raw
    .split('|')
    .map(segment => String(segment || '').trim())
    .filter(Boolean)
    .filter(segment => !/^【(评级|可行性报告|可行性报告URL|实施状态)】:/.test(segment))
    .join(' | ')
    .trim()
  return visible || emptyDescriptionText.value
})
const readTaggedDescriptionValue = (description, tag) => {
  const source = String(description || '')
  const marker = `【${tag}】:`
  const start = source.indexOf(marker)
  if (start < 0) return ''
  const contentStart = start + marker.length
  const end = source.indexOf('|', contentStart)
  const value = end < 0 ? source.slice(contentStart) : source.slice(contentStart, end)
  return String(value || '').trim()
}
const legacyProjectTier = computed(() => {
  const value = readTaggedDescriptionValue(project.value?.description, '评级')
  const normalized = String(value || '').trim().toUpperCase()
  if (!normalized || normalized === '未定级') return ''
  if (normalized === 'S级'.toUpperCase()) return 'S'
  if (normalized === 'A级'.toUpperCase()) return 'A'
  if (normalized === 'B级'.toUpperCase()) return 'B'
  if (normalized === 'C级'.toUpperCase()) return 'C'
  if (normalized === 'N级'.toUpperCase()) return 'N'
  return normalized
})
const syncedProjectTierValue = computed(() => {
  const projectTier = String(project.value?.projectTier || '').trim().toUpperCase()
  const executionPlanTier = String(executionOverview.value?.plan?.projectTier || '').trim().toUpperCase()
  return executionPlanTier || projectTier || legacyProjectTier.value || ''
})
const syncedProjectTier = computed(() => syncedProjectTierValue.value || '未设置')
const dynamicGoalDescription = computed(() => executionOverview.value?.plan?.goalDescription || '未设置')
const dynamicTechStackDescription = computed(() => executionOverview.value?.plan?.techStackDescription || '未设置')
const implementationStatusText = computed(() => readTaggedDescriptionValue(project.value?.description, '实施状态') || '未设置')
const projectTierOptions = [
  { label: 'S', value: 'S' },
  { label: 'A', value: 'A' },
  { label: 'B', value: 'B' },
  { label: 'C', value: 'C' },
  { label: 'N', value: 'N' }
]
const squadTitle = computed(() => isProductFlow.value ? '产品成员' : (isResearchFlow.value ? '科研成员' : '项目成员及权责比'))
const isLeadMember = member => {
  const memberId = String(member?.userId || '')
  if (!memberId) return false
  return memberId === String(project.value?.managerId || '')
    || memberId === String(project.value?.hostUserId || '')
    || memberId === String(project.value?.ideaOwnerUserId || '')
}
const sortedSquadMembers = computed(() => {
  const members = [...(project.value?.members || [])]
  const managerId = String(project.value?.managerId || '')
  const hostId = String(project.value?.hostUserId || '')
  const ownerId = String(project.value?.ideaOwnerUserId || '')

  const memberRank = member => {
    const memberId = String(member?.userId || '')
    if (memberId && memberId === managerId) return 0
    if (memberId && memberId === hostId) return 1
    if (memberId && memberId === ownerId) return 2
    return 9
  }

  return members.sort((a, b) => {
    const rankDiff = memberRank(a) - memberRank(b)
    if (rankDiff !== 0) return rankDiff
    return String(a?.name || '').localeCompare(String(b?.name || ''))
  })
})
const buildTeamDialogTitle = computed(() => isProductFlow.value ? '🚀 组建产品团队' : '🚀 组建项目团队')
const managerAssignLabel = computed(() => isProductFlow.value ? '指派产品经理' : '指派项目经理')
const productFlowSteps = [
  { status: 'IDEA', label: '创意孵化' },
  { status: 'PROMOTION', label: '推广阶段' },
  { status: 'DEMO_EXECUTION', label: 'Demo 实施' },
  { status: 'MEETING_DECISION', label: '虚拟会议' },
  { status: 'TESTING', label: '测试上线' },
  { status: 'LAUNCHED', label: '立项实施' },
  { status: 'SHELVED', label: '已搁置' }
]
const selectedProductStatus = ref('')
const activeProductStatus = computed(() => String(project.value?.productStatus || 'IDEA').toUpperCase())
const activeProductStepIndex = computed(() => Math.max(productFlowSteps.findIndex(step => step.status === activeProductStatus.value), 0))
const productProgress = computed(() => Math.round(((activeProductStepIndex.value + 1) / productFlowSteps.length) * 100))
const currentProductStepLabel = computed(() => {
  const step = productFlowSteps.find(item => item.status === selectedProductStatus.value)
  return step ? step.label : '阶段'
})
const ideaFields = computed(() => ({
  targetUsers: project.value?.targetUsers || '未填写',
  coreFeatures: project.value?.coreFeatures || '未填写',
  useCase: project.value?.useCase || '未填写',
  problemStatement: project.value?.problemStatement || '未填写',
  techStackDesc: project.value?.techStackDesc || '未填写'
}))
const demoEngineerCount = computed(() => (project.value?.members || []).filter(member => {
  const role = String(member.role || '').toUpperCase()
  return role === 'DEV' || role === 'ALGORITHM' || role === 'RESEARCH'
}).length)
const demoRequiredCategories = ['ENGINEERING', 'DEMO_FILE', 'DESCRIPTION', 'FEASIBILITY']
const demoOwnerFieldMap = {
  ENGINEERING: 'demoEngineeringOwnerUserId',
  DEMO_FILE: 'demoFileOwnerUserId',
  DESCRIPTION: 'demoDescriptionOwnerUserId',
  FEASIBILITY: 'demoFeasibilityOwnerUserId'
}
const demoTypeLabelMap = {
  ENGINEERING: '工程文件',
  DEMO_FILE: 'Demo 演示',
  DESCRIPTION: '描述文档',
  FEASIBILITY: '可行性验证'
}
const demoUploadedCount = computed(() => {
  const categories = new Set((project.value?.uploads || []).map(file => String(file.category || file.type || '').toUpperCase()))
  return demoRequiredCategories.filter(category => categories.has(category)).length
})
const demoResponsibleCandidates = computed(() => {
  const ids = new Set((promotionSetupForm.value.demoEngineerIds || []).map(id => String(id)))
  const base = workflowMemberRoleRows.value.length > 0 ? workflowMemberRoleRows.value : allUsers.value
  const filtered = ids.size > 0
    ? base.filter(user => ids.has(String(user.userId || user.id || '')))
    : base
  return [...filtered]
    .map(user => ({
      ...user,
      label: `${user.name || user.username || user.id} / ${formatRole(user.role || 'MEMBER')}`
    }))
    .sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'zh-Hans-CN'))
})
const getDemoOwnerName = category => {
  const field = demoOwnerFieldMap[category]
  const userId = String(project.value?.[field] || '').trim()
  if (!userId) return '未指定'
  const matched = allUsers.value.find(user => String(user.id) === userId) || (project.value?.members || []).find(user => String(user.userId) === userId)
  return matched?.name || matched?.username || userId
}
const demoResponsibilitySummary = computed(() => demoRequiredCategories.map(category => ({
  category,
  label: demoTypeLabelMap[category],
  ownerName: getDemoOwnerName(category),
  uploaded: (project.value?.uploads || []).some(file => String(file.category || file.type || '').toUpperCase() === category)
})))
const meetingParticipantSummary = computed(() => {
  const ids = String(project.value?.meetingParticipantUserIds || '')
  if (!ids) return '未登记'
  const userMap = new Map((project.value?.members || []).map(member => [String(member.userId), member.name]))
  return ids.split(',').map(id => userMap.get(String(id).trim()) || id.trim()).filter(Boolean).join('、') || '未登记'
})
const researchKeyFileConfig = [
  { category: 'RESEARCH_BLUEPRINT_DOC', label: '蓝图文档', ownerField: 'blueprintOwnerUserId' },
  { category: 'RESEARCH_ARCHITECTURE_DOC', label: '架构文档', ownerField: 'architectureOwnerUserId' },
  { category: 'RESEARCH_TASK_BREAKDOWN_DOC', label: '任务分解文档', ownerField: 'taskBreakdownOwnerUserId' },
  { category: 'RESEARCH_EVALUATION_REPORT', label: '评测报告', ownerField: 'evaluationReportOwnerUserId' }
]
const researchKeyFileSummary = computed(() => {
  if (!isResearchFlow.value) return []
  return researchKeyFileConfig.map(item => {
    const ownerId = String(project.value?.[item.ownerField] || '').trim()
    const matched = allUsers.value.find(user => String(user.id) === ownerId) || (project.value?.members || []).find(user => String(user.userId) === ownerId)
    const uploaded = (project.value?.uploads || []).some(file => String(file.category || file.type || '').toUpperCase() === item.category)
    return {
      ...item,
      ownerId,
      ownerName: matched?.name || matched?.username || (ownerId || '未指定'),
      uploaded,
      canUpload: ownerId && String(ownerId).padStart(6, '0') === String(activeUserId.value).padStart(6, '0')
    }
  })
})
const currentStageRequiredResearchDocs = computed(() => {
  if (!isResearchFlow.value) return []
  const status = String(selectedResearchStatus.value || activeResearchStatus.value || 'INIT').toUpperCase()
  const stageMap = {
    'INIT': ['RESEARCH_BLUEPRINT_DOC'],
    'BLUEPRINT': ['RESEARCH_ARCHITECTURE_DOC'],
    'EXPANSION': ['RESEARCH_TASK_BREAKDOWN_DOC'],
    'DESIGN': ['RESEARCH_EVALUATION_REPORT']
  }
  const allowedCategories = stageMap[status] || []
  return researchKeyFileSummary.value.filter(item => allowedCategories.includes(item.category))
})
const currentStepDescription = computed(() => {
  const map = {
    PROMOTION: '需完成推广成员配置与 Promotion IC 指派。',
    MEETING_DECISION: '需上传会议纪要并做出 OK/驳回决策。',
    TESTING: '内测人数达标后可执行通过/搁置决策。',
    LAUNCHED: '已转入正式项目主干实施。',
    SHELVED: '当前产品已搁置。'
  }
  return map[selectedProductStatus.value] || '请按状态机推进当前阶段动作。'
})
const nextStageButtonText = computed(() => {
  if (selectedProductStatus.value === 'IDEA') return '配置推广与 Demo 组队'
  if (selectedProductStatus.value === 'DEMO_EXECUTION') return '上传 Demo 四槽文件'
  if (selectedProductStatus.value === 'MEETING_DECISION') return '提交会议决策'
  if (selectedProductStatus.value === 'TESTING') return '提交测试结论'
  if (selectedProductStatus.value === 'PROMOTION') return '继续推进到 Demo'
  return '当前阶段无下一步'
})
const currentActionHint = computed(() => {
  if (selectedProductStatus.value === 'IDEA') return '先完成推广成员和 4 名 Demo 工程师配置。'
  if (selectedProductStatus.value === 'DEMO_EXECUTION') return '请由对应责任人完成 4 个核心文件槽位上传，再提交评审。'
  if (selectedProductStatus.value === 'PROMOTION') return '推广阶段必须 >=2 人，且指定 Promotion IC。'
  if (selectedProductStatus.value === 'TESTING') return '测试阶段通过需满足内测最小人数。'
  if (selectedProductStatus.value === 'MEETING_DECISION') return '上传会议纪要，选择参会成员并执行 OK/驳回。'
  return '状态决定视图，进度即是交互。'
})
const nextStageDisabledReason = computed(() => {
  if (!isProductFlow.value) return '当前阶段不可推进'
  if (!isManager.value) return '仅当前阶段负责人可推进'
  if (selectedProductStatus.value === 'PROMOTION' && (project.value?.members || []).length < 2) return '需至少 2 名成员参与推广后解锁'
  if (selectedProductStatus.value === 'PROMOTION' && demoResponsibilitySummary.value.some(item => item.ownerName === '未指定')) return '需先为四类 Demo 文件指定责任人后进入 Demo 阶段'
  if (selectedProductStatus.value === 'DEMO_EXECUTION' && demoUploadedCount.value < 4) return '需上传工程文件、Demo演示、描述文档、可行性验证后解锁'
  if (selectedProductStatus.value === 'LAUNCHED' || selectedProductStatus.value === 'SHELVED') return '已终态，无需继续推进'
  return ''
})
const canMoveToNextProductStage = computed(() => isManager.value && !nextStageDisabledReason.value)
const showAssignEngineerAction = computed(() => selectedProductStatus.value === 'IDEA' && isManager.value)
const showPromoteAction = computed(() => selectedProductStatus.value === 'TESTING' && (project.value?.members || []).some(m => String(m.role || '').toUpperCase() === 'PROMOTION_IC' && String(m.userId) === activeUserId.value))
const currentChatStageTag = computed(() => {
  if (isProductFlow.value) {
    return String(selectedProductStatus.value || activeProductStatus.value || 'IDEA').toUpperCase()
  }
  if (isResearchFlow.value) {
    return String(selectedResearchStatus.value || activeResearchStatus.value || 'INIT').toUpperCase()
  }
  return String(selectedProjectStatus.value || activeProjectStatus.value || 'IMPLEMENTING').toUpperCase()
})
const projectFlowSteps = [
  { status: 'INITIATED', label: '发起' },
  { status: 'IMPLEMENTING', label: '实施' },
  { status: 'SETTLEMENT', label: '结算' },
  { status: 'COMPLETED', label: '归档' }
]
const selectedProjectStatus = ref('INITIATED')
const activeProjectStatus = computed(() => String(project.value?.projectStatus || 'INITIATED').toUpperCase())
const activeProjectStepIndex = computed(() => Math.max(projectFlowSteps.findIndex(step => step.status === activeProjectStatus.value), 0))
const projectProgress = computed(() => Math.round(((activeProjectStepIndex.value + 1) / projectFlowSteps.length) * 100))
const currentProjectStepLabel = computed(() => {
  const step = projectFlowSteps.find(item => item.status === selectedProjectStatus.value)
  return step ? step.label : '阶段'
})
const currentProjectStepDescription = computed(() => {
  const map = {
    INITIATED: '发起阶段：数据工程师完成可行性报告上传并选定项目经理后进入实施。',
    IMPLEMENTING: '实施阶段：Manager 需规划开发、算法与数据任务，并分配对应工程师执行。',
    SETTLEMENT: '结算阶段：确认全部子任务完成并收口交付。',
    COMPLETED: '归档阶段：项目完成并沉淀资料。'
  }
  return map[selectedProjectStatus.value] || '请按项目交付状态机推进。'
})
const nextProjectStageMap = {
  INITIATED: 'IMPLEMENTING',
  IMPLEMENTING: 'SETTLEMENT',
  SETTLEMENT: 'COMPLETED'
}
const nextProjectStage = computed(() => nextProjectStageMap[activeProjectStatus.value] || '')
const nextProjectStageButtonText = computed(() => nextProjectStage.value ? `推进到 ${getStatusOptions('PROJECT')[nextProjectStage.value]}` : '当前阶段无下一步')
const isProjectStatusOptionDisabled = status => {
  const activeStatus = activeProjectStatus.value
  if (status === activeStatus) return true
  return nextProjectStageMap[activeStatus] !== status
}
const selectedDataEngineerMemberId = computed(() => String(defaultProjectDataEngineer.value?.id || ''))
const canEditImplementationStatus = computed(() => {
  if (isProductFlow.value || isResearchFlow.value) return false
  if (!activeUserId.value) return false
  const stage = String(project.value?.projectStatus || '').toUpperCase()
  if (!['INITIATED', 'IMPLEMENTING'].includes(stage)) return false
  return activeUserId.value === selectedDataEngineerMemberId.value
})
const canEditProjectDynamicInfo = computed(() => {
  if (isProductFlow.value || isResearchFlow.value) return false
  if (!activeUserId.value) return false
  const stage = String(project.value?.projectStatus || '').toUpperCase()
  if (stage === 'COMPLETED') return false
  return activeUserRole.value === 'ADMIN' || canEditImplementationStatus.value || isManager.value
})
const hasFeasibilityReportUploaded = computed(() => {
  const hasUrl = Boolean(String(project.value?.feasibilityReportUrl || '').trim())
  const hasAsset = (project.value?.uploads || []).some(file => {
    const category = String(file.category || file.type || '').toUpperCase()
    return category === 'FEASIBILITY_REPORT' || category === 'FEASIBILITY_REPORT_URL'
  })
  return hasUrl || hasAsset
})
const feasibilityReportStatusText = computed(() => hasFeasibilityReportUploaded.value ? '已上传' : '未上传')
const hasAssignedProjectManager = computed(() => {
  const managerId = String(project.value?.managerId || '').trim()
  if (!managerId) return false
  return (project.value?.members || []).some(item => String(item.userId || item.id || '') === managerId)
})
const hasResponsibilityRatiosAllocated = computed(() => {
  const members = project.value?.members || []
  if (!members.length) return false
  const total = members.reduce((sum, member) => {
    const execution = Number(member.executionResponsibilityRatio || member.weight || 0)
    const manager = Number(member.managerResponsibilityRatio || member.managerWeight || 0)
    return sum + execution + manager
  }, 0)
  return total === 100
})
const hasImplementationStatusUpdated = computed(() => {
  const status = implementationStatusText.value
  return Boolean(status && status !== '未设置')
})
const currentProjectActionHint = computed(() => {
  if (selectedProjectStatus.value === 'INITIATED') {
    if (!hasFeasibilityReportUploaded.value) return '请由被选中的数据工程师上传可行性报告。'
    if (!hasAssignedProjectManager.value) return '还需指定项目经理才可进入实施阶段。'
    if (!hasResponsibilityRatiosAllocated.value) return '还需完成权责比分配才可进入实施阶段。'
    if (!hasImplementationStatusUpdated.value) return '还需由数据工程师更新实施状态才可进入实施阶段。'
    return '可行性报告已上传、项目经理已指定且权责比已分配，可进入实施阶段。'
  }
  if (selectedProjectStatus.value === 'IMPLEMENTING') return '请由 Manager 规划开发、算法与数据任务并选择对应工程师，任务落实后再推进结算。'
  if (selectedProjectStatus.value === 'SETTLEMENT') return '结算通过后可归档。'
  return '项目已归档。'
})
const nextProjectStageDisabledReason = computed(() => {
  if (!nextProjectStage.value) return '当前阶段不可推进'
  if (activeProjectStatus.value === 'INITIATED' && !hasFeasibilityReportUploaded.value) return '需先由数据工程师上传可行性报告'
  if (activeProjectStatus.value === 'INITIATED' && !hasAssignedProjectManager.value) return '还需指定项目经理才可进入实施阶段'
  if (activeProjectStatus.value === 'INITIATED' && !hasResponsibilityRatiosAllocated.value) return '还需完成权责比分配才可进入实施阶段'
  if (activeProjectStatus.value === 'INITIATED' && !hasImplementationStatusUpdated.value) return '还需由数据工程师更新实施状态才可进入实施阶段'
  if (!isManager.value) return '仅当前负责人可推进'
  return ''
})
const canMoveToNextProjectStage = computed(() => !isProductFlow.value && !isResearchFlow.value && !nextProjectStageDisabledReason.value)

const researchFlowSteps = [
  { status: 'INIT', label: '发起' },
  { status: 'BLUEPRINT', label: '小群蓝图' },
  { status: 'EXPANSION', label: '大群深化' },
  { status: 'DESIGN', label: '实施前设计' },
  { status: 'EXECUTION', label: '施工执行' },
  { status: 'EVALUATION', label: '评测' },
  { status: 'ARCHIVE', label: '入库完成' },
  { status: 'SHELVED', label: '已搁置' }
]
const selectedResearchStatus = ref('INIT')
const activeResearchStatus = computed(() => String(project.value?.researchStatus || 'INIT').toUpperCase())
const activeResearchStepIndex = computed(() => Math.max(researchFlowSteps.findIndex(step => step.status === activeResearchStatus.value), 0))
const researchProgress = computed(() => Math.round(((activeResearchStepIndex.value + 1) / researchFlowSteps.length) * 100))
const currentResearchStepLabel = computed(() => {
  const step = researchFlowSteps.find(item => item.status === selectedResearchStatus.value)
  return step ? step.label : '阶段'
})
const currentResearchStepDescription = computed(() => {
  const map = {
    INIT: '发起阶段：完善 idea、innovation、预算与核心成员。',
    BLUEPRINT: '小群蓝图：需完成蓝图与成员确认。',
    EXPANSION: '大群深化：主持人推进任务规划或投票推进。',
    DESIGN: '实施前设计：需定义架构与技术路线并指定总工。',
    EXECUTION: '施工执行：按并行缝合或迭代模式推进。',
    EVALUATION: '评测阶段：完成评审结果。',
    ARCHIVE: '入库完成：可执行中间件入库。',
    SHELVED: '已搁置：流程终止。'
  }
  return map[selectedResearchStatus.value] || '请按科研创新状态机推进。'
})
const nextResearchStageMap = {
  INIT: 'BLUEPRINT',
  BLUEPRINT: 'EXPANSION',
  EXPANSION: 'DESIGN',
  DESIGN: 'EXECUTION',
  EXECUTION: 'EVALUATION',
  EVALUATION: 'ARCHIVE'
}
const nextResearchStage = computed(() => nextResearchStageMap[selectedResearchStatus.value] || '')
const nextResearchStageButtonText = computed(() => nextResearchStage.value ? `推进到 ${getStatusOptions('RESEARCH')[nextResearchStage.value]}` : '当前阶段无下一步')
const currentResearchActionHint = computed(() => {
  if (selectedResearchStatus.value === 'INIT') return 'Research 发起后完善必填字段与成员。'
  if (selectedResearchStatus.value === 'EXPANSION') return 'Host 推进任务规划或投票通过后进入 DESIGN。'
  if (selectedResearchStatus.value === 'DESIGN') return 'Chief Engineer 确认设计后进入执行。'
  if (selectedResearchStatus.value === 'ARCHIVE') return '可将科研成果入库到中间件仓。'
  return '按科研创新状态机推进。'
})
const canResearchOperate = computed(() => {
  const uid = String(activeUserId.value || '')
  if (!uid) return false
  if (isManager.value) return true
  const hostId = String(project.value?.hostUserId || '')
  const chiefId = String(project.value?.chiefEngineerUserId || '')
  return uid === hostId || uid === chiefId
})
const nextResearchStageDisabledReason = computed(() => {
  if (!nextResearchStage.value) return '当前阶段不可推进'
  if (!canResearchOperate.value) return '仅发起人/主持人/总工程师可推进'
  if (selectedResearchStatus.value === 'SHELVED') return '已搁置，无需继续推进'
  return ''
})
const canMoveToNextResearchStage = computed(() => isResearchFlow.value && !nextResearchStageDisabledReason.value)
const hostName = computed(() => {
  const uid = String(project.value?.hostUserId || '')
  const member = (project.value?.members || []).find(item => String(item.userId || '') === uid)
  return member?.name || '未指定'
})
const chiefEngineerName = computed(() => {
  const uid = String(project.value?.chiefEngineerUserId || '')
  const member = (project.value?.members || []).find(item => String(item.userId || '') === uid)
  return member?.name || '未指定'
})

const ideaOwnerId = computed(() => String(project.value?.ideaOwnerUserId || project.value?.managerId || ''))
const canManageProductMembers = computed(() => isProductFlow.value && (isManager.value || ideaOwnerId.value === activeUserId.value))
const canManageProductTaskAssignments = computed(() => canManageProductMembers.value)
const canManageResearchMembers = computed(() => isResearchFlow.value && (isManager.value || String(project.value?.hostUserId || '') === activeUserId.value))
const researchIdeaOwnerId = computed(() => String(project.value?.hostUserId || project.value?.ideaOwnerUserId || ''))
const canManageProjectMembers = computed(() => !isProductFlow.value && !isResearchFlow.value && activeProjectStatus.value !== 'COMPLETED' && isManager.value)
const canManageExecutionFiles = computed(() => Boolean(executionOverview.value?.canManage))
const canUploadManagerExecutionFiles = computed(() => Boolean(executionOverview.value?.canUploadManagerFiles))
const canUploadEngineerExecutionFiles = computed(() => Boolean(executionOverview.value?.canUploadEngineerFiles))
const normalizeArchiveFolderPath = (value) => String(value || '')
  .trim()
  .replace(/\\+/g, '/')
  .replace(/\/+/g, '/')
  .replace(/^\/+|\/+$/g, '')
const managerArchiveFolders = computed(() => {
  return (executionOverview.value?.managerArchiveFolders || []).map(folder => ({
    ...folder,
    folderPath: normalizeArchiveFolderPath(folder.folderPath),
    parentPath: normalizeArchiveFolderPath(folder.parentPath),
    folderName: folder.folderName || '未命名文件夹'
  }))
})
const latestFeasibilityAssetEntry = computed(() => {
  const latestFeasibilityAsset = (project.value?.uploads || []).find(file => String(file.category || file.type || '').toUpperCase() === 'FEASIBILITY_REPORT')
  if (!latestFeasibilityAsset) return null
  return {
    renderKey: `latest-feasibility-${latestFeasibilityAsset.id || latestFeasibilityAsset.name}`,
    sourceType: 'asset',
    type: latestFeasibilityAsset.type,
    name: latestFeasibilityAsset.name,
    meta: `${latestFeasibilityAsset.user || '未知上传者'} · ${latestFeasibilityAsset.time || ''}`,
    context: '可行性报告',
    fileName: latestFeasibilityAsset.name,
    canDownload: true,
    raw: latestFeasibilityAsset
  }
})
const managerArchiveVisibleFiles = computed(() => {
  const currentPath = normalizeArchiveFolderPath(managerArchiveActivePath.value)
  const archiveFiles = (executionOverview.value?.managerArchiveFiles || []).filter(file => {
    return normalizeArchiveFolderPath(file.secondaryCategory) === currentPath
  })
  if (currentPath) return archiveFiles
  return [
    ...archiveFiles,
    ...(latestFeasibilityAssetEntry.value ? [{
      ...latestFeasibilityAssetEntry.value,
      id: latestFeasibilityAssetEntry.value.raw?.id ?? latestFeasibilityAssetEntry.value.renderKey,
      secondaryCategory: '',
      uploaderName: latestFeasibilityAssetEntry.value.raw?.user,
      uploadedAt: latestFeasibilityAssetEntry.value.raw?.time,
      canRecategorize: false,
      canDelete: false
    }] : [])
  ]
})
const managerArchiveChildFolders = computed(() => {
  const currentPath = normalizeArchiveFolderPath(managerArchiveActivePath.value)
  return managerArchiveFolders.value.filter(folder => normalizeArchiveFolderPath(folder.parentPath) === currentPath)
})
const managerArchiveActivePathLabel = computed(() => managerArchiveActivePath.value || '根目录')
const managerArchiveBreadcrumbs = computed(() => {
  const activePath = normalizeArchiveFolderPath(managerArchiveActivePath.value)
  if (!activePath) return []
  const segments = activePath.split('/')
  return segments.map((segment, index) => ({
    folderName: segment,
    folderPath: segments.slice(0, index + 1).join('/')
  }))
})
const productMemberAddCandidates = computed(() => {
  const existingIds = new Set((project.value?.members || []).map(member => String(member.userId || '')))
  return allUsers.value
    .filter(user => !existingIds.has(String(user.userId || user.id || '')))
    .map(user => ({
      ...user,
      label: `${user.name || user.username || user.id} / ${formatRole(user.role || 'MEMBER')}`
    }))
})
const researchMemberAddCandidates = computed(() => {
  const existingIds = new Set((project.value?.members || []).map(member => String(member.userId || '')))
  return allUsers.value
    .filter(user => !existingIds.has(String(user.userId || user.id || '')))
    .map(user => ({
      ...user,
      label: `${user.name || user.username || user.id} / ${formatRole(user.role || 'MEMBER')}`
    }))
})
const resolveProjectResponsibilityRole = member => {
  const projectRole = normalizeRoleAlias(member?.role)
  if (isTeamBuildSelectableRole(projectRole)) {
    return projectRole
  }
  const userId = String(member?.userId || member?.id || '')
  const matchedUser = allUsers.value.find(user => String(user.id || '') === userId)
  const fallbackRole = normalizeRoleAlias(matchedUser?.role)
  return isTeamBuildSelectableRole(fallbackRole) ? fallbackRole : projectRole
}
const projectManagerSupportsExecutionRatio = computed(() => {
  const managerId = String(project.value?.managerId || '')
  if (!managerId) return false
  const managerMember = (project.value?.members || []).find(member => String(member.userId || member.id || '') === managerId)
  return isDataRole(resolveProjectResponsibilityRole(managerMember))
})
const projectResponsibilityMembers = computed(() => {
  const managerId = String(project.value?.managerId || '')
  const merged = new Map()

  ;(project.value?.members || []).forEach(member => {
    const memberId = String(member.userId || member.id || '')
    const resolvedRole = resolveProjectResponsibilityRole(member)
    const hasExistingResponsibility = Number(member.executionResponsibilityRatio || member.weight || 0) > 0
    if (!memberId || memberId === managerId) return
    if (!isTeamBuildSelectableRole(resolvedRole) && !hasExistingResponsibility) return
    merged.set(memberId, {
      ...member,
      id: memberId,
      role: resolvedRole
    })
  })

  normalizeTeamMemberIds(projectMemberForm.value.addUserIds || []).forEach(memberId => {
    if (!memberId || memberId === managerId || merged.has(memberId)) return
    const matchedUser = allUsers.value.find(user => String(user.id || '') === memberId)
    const resolvedRole = resolveProjectResponsibilityRole(matchedUser)
    if (!matchedUser || !isTeamBuildSelectableRole(resolvedRole)) return
    merged.set(memberId, {
      ...matchedUser,
      id: memberId,
      userId: memberId,
      role: resolvedRole,
      name: matchedUser.name || matchedUser.username || memberId
    })
  })

  return Array.from(merged.values())
})
const projectResponsibilityTotal = computed(() => {
  const managerWeight = Number(projectMemberForm.value.managerWeight || 0)
  const managerExecutionWeight = projectManagerSupportsExecutionRatio.value
    ? Number(projectMemberForm.value.managerExecutionWeight || 0)
    : 0
  const memberWeights = projectResponsibilityMembers.value.reduce((sum, member) => {
    return sum + Number(projectMemberWeights.value[member.id] || 0)
  }, 0)
  return managerWeight + managerExecutionWeight + memberWeights
})
const folderBVisibleFiles = computed(() => {
  return (executionOverview.value?.engineerWorkspaceFiles || []).map(file => ({
    ...file,
    sourceType: 'execution',
    renderKey: `exec-${file.id}`,
    fileName: file.fileName,
    metaText: `${file.secondaryCategory ? `目录：${file.secondaryCategory} · ` : ''}${file.uploaderName || '未知上传者'} · ${file.uploadedAt || ''}`,
    canDownload: Boolean(file.canDownload),
    canDelete: Boolean(file.canDelete)
  }))
})
const latestResultEntries = computed(() => {
  if (showExecutionWorkspacePanel.value) {
    const executionFiles = canManageExecutionFiles.value
      ? [
          ...(executionOverview.value?.engineerWorkspaceFiles || []),
          ...(executionOverview.value?.managerArchiveFiles || [])
        ]
      : [...(executionOverview.value?.engineerWorkspaceFiles || [])]

    const executionEntries = executionFiles.map(file => ({
      renderKey: `latest-execution-${file.id}-${file.folderType || 'FILE'}`,
      sourceType: 'execution',
      type: file.fileType,
      name: file.fileName,
      meta: `${file.uploaderName || '未知上传者'} · ${file.uploadedAt || ''}`,
      context: file.secondaryCategory ? `目录：${file.secondaryCategory}` : (String(file.folderType || '') === 'A_MANAGER_ARCHIVE' ? '文件夹 A' : '文件夹 B'),
      canDownload: Boolean(file.canDownload),
      canDelete: Boolean(file.canDelete),
      raw: file
    }))
    return [latestFeasibilityAssetEntry.value, ...executionEntries].filter(Boolean).slice(0, 8)
  }

  return (project.value?.uploads || []).map(file => ({
    renderKey: `latest-asset-${file.id || file.name}`,
    sourceType: 'asset',
    type: file.type,
    name: file.name,
    fileName: file.name,
    meta: `${file.user || '未知上传者'} · ${file.time || ''}`,
    context: formatAssetCategory(file.category),
    canDownload: true,
    raw: file
  }))
})
const latestResultsEmptyText = computed(() => showExecutionWorkspacePanel.value ? '暂无实施成果文件' : '暂无文件上传')
const absoluteReportUrl = computed(() => {
  const url = String(project.value?.feasibilityReportUrl || '')
  if (!url) return ''
  return url.startsWith('http://') || url.startsWith('https://') ? url : `${window.location.origin}${url}`
})
const isBusinessRole = (role) => {
  const normalized = normalizeRole(role)
  return normalized === 'BUSINESS' || normalized === 'BD'
}
const isDataRole = (role) => {
  const normalized = normalizeRoleAlias(role)
  return normalized === 'DATA' || normalized === 'DATA_ENGINEER'
}
const isDevOrAlgorithmRole = (role) => {
  const normalized = normalizeRoleAlias(role)
  return normalized === 'DEV' || normalized === 'ALGORITHM'
}
const isTeamBuildSelectableRole = role => isDevOrAlgorithmRole(role) || isDataRole(role) || normalizeRoleAlias(role) === 'RESEARCH'
const normalizeRoleAlias = role => {
  const normalized = normalizeRole(role)
  if (normalized === 'ALGO') return 'ALGORITHM'
  if (normalized === 'REASE') return 'RESEARCH'
  return normalized
}
const normalizeBuildTeamPayloadRole = role => {
  const normalized = normalizeRoleAlias(role)
  if (normalized === 'DATA_ENGINEER') return 'DATA'
  return normalized
}

const candidateRoleRank = role => {
  const normalized = normalizeRoleAlias(role)
  const order = {
    BUSINESS: 0,
    BD: 0,
    DATA: 1,
    DATA_ENGINEER: 1,
    DEV: 2,
    ALGORITHM: 3,
    RESEARCH: 4,
    ADMIN: 5,
    MEMBER: 6
  }
  return Object.prototype.hasOwnProperty.call(order, normalized) ? order[normalized] : 99
}

const sortCandidatesByRole = (list, roleAccessor = item => item?.role) => {
  return [...(list || [])].sort((a, b) => {
    const roleDiff = candidateRoleRank(roleAccessor(a)) - candidateRoleRank(roleAccessor(b))
    if (roleDiff !== 0) return roleDiff
    const nameDiff = String(a?.name || a?.username || a?.label || '').localeCompare(String(b?.name || b?.username || b?.label || ''), 'zh-Hans-CN')
    if (nameDiff !== 0) return nameDiff
    return String(a?.id || a?.userId || '').localeCompare(String(b?.id || b?.userId || ''))
  })
}

// 组队弹窗的选项
const managerCandidates = computed(() => {
  if (!project.value) return []
  const base = workflowMemberRoleRows.value.length > 0 ? workflowMemberRoleRows.value : (project.value.members || [])
  return sortCandidatesByRole(base)
      .filter(m => isBusinessRole(m.role) || isDataRole(m.role))
      .map(m => ({
        ...m,
        id: m.id || m.userId,
        label: `${m.name || m.username || m.id} / ${formatRole(m.role || 'MEMBER')}`
      }))
      .filter(m => !!m.id)
})

const defaultProjectDataEngineer = computed(() => {
  const members = project.value?.members || []
  const directMatch = members.find(member => normalizeRoleAlias(member.role) === 'DATA_ENGINEER')
  if (directMatch) {
    return {
      ...directMatch,
      id: String(directMatch.userId || directMatch.id || '')
    }
  }

  const managerFallback = members.find(member => {
    const memberId = String(member.userId || member.id || '')
    return memberId === String(project.value?.managerId || '') && isDataRole(member.role)
  })

  return managerFallback
    ? {
        ...managerFallback,
        id: String(managerFallback.userId || managerFallback.id || '')
      }
    : null
})

const memberCandidates = computed(() => {
  const candidateMap = new Map()

  const appendCandidate = (item, source = 'legacy') => {
    const id = String(item?.userId || item?.id || '').trim()
    const role = normalizeRoleAlias(item?.role)
    if (!id || !isTeamBuildSelectableRole(role)) return
    const optionKey = `${id}-${role}`
    if (candidateMap.has(optionKey)) return
    candidateMap.set(optionKey, {
      ...item,
      id: optionKey,
      userId: id,
      role,
      source,
      avatar: item?.hiddenAvatar ? hiddenAvatar : (item?.avatar || defaultAvatar)
    })
  }

  ;(project.value?.members || []).forEach(member => appendCandidate(member, 'project-member'))
  workflowMemberRoleRows.value.forEach(row => appendCandidate(row, 'workflow-role'))

  allUsers.value.forEach(user => appendCandidate(user, 'all-users'))

  const defaultDataId = String(defaultProjectDataEngineer.value?.id || '')
  return sortCandidatesByRole(Array.from(candidateMap.values()), item => item.role).sort((a, b) => {
    const aUserId = String(a.userId || a.id || '')
    const bUserId = String(b.userId || b.id || '')
    if (aUserId === defaultDataId) return -1
    if (bUserId === defaultDataId) return 1
    return 0
  })
})

const activeUserId = computed(() => String(userStore.activeUserInfo?.userId || ''))
const activeUserRole = computed(() => normalizeRole(userStore.activeUserInfo?.role))
const showExecutionManagementPanel = computed(() => isProjectFlow.value && ['IMPLEMENTING', 'SETTLEMENT', 'COMPLETED'].includes(String(project.value?.projectStatus || '').toUpperCase()))
const showExecutionWorkspacePanel = computed(() => {
  if (showExecutionManagementPanel.value) return true
  if (isProductFlow.value) {
    return ['DEMO_EXECUTION', 'MEETING_DECISION', 'TESTING', 'LAUNCHED'].includes(String(project.value?.productStatus || '').toUpperCase())
  }
  if (isResearchFlow.value) {
    return ['DESIGN', 'EXECUTION', 'EVALUATION', 'ARCHIVE', 'PRE_EXECUTION', 'CONSTRUCTION', 'ARCHIVED_TO_MIDDLEWARE'].includes(String(project.value?.researchStatus || '').toUpperCase())
  }
  return false
})
const canManageProjectTaskAssignments = computed(() => !isProductFlow.value && !isResearchFlow.value && isManager.value)
const selectedManager = computed(() => managerCandidates.value.find(user => String(user.id) === String(teamForm.value.managerUserId || '')) || null)
const selectedManagerIsDataEngineer = computed(() => Boolean(selectedManager.value && isDataRole(selectedManager.value.role)))
const selectedMemberDetails = computed(() => memberCandidates.value.filter(user => teamForm.value.teamMembers.includes(user.id)))
const selectedManagerExecutionWeight = computed(() => {
  if (!selectedManagerIsDataEngineer.value) return 0
  return Number(teamMemberWeights.value[String(teamForm.value.managerUserId || '')] || 0)
})
const teamResponsibilityTotal = computed(() => {
  const managerRatio = Number(teamForm.value.managerWeight || 0)
  const membersRatio = selectedMemberDetails.value.reduce((sum, member) => sum + Number(teamMemberWeights.value[member.id] || 0), 0)
  return managerRatio + membersRatio
})
const memberTaskCards = computed(() => {
  return (executionOverview.value?.schedules || []).map(item => ({
    ...item,
    role: item.role || 'MEMBER'
  }))
})

const parseDateInput = value => {
  if (!value && value !== 0) return null
  if (typeof value === 'number') {
    const normalized = value > 0 && value < 100000000000 ? value * 1000 : value
    const parsed = new Date(normalized)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }
  const text = String(value).trim()
  if (!text) return null
  if (/^\d+$/.test(text)) {
    const numeric = Number(text)
    const normalized = text.length <= 10 ? numeric * 1000 : numeric
    const parsed = new Date(normalized)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }
  const normalized = text.includes(' ') ? text.replace(' ', 'T') : text
  const parsed = new Date(normalized)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

const defaultDeadlineTimestamp = () => Date.now()

const projectTaskAssignmentsForTimeline = computed(() => {
  return [...projectTaskAssignments.value].sort((a, b) => {
    const timeA = parseDateInput(a.expectedEndDate)?.getTime() ?? Number.MAX_SAFE_INTEGER
    const timeB = parseDateInput(b.expectedEndDate)?.getTime() ?? Number.MAX_SAFE_INTEGER
    if (timeA !== timeB) return timeA - timeB
    return String(a.name || '').localeCompare(String(b.name || ''))
  })
})

const syncProductTaskSlots = (rawAssignments = []) => {
  const assignmentMap = new Map((rawAssignments || []).map(item => [String(item.userId || ''), item]))
  productTaskAssignments.value = (project.value?.members || []).map(member => {
    const userId = String(member.userId || member.id || '')
    const current = assignmentMap.get(userId) || {}
    return {
      userId,
      name: member.name || current.name || userId,
      role: member.role || current.role || 'MEMBER',
      taskName: current.taskName || '',
      expectedOutput: current.expectedOutput || '',
      expectedEndDate: toPickerTimestamp(current.expectedEndDate) || defaultDeadlineTimestamp()
    }
  })
}

const fetchProductTaskAssignments = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !isProductFlow.value) {
    productTaskAssignments.value = []
    return
  }
  productTaskAssignmentLoading.value = true
  try {
    const res = await request.get(`/api/products/${targetId}/task-assignments`)
    const assignments = res.data || res || []
    syncProductTaskSlots(assignments)
  } catch (error) {
    syncProductTaskSlots([])
    console.error('产品任务分配加载失败:', error)
  } finally {
    productTaskAssignmentLoading.value = false
  }
}

const saveProductTaskAssignments = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !isProductFlow.value || !canManageProductTaskAssignments.value) {
    return
  }
  productTaskAssignmentSaving.value = true
  try {
    const missingDeadline = productTaskAssignments.value.some(item => String(item.taskName || '').trim() && !item.expectedEndDate)
    if (missingDeadline) {
      productTaskAssignmentSaving.value = false
      return ElMessage.warning('已分配任务的成员必须设置截止时间')
    }

    await request.put(`/api/products/${targetId}/task-assignments`, {
      assignments: productTaskAssignments.value.map(item => ({
        userId: item.userId,
        taskName: String(item.taskName || '').trim() || null,
        expectedOutput: String(item.expectedOutput || '').trim() || null,
        expectedEndDate: item.expectedEndDate ? toIsoFromPicker(item.expectedEndDate) : null
      }))
    })
    ElMessage.success('任务分配已保存')
    await fetchProductTaskAssignments()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存任务分配失败')
  } finally {
    productTaskAssignmentSaving.value = false
  }
}

const syncProjectTaskSlots = (rawAssignments = []) => {
  const assignmentMap = new Map((rawAssignments || []).map(item => [String(item.userId || ''), item]))
  projectTaskAssignments.value = (project.value?.members || []).map(member => {
    if (['BUSINESS', 'BD'].includes(String(member.role || '').toUpperCase())) {
      return null
    }
    const userId = String(member.userId || member.id || '')
    const current = assignmentMap.get(userId) || {}
    return {
      userId,
      name: member.name || current.name || userId,
      role: member.role || current.role || 'MEMBER',
      taskName: current.taskName || '',
      expectedOutput: current.expectedOutput || '',
      expectedEndDate: toPickerTimestamp(current.expectedEndDate) || defaultDeadlineTimestamp()
    }
  }).filter(Boolean)
}

const fetchProjectTaskAssignments = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || isProductFlow.value || isResearchFlow.value) {
    projectTaskAssignments.value = []
    return
  }
  projectTaskAssignmentLoading.value = true
  try {
    const res = await request.get(`/api/projects/${targetId}/task-assignments`)
    const assignments = res.data || res || []
    syncProjectTaskSlots(assignments)
  } catch (error) {
    syncProjectTaskSlots([])
    console.error('项目任务分配加载失败:', error)
  } finally {
    projectTaskAssignmentLoading.value = false
  }
}

const saveProjectTaskAssignments = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || isProductFlow.value || isResearchFlow.value || !canManageProjectTaskAssignments.value) {
    return
  }
  projectTaskAssignmentSaving.value = true
  try {
    await request.put(`/api/projects/${targetId}/task-assignments`, {
      assignments: projectTaskAssignments.value.map(item => ({
        userId: item.userId,
        taskName: String(item.taskName || '').trim() || null,
        expectedOutput: String(item.expectedOutput || '').trim() || null,
        expectedEndDate: toIsoFromPicker(item.expectedEndDate)
      }))
    })
    ElMessage.success('任务分配已保存')
    await fetchProjectTaskAssignments()
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存任务分配失败')
  } finally {
    projectTaskAssignmentSaving.value = false
  }
}

const saveImplementationStatus = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !canEditImplementationStatus.value) return
  const status = String(implementationStatusDraft.value || '').trim()
  if (!status) {
    return ElMessage.warning('请先填写实施状态')
  }
  implementationStatusSaving.value = true
  try {
    await request.patch(`/api/projects/${targetId}/implementation-status`, { status })
    ElMessage.success('实施状态已更新')
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '实施状态更新失败')
  } finally {
    implementationStatusSaving.value = false
  }
}

const saveProjectDynamicInfo = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !canEditProjectDynamicInfo.value) return

  projectDynamicInfoSaving.value = true
  try {
    await request.patch(`/api/projects/${targetId}/dynamic-info`, {
      goalDescription: String(dynamicInfoForm.value.goalDescription || '').trim(),
      projectTier: String(dynamicInfoForm.value.projectTier || '').trim() || null,
      techStackDescription: String(dynamicInfoForm.value.techStackDescription || '').trim(),
      implementationStatus: String(dynamicInfoForm.value.implementationStatus || '').trim(),
      estimatedRevenue: dynamicInfoForm.value.estimatedRevenue ? Number(dynamicInfoForm.value.estimatedRevenue) : null
    })
    ElMessage.success('动态信息已更新')
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '动态信息更新失败')
  } finally {
    projectDynamicInfoSaving.value = false
  }
}

const deleteCurrentProject = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !canDeleteCurrentProject.value) return

  try {
    await ElMessageBox.confirm(`确认删除项目「${project.value?.name || targetId}」吗？此操作不可恢复。`, '删除项目', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await request.delete(`/api/projects/${targetId}`)
    ElMessage.success('项目已删除')
    window.location.href = getErpLandingRoute(activeUserRole.value)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '删除项目失败')
  }
}

const isActiveUserProjectMember = roleMatcher => {
  if (!project.value || !activeUserId.value) return false

  return (project.value.members || []).some(member => {
    const memberId = String(member.userId || member.id || '')
    return memberId === activeUserId.value && roleMatcher(member.role)
  })
}

const canBuildTeam = computed(() => {
  if (!project.value || !activeUserId.value) return false
  const stage = String(project.value.projectStatus || '').toUpperCase()
  const hasTeamBuilt = (project.value.members || []).length > 2
  const isTeamFormationStage = stage === 'TEAM_FORMATION' || stage === 'INITIATED' || (stage === 'IMPLEMENTING' && !hasTeamBuilt)
  if (!isTeamFormationStage) return false
  return isActiveUserProjectMember(isDataRole)
})

const canUploadProjectAsset = computed(() => {
  if (!project.value || !activeUserId.value) return false
  if (isProductFlow.value || isResearchFlow.value) return false
  if (String(project.value.projectStatus || '').toUpperCase() !== 'INITIATED') return false
  return activeUserId.value === selectedDataEngineerMemberId.value
})

const ADMIN_USERNAMES = new Set(['Zhangqi', 'guojianwen', 'jiaomiao'])
const activeUsername = computed(() => userStore.activeUserInfo?.username || '')
const isAdminUser = computed(() => ADMIN_USERNAMES.has(activeUsername.value) || activeUserRole.value === 'ADMIN')

const canUseTeamChat = computed(() => {
  if (!project.value || !activeUserId.value) return false
  if (isAdminUser.value) return true
  if (String(project.value.managerId || '') === activeUserId.value) return true
  return (project.value.members || []).some(member => String(member.userId || member.id || '') === activeUserId.value)
})
const canDeleteCurrentProject = computed(() => {
  if (!project.value || !activeUserId.value) return false
  return isAdminUser.value
})

// 3. 权限判定
const isManager = computed(() => {
  if (!project.value || !activeUserId.value) return false

  // 规则1：ADMIN 或指定管理员用户名拥有最高权限
  if (isAdminUser.value) return true

  // 规则2：项目的 managerId 与当前用户 ID 匹配
  const managerId = String(project.value.managerId)
  if (activeUserId.value === managerId) return true

  // 规则3：在组队阶段，项目的数据工程师拥有权限
  if (project.value.projectStatus === 'TEAM_FORMATION') {
    const isDataEngineerOnProject = isActiveUserProjectMember(isDataRole)
    if (isDataEngineerOnProject) return true
  }

  return false
})

const formatMoney = (amount) => {
  const numeric = Number(amount || 0)
  if (!Number.isFinite(numeric)) return '0.00'
  return numeric.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

const formatPercent = (value) => {
  const numeric = Number(value || 0)
  if (!Number.isFinite(numeric)) return '0%'
  return `${numeric.toFixed(2).replace(/\.00$/, '')}%`
}

const formatResponsibilityShare = (earnings) => {
  const current = Number(earnings?.responsibilityRatio || 0)
  const total = Number(earnings?.totalPoolResponsibility || 0)
  return `${current}/${total}`
}

const formatDateTimeLabel = (value) => {
  if (!value) return ''
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return String(value)
  return parsed.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const fetchProjectEarnings = async (projectId) => {
  const targetId = projectId || props.projectId || route.params.id
  if (!targetId || !isProjectFlow.value) {
    projectEarnings.value = null
    projectEarningsLoading.value = false
    return
  }

  projectEarningsLoading.value = true
  try {
    const res = await request.get(`/api/projects/${targetId}/earnings/me`)
    projectEarnings.value = res.data || res || null
  } catch (error) {
    console.error('预计分红测算加载失败:', error)
    projectEarnings.value = null
  } finally {
    projectEarningsLoading.value = false
  }
}

// 4. 获取项目详情
const fetchProject = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId) return

  loading.value = true
  try {
    const res = await request.get(`/api/projects/${targetId}`)
    const data = res.data || res

    // 数据归一化处理，防止 null 导致页面渲染报错
    project.value = {
      ...data,
      flowType: inferProjectFlowType(data),
      projectStatus: data.projectStatus || mapProjectStatusFromLabel(data.status),
      productStatus: data.productStatus || data.status || 'IDEA',
      researchStatus: data.researchStatus || data.status || 'INIT',
      feasibilityReportUrl: data.feasibilityReportUrl || '',
      projectTier: data.projectTier || '',
      members: (data.members || []).map(m => ({
        ...m,
        id: m.id || m.userId
      })),
      milestones: data.milestones || [],
      uploads: data.uploads || [],
      subtasks: data.subtasks || []
    }
    implementationStatusDraft.value = readTaggedDescriptionValue(project.value?.description, '实施状态') || ''
    if (String(project.value.flowType || '').toUpperCase() === 'PRODUCT') {
      selectedProductStatus.value = String(project.value.productStatus || 'IDEA').toUpperCase()
    } else if (String(project.value.flowType || '').toUpperCase() === 'RESEARCH') {
      selectedResearchStatus.value = String(project.value.researchStatus || 'INIT').toUpperCase()
    } else {
      selectedProjectStatus.value = String(project.value.projectStatus || 'INITIATED').toUpperCase()
    }
    await fetchProjectEarnings(targetId)
    await fetchExecutionOverview()
    syncProjectDynamicInfoForm()
    await fetchGitRepositories()
    await fetchChatBootstrap()
    await fetchProductTaskAssignments()
    await fetchProjectTaskAssignments()
    await fetchWorkflowMemberRoles(targetId)
  } catch (e) {
    console.error('项目加载失败:', e)
    ElMessage.error("获取项目详情失败")
  } finally {
    loading.value = false
  }
}

const fetchWorkflowMemberRoles = async (targetId) => {
  if (!targetId) {
    workflowMemberRoleRows.value = []
    return
  }
  try {
    const flowType = String(project.value?.flowType || inferProjectFlowType(project.value || {})).toUpperCase()
    let res
    if (flowType === 'PRODUCT') {
      res = await getProductWorkflowMemberRoles(targetId)
    } else if (flowType === 'RESEARCH') {
      res = await getResearchWorkflowMemberRoles(targetId)
    } else {
      res = await getProjectWorkflowMemberRoles(targetId)
    }
    workflowMemberRoleRows.value = res.data || res || []
  } catch {
    workflowMemberRoleRows.value = []
  }
}

const syncProjectDynamicInfoForm = () => {
  dynamicInfoForm.value = {
    goalDescription: executionOverview.value?.plan?.goalDescription || '',
    projectTier: String(executionOverview.value?.plan?.projectTier || project.value?.projectTier || ''),
    techStackDescription: executionOverview.value?.plan?.techStackDescription || '',
    implementationStatus: readTaggedDescriptionValue(project.value?.description, '实施状态') || '',
    estimatedRevenue: project.value?.estimatedRevenue || ''
  }
}

// 5. 状态切换逻辑 (支持三流并行)
const handleStatusChange = async (newStatus) => {
  const targetId = props.projectId || route.params.id
  const flowType = String(project.value.flowType || '').toUpperCase()

  try {
    if (flowType === 'PRODUCT') {
      await request.put(`/api/projects/${targetId}/product-status`, null, { params: { status: newStatus } })
    } else if (flowType === 'RESEARCH') {
      await request.post(`/api/research/${targetId}/transition`, { toStatus: newStatus })
    } else {
      await request.put(`/api/projects/${targetId}/project-status`, null, { params: { status: newStatus } })
    }

    // 局部更新 UI 状态
    if (flowType === 'PRODUCT') {
      project.value.productStatus = newStatus
      selectedProductStatus.value = newStatus
    } else if (flowType === 'RESEARCH') {
      project.value.researchStatus = newStatus
      selectedResearchStatus.value = newStatus
    } else {
      project.value.projectStatus = newStatus
      selectedProjectStatus.value = newStatus
    }

    ElMessage.success(`状态已成功切换为: ${newStatus}`)
  } catch (e) {
    console.error(e)
    ElMessage.error(e.response?.data?.message || e.message || '状态切换失败')
  }
}

const handleTravelReimbursementSubmitted = () => {
  showTravelReimbursementDialog.value = false
  window.dispatchEvent(new Event('finance-global-refresh'))
}

// 6. 里程碑添加逻辑
const submitMilestone = async () => {
  if (!msForm.value.title || !msForm.value.date) {
    return ElMessage.warning("请填写完整的里程碑信息")
  }

  const targetId = props.projectId || route.params.id
  msLoading.value = true

  try {
    await request.post(`/api/projects/${targetId}/milestones`, {
      title: msForm.value.title,
      dueDate: new Date(msForm.value.date).toISOString()
    })

    ElMessage.success("里程碑规划成功")
    showAddMilestone.value = false
    msForm.value = { title: '', date: '' }
    await fetchProject() // 刷新列表
  } catch (e) {
    ElMessage.error("添加里程碑失败")
  } finally {
    msLoading.value = false
  }
}

// 7. 文件上传逻辑 (点击 & 拖拽)
const triggerFileInput = () => {
  pendingAssetCategory.value = ''
  if (fileInputRef.value) {
    fileInputRef.value.click()
  } else {
    ElMessage.error("上传组件未就绪")
  }
}

const triggerResearchKeyFileUpload = category => {
  if (isResearchFlow.value) {
    showResearchKeyDocUploadDialog.value = true
    return
  }
  pendingAssetCategory.value = category
  if (fileInputRef.value) {
    fileInputRef.value.click()
  } else {
    ElMessage.error('上传组件未就绪')
  }
}

const handleFileChange = async (e) => {
  const files = e.target.files
  if (files && files.length > 0) {
    await uploadFile(files[0])
  }
}

const handleDrop = async (e) => {
  isDragOver.value = false
  const files = e.dataTransfer.files
  if (files && files.length > 0) {
    await uploadFile(files[0])
  }
}

const uploadFile = async (file) => {
  const targetId = props.projectId || route.params.id
  uploading.value = true

  const formData = new FormData()
  formData.append('file', file)
  if (pendingAssetCategory.value) {
    formData.append('assetCategory', pendingAssetCategory.value)
  }

  try {
    await request.post(`/api/projects/${targetId}/assets`, formData, {
      timeout: 60000
    })

    ElMessage.success(`${pendingAssetCategory.value ? '关键文件' : '可行性报告'} ${file.name} 上传成功`)
    // 关键：重新拉取数据，检查状态是否自动流转
    await fetchProject()

  } catch (e) {
    console.error(e)
    const errorMessage = e?.response?.data?.message || e?.response?.data?.error || e?.message || '可行性报告上传失败'
    ElMessage.error(errorMessage)
  } finally {
    uploading.value = false
    pendingAssetCategory.value = ''
    if (fileInputRef.value) fileInputRef.value.value = '' // 清空 input
  }
}

const fetchExecutionOverview = async () => {
  const targetId = props.projectId || route.params.id
  const shouldLoadProjectExecutionPlan = !isProductFlow.value && !isResearchFlow.value
  if (!targetId || (!shouldLoadProjectExecutionPlan && !showExecutionManagementPanel.value && !showExecutionWorkspacePanel.value)) {
    executionOverview.value = null
    managerArchiveActivePath.value = ''
    return
  }

  try {
    const res = await request.get(`/api/projects/${targetId}/execution/overview`)
    executionOverview.value = res.data || res
    syncManagerArchiveSelection()
    syncProjectDynamicInfoForm()
  } catch (error) {
    executionOverview.value = null
    managerArchiveActivePath.value = ''
    console.error('实施概览加载失败:', error)
  }
}

const fetchGitRepositories = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId) return
  gitRepoLoading.value = true
  try {
    const res = await request.get(`/api/projects/${targetId}/git-repositories`)
    const repos = res.data || res || []
    gitRepositories.value = repos
    const selectedExists = repos.some(item => item.id === selectedGitRepoId.value)
    if (!selectedExists) {
      selectedGitRepoId.value = repos.length ? repos[0].id : null
    }
    if (selectedGitRepoId.value) {
      await fetchGitRepositoryLogs(selectedGitRepoId.value)
    } else {
      gitRepoLogs.value = []
    }
  } catch (error) {
    gitRepositories.value = []
    gitRepoLogs.value = []
    console.error('Git 仓库配置加载失败:', error)
  } finally {
    gitRepoLoading.value = false
  }
}

const createGitRepositoryConfig = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId) return
  if (!String(gitRepoForm.value.repositoryUrl || '').trim()) {
    return ElMessage.warning('请填写仓库地址')
  }
  if (!String(gitRepoForm.value.accessToken || '').trim()) {
    return ElMessage.warning('请填写 Token 密钥')
  }
  gitRepoSubmitting.value = true
  try {
    const payload = {
      repositoryUrl: String(gitRepoForm.value.repositoryUrl || '').trim(),
      accessToken: String(gitRepoForm.value.accessToken || '').trim(),
      branch: String(gitRepoForm.value.branch || '').trim() || 'main',
      provider: 'GITHUB'
    }
    const res = await request.post(`/api/projects/${targetId}/git-repositories`, payload)
    const created = res.data || res
    ElMessage.success('Git 仓库配置已创建，请点击测试链接')
    gitRepoForm.value = {
      repositoryUrl: '',
      accessToken: '',
      branch: 'main',
      provider: 'GITHUB'
    }
    await fetchGitRepositories()
    if (created?.id) {
      selectedGitRepoId.value = created.id
      await fetchGitRepositoryLogs(created.id)
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '创建 Git 仓库配置失败')
  } finally {
    gitRepoSubmitting.value = false
  }
}

const selectGitRepository = async repoId => {
  selectedGitRepoId.value = repoId
  await fetchGitRepositoryLogs(repoId)
}

const testGitRepository = async repo => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !repo?.id) return
  try {
    const res = await request.post(`/api/projects/${targetId}/git-repositories/${repo.id}/test`)
    const data = res.data || res || {}
    ElMessage.success(data.message || '链接测试成功')
    await fetchGitRepositories()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '链接测试失败')
    await fetchGitRepositories()
  }
}

const fetchGitRepositoryLogs = async repoId => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !repoId) {
    gitRepoLogs.value = []
    return
  }
  gitRepoLogsLoading.value = true
  try {
    const res = await request.get(`/api/projects/${targetId}/git-repositories/${repoId}/logs`, {
      params: { limit: 40 }
    })
    gitRepoLogs.value = res.data || res || []
  } catch (error) {
    gitRepoLogs.value = []
    ElMessage.error(error.response?.data?.message || error.message || '读取 Git 操作日志失败')
  } finally {
    gitRepoLogsLoading.value = false
  }
}

const startGitRepoPolling = () => {
  stopGitRepoPolling()
  gitRepoPollingTimer = window.setInterval(async () => {
    if (!selectedGitRepoId.value) return
    await fetchGitRepositoryLogs(selectedGitRepoId.value)
  }, 60000)
}

const stopGitRepoPolling = () => {
  if (gitRepoPollingTimer) {
    window.clearInterval(gitRepoPollingTimer)
    gitRepoPollingTimer = null
  }
}

const fetchChatBootstrap = async () => {
  if (!canUseTeamChat.value) {
    chatMessages.value = []
    chatParticipants.value = []
    return
  }
  chatLoading.value = true
  const targetId = props.projectId || route.params.id
  const stageTag = currentChatStageTag.value
  const messageConfig = stageTag ? { params: { stageTag } } : undefined
  try {
    const [messagesRes, participantsRes] = await Promise.all([
      request.get(`/api/projects/${targetId}/chat/messages`, messageConfig),
      request.get(`/api/projects/${targetId}/chat/participants`)
    ])
    chatMessages.value = messagesRes.data || messagesRes || []
    chatParticipants.value = participantsRes.data || participantsRes || []
  } catch (error) {
    chatMessages.value = []
    chatParticipants.value = []
    console.error('聊天加载失败:', error)
  } finally {
    chatLoading.value = false
  }
}

const sendChatMessage = async () => {
  const targetId = props.projectId || route.params.id
  const content = String(chatDraft.value || '').trim()
  if (!content) return ElMessage.warning('请输入聊天消息')
  chatSending.value = true
  try {
    await request.post(`/api/projects/${targetId}/chat/messages`, {
      content,
      stageTag: currentChatStageTag.value || null
    })
    chatDraft.value = ''
    showMentionDropdown.value = false
    mentionSuggestions.value = []
    await fetchChatBootstrap()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '发送消息失败')
  } finally {
    chatSending.value = false
  }
}

const startChatPolling = () => {
  stopChatPolling()
  chatPollingTimer = window.setInterval(async () => {
    if (!canUseTeamChat.value) return
    const targetId = props.projectId || route.params.id
    if (!targetId) return
    try {
      const stageTag = currentChatStageTag.value
      const res = await request.get(`/api/projects/${targetId}/chat/messages`, stageTag ? { params: { stageTag } } : undefined)
      chatMessages.value = res.data || res || []
    } catch {
      // ignore polling errors
    }
  }, 8000)
}

const stopChatPolling = () => {
  if (chatPollingTimer) {
    window.clearInterval(chatPollingTimer)
    chatPollingTimer = null
  }
}

const handleDownload = async (file) => {
  const targetUrl = String(file?.url || project.value?.feasibilityReportUrl || '').trim()
  if (!targetUrl) {
    ElMessage.warning('该文件暂不支持下载')
    return
  }

  try {
    const requestUrl = targetUrl.startsWith('http') ? targetUrl : targetUrl
    const response = await request.get(requestUrl, {
      responseType: 'blob'
    })
    const blob = response instanceof Blob ? response : new Blob([response])
    const blobUrl = window.URL.createObjectURL(blob)
    window.open(blobUrl, '_blank', 'noopener')
    window.setTimeout(() => window.URL.revokeObjectURL(blobUrl), 60000)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '打开文件失败')
  }
}

const openLatestFeasibilityReport = async () => {
  await handleDownload(latestFeasibilityAssetEntry.value?.raw || { url: project.value?.feasibilityReportUrl })
}

const handleFolderBDownload = (file) => {
  downloadExecutionFile(file)
}

const syncManagerArchiveSelection = () => {
  const activePath = normalizeArchiveFolderPath(managerArchiveActivePath.value)
  if (!activePath) {
    managerArchiveActivePath.value = ''
    return
  }
  const folderExists = managerArchiveFolders.value.some(folder => folder.folderPath === activePath)
  const fileExists = (executionOverview.value?.managerArchiveFiles || []).some(file => normalizeArchiveFolderPath(file.secondaryCategory) === activePath)
  managerArchiveActivePath.value = folderExists || fileExists ? activePath : ''
}

const selectManagerArchiveFolder = (folderPath) => {
  managerArchiveActivePath.value = normalizeArchiveFolderPath(folderPath)
}

const goToManagerArchiveParent = () => {
  const activePath = normalizeArchiveFolderPath(managerArchiveActivePath.value)
  if (!activePath) return
  const segments = activePath.split('/')
  segments.pop()
  managerArchiveActivePath.value = segments.join('/')
}

const formatManagerArchiveFileMeta = (file) => {
  const folderPath = normalizeArchiveFolderPath(file?.secondaryCategory)
  const uploader = file?.uploaderName || file?.raw?.user || '未知上传者'
  const uploadedAt = file?.uploadedAt || file?.raw?.time || ''
  return `${folderPath || '根目录'} · ${uploader} · ${uploadedAt}`
}

const createManagerArchiveFolder = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !canManageExecutionFiles.value) return
  const folderName = window.prompt(`请为 ${managerArchiveActivePathLabel.value} 新建子文件夹`, '')
  if (folderName === null) return
  if (!String(folderName || '').trim()) {
    return ElMessage.warning('请填写子文件夹名称')
  }
  try {
    await request.post(`/api/projects/${targetId}/execution/archive-folders`, {
      parentPath: managerArchiveActivePath.value,
      folderName
    })
    ElMessage.success('子文件夹已创建')
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '创建子文件夹失败')
  }
}

const openArchiveMoveDialog = (file) => {
  archiveMoveFile.value = file
  archiveMoveTargetPath.value = normalizeArchiveFolderPath(file?.secondaryCategory)
  showArchiveMoveDialog.value = true
}

const submitArchiveMove = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !archiveMoveFile.value) return
  archiveMoveLoading.value = true
  try {
    await request.patch(`/api/projects/${targetId}/execution/files/${archiveMoveFile.value.id}/archive-folder`, {
      targetFolderPath: archiveMoveTargetPath.value
    })
    ElMessage.success('文件已移动')
    showArchiveMoveDialog.value = false
    archiveMoveFile.value = null
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '移动文件失败')
  } finally {
    archiveMoveLoading.value = false
  }
}

// 提交组队信息
const submitBuildTeam = async () => {
  const targetId = props.projectId || route.params.id
  if (!canBuildTeam.value) {
    return ElMessage.warning('仅当前项目的数据工程师可组建团队')
  }
  if (!teamForm.value.managerUserId || teamForm.value.teamMembers.length === 0) {
    return ElMessage.warning('请指派项目经理并确认团队成员列表')
  }
  if (Number(teamForm.value.managerWeight || 0) <= 0) {
    return ElMessage.warning('请填写 Manager 权责比')
  }
  if (selectedManagerIsDataEngineer.value && !teamForm.value.teamMembers.includes(String(teamForm.value.managerUserId || ''))) {
    return ElMessage.warning('数据工程师兼任 Manager 时，请在团队成员列表中保留该数据工程师并填写其执行权责比')
  }
  if (selectedManagerIsDataEngineer.value && selectedManagerExecutionWeight.value <= 0) {
    return ElMessage.warning('数据工程师兼任 Manager 时，请在团队成员列表中填写其执行权责比')
  }
  if (teamResponsibilityTotal.value !== 100) {
    return ElMessage.warning('组队权责比总和必须为 100')
  }

  // 将 teamMembers 的 userId 转换为 DTO 格式
  const memberDTOs = teamForm.value.teamMembers
    .filter(userId => !(selectedManagerIsDataEngineer.value && String(userId) === String(teamForm.value.managerUserId || '')))
    .map(userId => {
      const user = memberCandidates.value.find(candidate => String(candidate.id) === String(userId))
      const payloadRole = normalizeBuildTeamPayloadRole(user?.role)
      return { userId: String(user?.userId || userId).split('-')[0], role: payloadRole, weight: Number(teamMemberWeights.value[userId] || 0) }
    })

  const payload = {
    managerUserId: teamForm.value.managerUserId,
    managerWeight: Number(teamForm.value.managerWeight || 0),
    managerExecutionWeight: selectedManagerExecutionWeight.value,
    teamMembers: memberDTOs
  }

  try {
    await request.post(`/api/projects/${targetId}/build-team`, payload)
    ElMessage.success('团队组建成功，等待可行性报告上传后进入实施阶段。')
    showBuildTeamDialog.value = false
    await fetchProject() // 刷新项目详情
  } catch (error) {
    const errorData = error.response?.data
    const errorMsg = errorData?.message || (typeof errorData === 'string' ? errorData : '') || error.message || '团队组建失败'
    ElMessage.error(errorMsg)
  }
}

const openExecutionPlanDialog = () => {
  const schedules = executionOverview.value?.schedules || []
  const scheduleMap = new Map(schedules.map(item => [String(item.userId), item]))
  const members = project.value?.members || []
  executionPlanForm.value = {
    goalDescription: executionOverview.value?.plan?.goalDescription || '',
    projectTier: executionOverview.value?.plan?.projectTier || '',
    techStackDescription: executionOverview.value?.plan?.techStackDescription || '',
    memberSchedules: members.map(member => {
      const schedule = scheduleMap.get(String(member.userId)) || {}
      return {
        userId: member.userId,
        name: member.name,
        taskName: schedule.taskName || '',
        expectedOutput: schedule.expectedOutput || '',
        expectedStartDate: toPickerTimestamp(schedule.expectedStartDate),
        expectedEndDate: toPickerTimestamp(schedule.expectedEndDate)
      }
    })
  }
  showExecutionPlanDialog.value = true
}

const toPickerTimestamp = (value) => {
  const parsed = parseDateInput(value)
  if (!parsed) return ''
  return parsed.getTime()
}

const toIsoFromPicker = (value) => {
  const parsed = parseDateInput(value)
  return (parsed || new Date()).toISOString()
}

const submitExecutionPlan = async () => {
  const targetId = props.projectId || route.params.id
  if (!executionPlanForm.value.goalDescription.trim()) {
    return ElMessage.warning('请填写实施目标')
  }
  if (!executionPlanForm.value.techStackDescription.trim()) {
    return ElMessage.warning('请填写技术栈和深度描述')
  }
  if (!executionPlanForm.value.projectTier) {
    return ElMessage.warning('请选择项目评级')
  }

  executionPlanSubmitting.value = true
  try {
    const hasInvalidSchedule = executionPlanForm.value.memberSchedules.some(schedule => {
      if (!String(schedule.taskName || '').trim()) return true
      if (!String(schedule.expectedOutput || '').trim()) return true
      const start = Number(schedule.expectedStartDate || 0)
      const end = Number(schedule.expectedEndDate || 0)
      if (!start || !end) return true
      return end <= start
    })
    if (hasInvalidSchedule) {
      executionPlanSubmitting.value = false
      return ElMessage.warning('请完整填写每位成员的任务、产出和开始/结束时间（结束需晚于开始）')
    }

    await request.post(`/api/projects/${targetId}/execution/plan`, {
      goalDescription: executionPlanForm.value.goalDescription.trim(),
      projectTier: executionPlanForm.value.projectTier,
      techStackDescription: executionPlanForm.value.techStackDescription.trim(),
      memberSchedules: executionPlanForm.value.memberSchedules.map(schedule => ({
        userId: schedule.userId,
        taskName: String(schedule.taskName || '').trim(),
        expectedOutput: String(schedule.expectedOutput || '').trim(),
        expectedStartDate: toIsoFromPicker(schedule.expectedStartDate),
        expectedEndDate: toIsoFromPicker(schedule.expectedEndDate)
      }))
    })
    ElMessage.success('实施计划已保存')
    if (project.value) {
      project.value.projectTier = executionPlanForm.value.projectTier
    }
    showExecutionPlanDialog.value = false
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存实施计划失败')
  } finally {
    executionPlanSubmitting.value = false
  }
}

const confirmMemberTask = async (schedule, confirmed) => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !schedule?.userId) return
  try {
    await request.patch(`/api/projects/${targetId}/execution/schedules/${schedule.userId}/confirm`, { confirmed })
    ElMessage.success(confirmed ? '已确认成员任务完成' : '已取消确认')
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '成员任务确认失败')
  }
}

const openSubtaskDialog = (task = null) => {
  editingSubtaskId.value = task?.id || null
  subtaskForm.value = {
    title: task?.title || '',
    description: task?.description || '',
    assigneeUserId: task?.assigneeUserId || ''
  }
  showSubtaskDialog.value = true
}

const submitSubtask = async () => {
  const targetId = props.projectId || route.params.id
  if (!subtaskForm.value.title.trim()) {
    return ElMessage.warning('请填写子任务标题')
  }
  const payload = {
    title: subtaskForm.value.title.trim(),
    description: subtaskForm.value.description.trim(),
    assigneeUserId: subtaskForm.value.assigneeUserId || null
  }
  try {
    if (editingSubtaskId.value) {
      await request.put(`/api/projects/${targetId}/subtasks/${editingSubtaskId.value}`, payload)
    } else {
      await request.post(`/api/projects/${targetId}/subtasks`, payload)
    }
    ElMessage.success('子任务已保存')
    showSubtaskDialog.value = false
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存子任务失败')
  }
}

const completeSubtask = async task => {
  const targetId = props.projectId || route.params.id
  try {
    await request.post(`/api/projects/${targetId}/subtasks/${task.id}/complete`)
    ElMessage.success('子任务已标记完成')
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '完成子任务失败')
  }
}

const triggerExecutionUpload = (folderType) => {
  const canUpload = folderType === 'A_MANAGER_ARCHIVE'
    ? canUploadManagerExecutionFiles.value
    : canUploadEngineerExecutionFiles.value
  if (!canUpload) {
    ElMessage.warning(folderType === 'A_MANAGER_ARCHIVE' ? '当前不可上传管理归档文件' : '当前不可上传实施成果文件')
    return
  }
  executionUploadFolderType.value = folderType
  executionFileInputRef.value?.click()
}

const triggerExecutionFolderUpload = (folderType) => {
  const canUpload = folderType === 'A_MANAGER_ARCHIVE'
    ? canUploadManagerExecutionFiles.value
    : canUploadEngineerExecutionFiles.value
  if (!canUpload) {
    ElMessage.warning(folderType === 'A_MANAGER_ARCHIVE' ? '当前不可上传管理归档文件夹' : '当前不可上传实施成果文件夹')
    return
  }
  executionUploadFolderType.value = folderType
  executionFolderInputRef.value?.click()
}

const normalizeFolderUploadPath = rawPath => {
  const normalized = String(rawPath || '')
    .replace(/\\/g, '/')
    .replace(/\/+/g, '/')
    .replace(/^\/+|\/+$/g, '')
  if (!normalized.includes('/')) return ''
  const segments = normalized.split('/')
  segments.pop()
  return segments.join('/')
}

const buildExecutionUploadFormData = file => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('folderType', executionUploadFolderType.value)

  if (executionUploadFolderType.value === 'A_MANAGER_ARCHIVE') {
    if (managerArchiveActivePath.value) {
      formData.append('secondaryCategory', managerArchiveActivePath.value)
    }
    return formData
  }

  const relativeFolderPath = normalizeFolderUploadPath(file?.webkitRelativePath)
  if (relativeFolderPath) {
    formData.append('secondaryCategory', relativeFolderPath)
  }
  return formData
}

const resetExecutionUploadInputs = () => {
  if (executionFileInputRef.value) executionFileInputRef.value.value = ''
  if (executionFolderInputRef.value) executionFolderInputRef.value.value = ''
}

const uploadExecutionFiles = async files => {
  const uploadList = Array.from(files || []).filter(Boolean)
  const targetId = props.projectId || route.params.id
  if (!targetId || !uploadList.length) return

  executionUploading.value = true
  let successCount = 0
  const failedFiles = []

  try {
    for (const file of uploadList) {
      try {
        await request.post(`/api/projects/${targetId}/execution/upload`, buildExecutionUploadFormData(file), { timeout: 60000 })
        successCount += 1
      } catch (error) {
        failedFiles.push({
          name: file.name,
          message: error.response?.data?.message || error.message || '执行文件上传失败'
        })
      }
    }

    if (successCount > 0) {
      await fetchExecutionOverview()
    }

    if (failedFiles.length === 0) {
      ElMessage.success(uploadList.length > 1 ? `执行文件上传成功（${successCount}/${uploadList.length}）` : '执行文件上传成功')
      return
    }

    const failedSummary = failedFiles[0]
    if (successCount > 0) {
      ElMessage.warning(`已上传 ${successCount}/${uploadList.length} 个文件，${failedSummary.name} 失败：${failedSummary.message}`)
    } else {
      ElMessage.error(`${failedSummary.name} 上传失败：${failedSummary.message}`)
    }
  } finally {
    executionUploading.value = false
    resetExecutionUploadInputs()
  }
}

const handleExecutionFileChange = async (event) => {
  await uploadExecutionFiles(event.target.files)
}

const handleExecutionFolderChange = async (event) => {
  await uploadExecutionFiles(event.target.files)
}

const downloadExecutionFile = async (file) => {
  try {
    const response = await request.get(`/api/projects/${project.value.id}/execution/files/${file.id}/download`, {
      responseType: 'blob'
    })
    const blob = response instanceof Blob ? response : new Blob([response])
    const blobUrl = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = blobUrl
    anchor.download = file.fileName || `execution-file-${file.id}`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.URL.revokeObjectURL(blobUrl)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '下载文件失败')
  }
}

const deleteExecutionFile = async (file) => {
  if (!window.confirm(`确认删除文件 ${file.fileName} 吗？`)) return
  try {
    await request.delete(`/api/projects/${project.value.id}/execution/files/${file.id}`)
    ElMessage.success('文件已删除')
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '删除文件失败')
  }
}

const recategorizeExecutionFile = async (file) => {
  openArchiveMoveDialog(file)
}

// 8. 辅助工具函数
const getStatusOptions = (flowType) => {
  if (flowType === 'PRODUCT') {
    return {
      'IDEA': '创意孵化', 'PROMOTION': '推广组队', 'DEMO_EXECUTION': 'Demo实施',
      'MEETING_DECISION': '虚拟会议决策', 'TESTING': '测试与上线', 'LAUNCHED': '已转化为正式项目', 'SHELVED': '搁置/流产'
    }
  }
  if (flowType === 'RESEARCH') {
    return {
      'INIT': '发起',
      'BLUEPRINT': '小群蓝图',
      'EXPANSION': '大群深化',
      'DESIGN': '实施前设计',
      'EXECUTION': '施工执行',
      'EVALUATION': '评测',
      'ARCHIVE': '入库完成',
      'SHELVED': '已搁置'
    }
  }
  return {
    'INITIATED': '发起',
    'IMPLEMENTING': '实施',
    'SETTLEMENT': '结算',
    'COMPLETED': '归档'
  }
}

const getActiveStatus = (p) => {
  if (p.flowType === 'PRODUCT') return p.productStatus
  if (p.flowType === 'RESEARCH') return p.researchStatus
  return p.projectStatus
}

const formatDynamicStatus = (p) => {
  const options = getStatusOptions(p.flowType)
  return options[getActiveStatus(p)] || getActiveStatus(p)
}

const formatRole = (role) => {
  const roleMap = {
    'MANAGER': '负责人',
    'ADMIN': '负责人',
    'PM': '经理',
    'LEAD': '组长',
    'BUSINESS': '商务',
    'BD': '商务',
    'PROMOTION': '推广',
    'DATA': '数据',
    'DATA_ENGINEER': '数据',
    'DEV': '开发',
    'ALGORITHM': '算法',
    'RESEARCH': '研究',
    'MEMBER': '成员'
  }
  const normalized = normalizeRoleAlias(role)
  return roleMap[normalized] || normalized || '成员'
}

const formatMemberIdentityTag = member => {
  const roleTagMap = {
    MANAGER: '负责人',
    DATA: '数据',
    DATA_ENGINEER: '数据',
    DEV: '开发',
    ALGORITHM: '算法',
    RESEARCH: '研究',
    PROMOTION: '推广',
    PROMOTION_IC: '推广',
    BUSINESS: '商务',
    BD: '商务'
  }

  if (String(member?.userId || '') === String(project.value?.managerId || '')) return '负责人'

  const normalized = normalizeRoleAlias(member?.role)
  if (roleTagMap[normalized]) return roleTagMap[normalized]

  if (String(member?.userId || '') === String(project.value?.hostUserId || '')) return '研究'
  if (String(member?.userId || '') === String(project.value?.ideaOwnerUserId || '')) return '推广'
  return '成员'
}

const formatAssetCategory = category => {
  const normalized = String(category || '').trim().toUpperCase()
  const categoryMap = {
    FEASIBILITY_REPORT: '可行性报告',
    INITIATION_ATTACHMENT: '发起附件',
    ENGINEERING: '工程文件',
    DEMO_FILE: 'Demo 文件',
    DESCRIPTION: '说明文档',
    FEASIBILITY: '可行性验证',
    MEETING_MINUTES: '会议纪要'
  }
  return categoryMap[normalized] || (normalized ? `分类：${normalized}` : '')
}

const getMemberExecutionResponsibility = member => Number(member?.executionResponsibilityRatio || member?.weight || 0)
const getMemberManagerResponsibility = member => Number(member?.managerResponsibilityRatio || member?.managerWeight || 0)
const getMemberCombinedResponsibility = member => getMemberExecutionResponsibility(member) + getMemberManagerResponsibility(member)
const isMergedDataManagerMember = member => {
  if (isProductFlow.value || isResearchFlow.value) return false
  return String(member?.userId || '') === String(project.value?.managerId || '')
    && isDataRole(member?.role)
    && getMemberCombinedResponsibility(member) > 0
}

const showResponsibilityRatio = member => {
  if (isProductFlow.value || isResearchFlow.value) return false
  return Number(member?.managerResponsibilityRatio || 0) > 0 || Number(member?.executionResponsibilityRatio || 0) > 0
}

const projectMemberRatioText = member => {
  if (isProductFlow.value || isResearchFlow.value) return ''
  const execution = getMemberExecutionResponsibility(member)
  const manager = getMemberManagerResponsibility(member)
  const total = execution + manager
  if (isMergedDataManagerMember(member)) {
    const parts = []
    if (manager > 0) parts.push(`管理：${manager}`)
    if (execution > 0) parts.push(`执行：${execution}`)
    return total > 0 ? `权责比：${total}${parts.length ? `（${parts.join(' / ')}）` : ''}` : ''
  }
  const parts = []
  if (manager > 0) parts.push(`管理：${manager}`)
  if (execution > 0) parts.push(`执行：${execution}`)
  return parts.length ? `权责比：${parts.join(' / ')}` : ''
}

const formatDate = (dateStr) => {
  if (!dateStr) return 'TBD'
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

const formatMilestoneTimestamp = (dateStr) => {
  if (!dateStr) return ''
  const d = parseDateInput(dateStr)
  if (!d) return String(dateStr)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

const isProductStepDone = index => index < activeProductStepIndex.value
const isProductStepCurrent = index => index === activeProductStepIndex.value
const isProductStepLocked = index => index > activeProductStepIndex.value + 1
const getProductStepLockReason = status => {
  if (status === 'DEMO_EXECUTION') return '需在推广阶段完成组队后解锁'
  if (status === 'MEETING_DECISION') return '需在 Demo 阶段完成 4 份核心文件上传后解锁'
  if (status === 'TESTING') return '需在会议阶段提交 OK 决策后解锁'
  if (status === 'LAUNCHED') return '需在测试阶段通过后解锁'
  return '请先完成前置阶段'
}
const selectProductStep = (status, index) => {
  if (isProductStepLocked(index)) {
    ElMessage.warning(getProductStepLockReason(status))
    return
  }
  selectedProductStatus.value = status
}
const getStageOwnerName = status => {
  const members = project.value?.members || []
  const manager = members.find(member => String(member.userId) === String(project.value?.managerId))
  const promotionIc = members.find(member => String(member.role || '').toUpperCase() === 'PROMOTION_IC')
  const host = members.find(member => String(member.userId) === String(project.value?.hostUserId))
  const chief = members.find(member => String(member.userId) === String(project.value?.chiefEngineerUserId))
  if (isResearchFlow.value) {
    if (status === 'EXPANSION') return host?.name || manager?.name || '未指派'
    if (status === 'DESIGN' || status === 'EXECUTION' || status === 'EVALUATION' || status === 'ARCHIVE') {
      return chief?.name || manager?.name || '未指派'
    }
    return manager?.name || '未指派'
  }
  if (!isProductFlow.value) {
    const managerAssigned = hasAssignedProjectManager.value
    if ((activeProjectStatus.value === 'INITIATED' || !managerAssigned) && status !== 'INITIATED') {
      return '暂定'
    }
  }
  if (status === 'PROMOTION' || status === 'TESTING') {
    return promotionIc?.name || manager?.name || '未指派'
  }
  return manager?.name || '未指派'
}

const moveToNextProductStage = async () => {
  if (!isProductFlow.value) return
  if (selectedProductStatus.value === 'IDEA' || selectedProductStatus.value === 'PROMOTION') {
    await openPromotionSetupDialog()
    return
  }
  if (selectedProductStatus.value === 'DEMO_EXECUTION') {
    showDemoUploadDialog.value = true
    return
  }
  if (selectedProductStatus.value === 'MEETING_DECISION') {
    showMeetingDecisionDialog.value = true
    return
  }
  if (selectedProductStatus.value === 'TESTING') {
    openTestingDecisionDialog()
  }
}

const isProjectStepDone = index => index < activeProjectStepIndex.value
const isProjectStepCurrent = index => index === activeProjectStepIndex.value
const isProjectStepLocked = index => index > activeProjectStepIndex.value
const getProjectStepLockReason = status => {
  if (status === 'IMPLEMENTING') return '需在发起阶段完成组队与目标确认后解锁'
  if (status === 'SETTLEMENT') return '需在实施阶段完成任务后解锁'
  if (status === 'COMPLETED') return '需在结算阶段通过后解锁'
  return '请先完成前置阶段'
}
const selectProjectStep = (status, index) => {
  if (isProjectStepLocked(index)) {
    ElMessage.warning(getProjectStepLockReason(status))
    return
  }
  if (index > activeProjectStepIndex.value) {
    ElMessage.warning('请先完成当前阶段后再推进')
    return
  }
  selectedProjectStatus.value = status
}
const moveToNextProjectStage = async () => {
  if (!nextProjectStage.value) return
  await handleStatusChange(nextProjectStage.value)
  selectedProjectStatus.value = nextProjectStage.value
}

const isResearchStepDone = index => index < activeResearchStepIndex.value
const isResearchStepCurrent = index => index === activeResearchStepIndex.value
const isResearchStepLocked = index => index > activeResearchStepIndex.value + 1
const getResearchStepLockReason = status => {
  if (status === 'BLUEPRINT') return '需先完成 INIT 阶段基础信息'
  if (status === 'EXPANSION') return '需完成 BLUEPRINT 阶段确认'
  if (status === 'DESIGN') return '需完成 EXPANSION 阶段推进'
  if (status === 'EXECUTION') return '需完成 DESIGN 阶段定调'
  if (status === 'EVALUATION') return '需完成 EXECUTION 阶段执行'
  if (status === 'ARCHIVE') return '需完成 EVALUATION 阶段评测'
  return '请先完成前置阶段'
}
const selectResearchStep = (status, index) => {
  if (isResearchStepLocked(index)) {
    ElMessage.warning(getResearchStepLockReason(status))
    return
  }
  selectedResearchStatus.value = status
}

const buildResearchTransitionPayload = (fromStatus, toStatus) => {
  const payload = { toStatus }
  if (fromStatus === 'BLUEPRINT' && toStatus === 'EXPANSION') {
    payload.blueprintExists = true
    payload.smallGroupAllConfirmed = true
  }
  if (fromStatus === 'EXPANSION' && toStatus === 'DESIGN') {
    payload.taskPlanDefined = true
    payload.researchTasksAssigned = true
    payload.votePassThreshold = 0.67
  }
  if (fromStatus === 'DESIGN' && toStatus === 'EXECUTION') {
    payload.architectureDefined = true
    payload.techRouteDefined = true
    payload.taskBreakdownComplete = true
    payload.chiefEngineerUserId = project.value?.chiefEngineerUserId || null
  }
  if (fromStatus === 'EXECUTION' && toStatus === 'EVALUATION') {
    payload.allModulesCompleted = true
    payload.integrationSuccess = true
    payload.currentVersionStable = true
    payload.majorTasksCompleted = true
  }
  if (fromStatus === 'EVALUATION' && toStatus === 'ARCHIVE') {
    payload.evaluationCompleted = true
    payload.evaluationResult = 'accepted'
  }
  return payload
}

const moveToNextResearchStage = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId || !nextResearchStage.value) return
  try {
    const from = String(selectedResearchStatus.value || activeResearchStatus.value || 'INIT').toUpperCase()
    const to = String(nextResearchStage.value || '').toUpperCase()
    const payload = buildResearchTransitionPayload(from, to)
    await request.post(`/api/research/${targetId}/transition`, payload)
    project.value.researchStatus = to
    selectedResearchStatus.value = to
    ElMessage.success(`科研状态已推进到 ${to}`)
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '科研状态推进失败')
  }
}

const setResearchExecutionMode = async mode => {
  const targetId = props.projectId || route.params.id
  if (!targetId) return
  try {
    await request.post(`/api/research/${targetId}/set-construction-mode`, null, { params: { executionMode: mode } })
    ElMessage.success(`已设置科研执行模式：${mode}`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '设置执行模式失败')
  }
}

const archiveResearchMiddleware = async () => {
  const targetId = props.projectId || route.params.id
  if (!targetId) return
  const middlewareName = window.prompt('请输入中间件名称')
  if (!middlewareName || !middlewareName.trim()) return
  const middlewareDesc = window.prompt('请输入中间件描述（可空）') || ''
  const repoUrl = window.prompt('请输入仓库地址（可空）') || ''

  try {
    await request.post(`/api/research/${targetId}/archive-to-middleware`, null, {
      params: {
        middlewareName: middlewareName.trim(),
        middlewareDesc,
        repoUrl
      }
    })
    ElMessage.success('科研成果已入库到中间件仓')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '中间件入库失败')
  }
}

const updateMentionSuggestions = () => {
  const text = String(chatDraft.value || '')
  const match = text.match(/(?:^|\s)[@＠]([^\s@＠]{0,30})$/)
  if (!match) {
    mentionSuggestions.value = []
    showMentionDropdown.value = false
    return
  }
  const keyword = String(match[1] || '').trim().toLowerCase()
  const candidateMembers = (chatParticipants.value && chatParticipants.value.length)
    ? chatParticipants.value
    : (project.value?.members || [])
  const list = candidateMembers.filter(member => {
    const name = String(member.name || '').toLowerCase()
    const userId = String(member.userId || member.id || '').toLowerCase()
    return keyword ? (name.includes(keyword) || userId.includes(keyword)) : true
  }).slice(0, 8)
  mentionSuggestions.value = list
  showMentionDropdown.value = list.length > 0
}

const handleChatDraftInput = () => {
  updateMentionSuggestions()
}

const selectMention = member => {
  const text = String(chatDraft.value || '')
  const mentionName = String(member?.name || member?.userId || '').trim()
  chatDraft.value = text.replace(/(?:^|\s)[@＠]([^\s@＠]{0,30})$/, ` @${mentionName} `).replace(/^\s+/, '')
  showMentionDropdown.value = false
  mentionSuggestions.value = []
}

const mapProjectStatusFromLabel = (label) => {
  const labelMap = {
    '发起': 'INITIATED',
    '实施': 'IMPLEMENTING',
    '结算': 'SETTLEMENT',
    '归档': 'COMPLETED',
    '业务线索': 'LEAD',
    '线索': 'LEAD',
    '方案投标': 'BIDDING',
    '投标': 'BIDDING',
    '正式立项': 'INITIATED',
    '发起阶段': 'INITIATED',
    '组队阶段': 'TEAM_FORMATION',
    '项目实施': 'IMPLEMENTING',
    '实施': 'IMPLEMENTING',
    '竣工验收': 'ACCEPTANCE',
    '验收': 'ACCEPTANCE',
    '结算归档': 'SETTLEMENT',
    '已结项': 'COMPLETED',
    '已完成': 'COMPLETED'
  }
  return labelMap[String(label || '').trim()] || 'INITIATED'
}

const normalizeTeamMemberIds = ids => [...new Set((ids || []).map(id => String(id || '')).filter(Boolean))]

const buildInitialTeamState = () => {
  const currentMembers = project.value?.members || []
  const defaultManagerId = managerCandidates.value.some(user => String(user.id) === String(project.value?.managerId || ''))
    ? String(project.value?.managerId || '')
    : String(selectedDataEngineerMemberId.value || '')
  const executionMembers = currentMembers.filter(member => isTeamBuildSelectableRole(member.role))
  const initialTeamMembers = normalizeTeamMemberIds([
    selectedDataEngineerMemberId.value,
    ...executionMembers.map(member => String(member.userId || member.id || ''))
  ])
  const nextWeights = {}
  initialTeamMembers.forEach(memberId => {
    const currentMember = currentMembers.find(member => String(member.userId || member.id || '') === String(memberId))
    nextWeights[memberId] = Number(currentMember?.executionResponsibilityRatio || 0)
  })
  const managerMember = currentMembers.find(member => String(member.userId || member.id || '') === defaultManagerId)
  return {
    managerUserId: defaultManagerId,
    managerWeight: Number(managerMember?.managerResponsibilityRatio || 0),
    teamMembers: initialTeamMembers,
    teamMemberWeights: nextWeights
  }
}

const inviteMember = async () => {
  if (!canBuildTeam.value) {
    ElMessage.warning('仅当前项目的数据工程师可在组队阶段执行该操作')
    return
  }
  await fetchAllUsers()
  const initialState = buildInitialTeamState()
  teamMemberWeights.value = initialState.teamMemberWeights
  teamForm.value = {
    managerUserId: initialState.managerUserId,
    managerWeight: initialState.managerWeight,
    teamMembers: initialState.teamMembers
  }
  showBuildTeamDialog.value = true
}

const fetchAllUsers = async () => {
  if (allUsers.value.length > 0) return // 避免重复加载
  try {
    const res = await request.get('/api/users')
    const users = (res.data || res).map(u => ({
      id: u.userId,
      userId: u.userId,
      name: u.name || u.username,
      username: u.username,
      label: u.name && u.username && u.name !== u.username ? `${u.name} (${u.username})` : (u.name || u.username),
      role: u.role,
      avatar: u.hiddenAvatar ? hiddenAvatar : (u.avatar || defaultAvatar),
      hiddenAvatar: u.hiddenAvatar
    }))
    const zhangQi = users.find(user => String(user.userId || '') === '000010')
    if (zhangQi) {
      users.push({
        ...zhangQi,
        id: `${zhangQi.userId}__DEV`,
        role: 'DEV',
        label: `${zhangQi.name || zhangQi.username || zhangQi.userId}（开发）`
      })
    }
    allUsers.value = users
  } catch (error) {
    console.error('加载所有用户失败:', error)
    ElMessage.error('加载用户列表失败')
  }
}

const inferProjectFlowType = data => {
  const explicit = String(data?.flowType || '').trim().toUpperCase()
  if (['PROJECT', 'PRODUCT', 'RESEARCH'].includes(explicit)) {
    return explicit
  }
  const productStatus = String(data?.productStatus || '').trim().toUpperCase()
  if (productStatus && productStatus !== 'SHELVED') {
    return 'PRODUCT'
  }
  const researchStatus = String(data?.researchStatus || '').trim().toUpperCase()
  if (researchStatus && researchStatus !== 'INITIATED') {
    return 'RESEARCH'
  }
  return 'PROJECT'
}

const openProductMemberDialog = async () => {
  await fetchAllUsers()
  productMemberForm.value = { addUserIds: [] }
  showProductMemberDialog.value = true
}

const openProjectMemberDialog = async () => {
  if (!canManageProjectMembers.value) {
    ElMessage.warning('当前阶段不可调整成员或权限不足')
    return
  }
  await fetchAllUsers()
  const managerId = String(project.value?.managerId || '')
  const managerMember = (project.value?.members || []).find(member => String(member.userId || member.id || '') === managerId)
  const nextWeights = {}
  projectResponsibilityMembers.value.forEach(member => {
    nextWeights[member.id] = Number(member.executionResponsibilityRatio || member.weight || 0)
  })
  projectMemberWeights.value = nextWeights
  projectMemberForm.value = {
    addUserIds: [],
    managerWeight: Number(managerMember?.managerResponsibilityRatio || managerMember?.managerWeight || 0),
    managerExecutionWeight: Number(managerMember?.executionResponsibilityRatio || managerMember?.weight || 0)
  }
  showProjectMemberDialog.value = true
}

const canRemoveProductMember = member => {
  if (!canManageProductMembers.value) return false
  return String(member.userId || '') !== ideaOwnerId.value
}

const canRemoveProjectMember = member => {
  if (!canManageProjectMembers.value) return false
  return String(member?.userId || '') !== String(project.value?.managerId || '')
}

const submitAddProductMembers = async () => {
  const targetId = props.projectId || route.params.id
  const addUserIds = [...new Set(productMemberForm.value.addUserIds || [])]
  if (!addUserIds.length) return ElMessage.warning('请选择至少一个成员')
  productMemberLoading.value = true
  try {
    await request.patch(`/api/products/${targetId}/team-members`, {
      addUserIds,
      removeUserIds: []
    })
    ElMessage.success('成员已新增')
    productMemberForm.value.addUserIds = []
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '新增成员失败')
  } finally {
    productMemberLoading.value = false
  }
}

const openResearchMemberDialog = async () => {
  await fetchAllUsers()
  researchMemberForm.value = { addUserIds: [] }
  showResearchMemberDialog.value = true
}

const canRemoveResearchMember = member => {
  if (!canManageResearchMembers.value) return false
  return String(member.userId || '') !== researchIdeaOwnerId.value
}

const submitAddResearchMembers = async () => {
  const targetId = props.projectId || route.params.id
  const addUserIds = [...new Set(researchMemberForm.value.addUserIds || [])]
  if (!addUserIds.length) return ElMessage.warning('请选择至少一个成员')
  researchMemberLoading.value = true
  try {
    await request.patch(`/api/research/${targetId}/team-members`, {
      addUserIds,
      removeUserIds: []
    })
    ElMessage.success('成员已新增')
    researchMemberForm.value.addUserIds = []
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '新增成员失败')
  } finally {
    researchMemberLoading.value = false
  }
}

const removeResearchMember = async member => {
  const targetId = props.projectId || route.params.id
  const userId = String(member?.userId || '')
  if (!userId) return
  try {
    await ElMessageBox.confirm(`确认移除成员「${member.name}」吗？`, '确认移除', {
      confirmButtonText: '确认移除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch { return }
  researchMemberLoading.value = true
  try {
    await request.patch(`/api/research/${targetId}/team-members`, {
      addUserIds: [],
      removeUserIds: [userId]
    })
    ElMessage.success('成员已移除')
    researchMemberForm.value.addUserIds = []
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '移除成员失败')
  } finally {
    researchMemberLoading.value = false
  }
}

const submitProjectMemberChanges = async () => {
  const targetId = props.projectId || route.params.id
  const addUserIds = [...new Set((projectMemberForm.value.addUserIds || []).map(id => String(id || '')).filter(Boolean))]
  const responsibilityMembers = projectResponsibilityMembers.value.map(member => ({
    userId: member.id,
    role: normalizeBuildTeamPayloadRole(resolveProjectResponsibilityRole(member)),
    weight: Number(projectMemberWeights.value[member.id] || 0)
  }))

  if (!addUserIds.length && !responsibilityMembers.length && Number(projectMemberForm.value.managerWeight || 0) <= 0) {
    return ElMessage.warning('请至少新增成员或调整权责比')
  }
  if (projectResponsibilityTotal.value !== 100) {
    return ElMessage.warning('权责比总和必须为 100')
  }
  if (!responsibilityMembers.some(member => Number(member.weight || 0) > 0) && Number(projectMemberForm.value.managerExecutionWeight || 0) <= 0) {
    return ElMessage.warning('至少需要为一名执行成员分配大于 0 的执行权责比')
  }

  projectMemberLoading.value = true
  try {
    await request.patch(`/api/projects/${targetId}/execution/team-members`, {
      addUserIds,
      removeUserIds: [],
      managerWeight: Number(projectMemberForm.value.managerWeight || 0),
      managerExecutionWeight: projectManagerSupportsExecutionRatio.value ? Number(projectMemberForm.value.managerExecutionWeight || 0) : 0,
      responsibilityMembers
    })
    ElMessage.success('实施成员与权责比已更新')
    showProjectMemberDialog.value = false
    projectMemberForm.value.addUserIds = []
    await fetchProject()
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存成员与权责比失败')
  } finally {
    projectMemberLoading.value = false
  }
}

const submitAddProjectMembers = async () => {
  const targetId = props.projectId || route.params.id
  const addUserIds = [...new Set(projectMemberForm.value.addUserIds || [])]
  if (!addUserIds.length) return ElMessage.warning('请选择至少一个成员')
  projectMemberLoading.value = true
  try {
    await request.patch(`/api/projects/${targetId}/execution/team-members`, {
      addUserIds,
      removeUserIds: []
    })
    ElMessage.success('成员已新增，并已加入实施任务看板')
    projectMemberForm.value.addUserIds = []
    await fetchProject()
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '新增成员失败')
  } finally {
    projectMemberLoading.value = false
  }
}

const removeProductMember = async member => {
  const targetId = props.projectId || route.params.id
  if (!canRemoveProductMember(member)) return
  if (!window.confirm(`确认移除成员 ${member.name} 吗？`)) return
  productMemberLoading.value = true
  try {
    await request.patch(`/api/products/${targetId}/team-members`, {
      addUserIds: [],
      removeUserIds: [member.userId]
    })
    ElMessage.success('成员已移除')
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '移除成员失败')
  } finally {
    productMemberLoading.value = false
  }
}

const removeProjectMember = async member => {
  const targetId = props.projectId || route.params.id
  if (!canRemoveProjectMember(member)) return
  if (!window.confirm(`确认移除成员 ${member.name} 吗？`)) return
  projectMemberLoading.value = true
  try {
    await request.patch(`/api/projects/${targetId}/execution/team-members`, {
      addUserIds: [],
      removeUserIds: [member.userId]
    })
    ElMessage.success('成员已移除')
    await fetchProject()
    await fetchExecutionOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '移除成员失败')
  } finally {
    projectMemberLoading.value = false
  }
}

const openPromotionSetupDialog = async () => {
  await fetchAllUsers()
  const memberIds = (project.value?.members || []).map(member => member.userId)
  const promotionIc = (project.value?.members || []).find(member => String(member.role || '').toUpperCase() === 'PROMOTION_IC')
  const demoMembers = (project.value?.members || []).filter(member => String(member.role || '').toUpperCase() === 'DEMO_ENG').map(member => member.userId)
  promotionSetupForm.value = {
    promotionIcUserId: promotionIc?.userId || project.value?.promotionIcUserId || '',
    promotionMemberIds: memberIds,
    demoEngineerIds: demoMembers,
    demoEngineeringOwnerUserId: project.value?.demoEngineeringOwnerUserId || demoMembers[0] || '',
    demoFileOwnerUserId: project.value?.demoFileOwnerUserId || demoMembers[1] || '',
    demoDescriptionOwnerUserId: project.value?.demoDescriptionOwnerUserId || demoMembers[2] || '',
    demoFeasibilityOwnerUserId: project.value?.demoFeasibilityOwnerUserId || demoMembers[3] || '',
    projectTier: String(project.value?.projectTier || '').toUpperCase(),
    projectType: String(project.value?.projectType || 'BUSINESS').toUpperCase()
  }
  showPromotionSetupDialog.value = true
}

const submitPromotionSetup = async () => {
  const targetId = props.projectId || route.params.id
  if (!promotionSetupForm.value.promotionIcUserId) return ElMessage.warning('请选择推广执行人')
  if ((promotionSetupForm.value.promotionMemberIds || []).length < 2) return ElMessage.warning('推广成员至少需要 2 人')
  if ((promotionSetupForm.value.demoEngineerIds || []).length !== 4) return ElMessage.warning('Demo 工程师必须精确 4 人')
  if (!promotionSetupForm.value.demoEngineeringOwnerUserId || !promotionSetupForm.value.demoFileOwnerUserId || !promotionSetupForm.value.demoDescriptionOwnerUserId || !promotionSetupForm.value.demoFeasibilityOwnerUserId) {
    return ElMessage.warning('请为四类 Demo 文件分别指定责任人')
  }
  if (!promotionSetupForm.value.projectTier) return ElMessage.warning('推广阶段必须完成评级')
  if (!promotionSetupForm.value.projectType) return ElMessage.warning('请选择行业分类')
  productActionLoading.value = true
  try {
    await request.post(`/api/products/${targetId}/promotion-setup`, {
      promotionIcUserId: promotionSetupForm.value.promotionIcUserId,
      promotionMemberIds: promotionSetupForm.value.promotionMemberIds,
      demoEngineerIds: promotionSetupForm.value.demoEngineerIds,
      demoEngineeringOwnerUserId: promotionSetupForm.value.demoEngineeringOwnerUserId,
      demoFileOwnerUserId: promotionSetupForm.value.demoFileOwnerUserId,
      demoDescriptionOwnerUserId: promotionSetupForm.value.demoDescriptionOwnerUserId,
      demoFeasibilityOwnerUserId: promotionSetupForm.value.demoFeasibilityOwnerUserId,
      projectTier: promotionSetupForm.value.projectTier,
      projectType: promotionSetupForm.value.projectType
    })
    ElMessage.success('推广与 Demo 组队已完成，已进入 Demo 阶段')
    showPromotionSetupDialog.value = false
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '推广配置失败')
  } finally {
    productActionLoading.value = false
  }
}

const onDemoFileChange = (slot, event) => {
  const file = event.target.files?.[0] || null
  demoUploadForm.value[slot] = file
}

const submitDemoUploads = async () => {
  const targetId = props.projectId || route.params.id
  const entries = Object.entries(demoUploadForm.value).filter(([, file]) => !!file)
  if (!entries.length) return ElMessage.warning('请至少选择一个文件上传')
  productActionLoading.value = true
  try {
    for (const [demoFileType, file] of entries) {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('demoFileType', demoFileType)
      await request.post(`/api/products/${targetId}/demo/upload`, formData, { timeout: 60000 })
    }
    ElMessage.success('Demo 文件上传完成，系统已自动校验阶段')
    demoUploadForm.value = { ENGINEERING: null, DEMO_FILE: null, DESCRIPTION: null, FEASIBILITY: null }
    showDemoUploadDialog.value = false
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || 'Demo 文件上传失败')
  } finally {
    productActionLoading.value = false
  }
}

const onResearchDocChange = (category, event) => {
  const file = event.target.files?.[0] || null
  researchKeyDocUploadForm.value[category] = file
}

const submitResearchKeyDocUploads = async () => {
  const targetId = props.projectId || route.params.id
  const entries = Object.entries(researchKeyDocUploadForm.value).filter(([, file]) => !!file)
  if (!entries.length) return ElMessage.warning('请至少选择一个文件上传')
  productActionLoading.value = true
  try {
    for (const [docType, file] of entries) {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('docType', docType)
      await request.post(`/api/research/${targetId}/upload-key-doc`, formData, { timeout: 60000 })
    }
    ElMessage.success('文档上传完成')
    researchKeyDocUploadForm.value = {
      RESEARCH_BLUEPRINT_DOC: null,
      RESEARCH_ARCHITECTURE_DOC: null,
      RESEARCH_TASK_BREAKDOWN_DOC: null,
      RESEARCH_EVALUATION_REPORT: null
    }
    showResearchKeyDocUploadDialog.value = false
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '文档上传失败')
  } finally {
    productActionLoading.value = false
  }
}

const onMeetingMinutesFileChange = event => {
  meetingDecisionForm.value.meetingMinutesFile = event.target.files?.[0] || null
}

const submitMeetingDecision = async decision => {
  const targetId = props.projectId || route.params.id
  if (!meetingDecisionForm.value.meetingMinutesFile) return ElMessage.warning('请上传会议纪要文件')
  if (!(meetingDecisionForm.value.participantUserIds || []).length) return ElMessage.warning('请选择参会成员')
  productActionLoading.value = true
  try {
    const formData = new FormData()
    formData.append('meetingMinutes', meetingDecisionForm.value.meetingMinutesFile)
    formData.append('decision', decision)
    ;(meetingDecisionForm.value.participantUserIds || []).forEach(userId => formData.append('participantUserIds', userId))
    await request.post(`/api/products/${targetId}/meeting-decision`, formData, { timeout: 60000 })
    ElMessage.success(decision === 'OK' ? '会议决策已通过，进入测试阶段' : '会议已驳回，项目进入搁置')
    showMeetingDecisionDialog.value = false
    meetingDecisionForm.value = { participantUserIds: [], meetingMinutesFile: null }
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '会议决策提交失败')
  } finally {
    productActionLoading.value = false
  }
}

const openTestingDecisionDialog = () => {
  testingDecisionForm.value = { testFeedback: '' }
  showTestingDecisionDialog.value = true
}

const submitTestingDecision = async isPassed => {
  const targetId = props.projectId || route.params.id
  if (!String(testingDecisionForm.value.testFeedback || '').trim()) return ElMessage.warning('请填写测试反馈')
  productActionLoading.value = true
  try {
    await request.post(`/api/products/${targetId}/testing-feedback`, null, {
      params: {
        testFeedback: testingDecisionForm.value.testFeedback.trim(),
        isPassed
      }
    })
    ElMessage.success(isPassed ? '测试通过，已转入正式实施项目' : '测试未通过，项目已搁置')
    showTestingDecisionDialog.value = false
    await fetchProject()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '测试决策提交失败')
  } finally {
    productActionLoading.value = false
  }
}

// 9. 生命周期
onMounted(fetchProject)
onMounted(startChatPolling)
onMounted(startGitRepoPolling)
onBeforeUnmount(stopChatPolling)
onBeforeUnmount(stopGitRepoPolling)

watch(() => route.params.id, (newId) => {
  if (newId) fetchProject()
})

// 关键修复：同时监听从 props 传入的 projectId
watch(() => props.projectId, (newId) => {
  if (newId) fetchProject()
})

watch(activeProductStatus, value => {
  if (!selectedProductStatus.value) {
    selectedProductStatus.value = value
  }
})

watch(activeProjectStatus, value => {
  if (!selectedProjectStatus.value) {
    selectedProjectStatus.value = value
  }
})

watch(activeResearchStatus, value => {
  if (!selectedResearchStatus.value) {
    selectedResearchStatus.value = value
  }
})

watch(() => selectedProductStatus.value, async () => {
  if (isProductFlow.value) {
    await fetchChatBootstrap()
  }
})

watch(() => selectedResearchStatus.value, async () => {
  if (isResearchFlow.value) {
    await fetchChatBootstrap()
  }
})

watch(
  () => [teamForm.value.teamMembers.slice(), selectedDataEngineerMemberId.value],
  () => {
    const normalizedMembers = normalizeTeamMemberIds([
      selectedDataEngineerMemberId.value,
      ...(teamForm.value.teamMembers || [])
    ])
    const currentMembers = normalizeTeamMemberIds(teamForm.value.teamMembers || [])

    if (normalizedMembers.length !== currentMembers.length || normalizedMembers.some((memberId, index) => memberId !== currentMembers[index])) {
      teamForm.value.teamMembers = normalizedMembers
      return
    }

    const next = { ...teamMemberWeights.value }
    normalizedMembers.forEach(memberId => {
      if (typeof next[memberId] === 'undefined') {
        next[memberId] = 0
      }
    })
    Object.keys(next).forEach(memberId => {
      if (!normalizedMembers.includes(memberId)) {
        delete next[memberId]
      }
    })
    teamMemberWeights.value = next
  },
  { deep: true }
)
watch(
  () => [normalizeTeamMemberIds(projectMemberForm.value.addUserIds || []), projectResponsibilityMembers.value.map(member => member.id).join('|')],
  () => {
    const next = { ...projectMemberWeights.value }
    projectResponsibilityMembers.value.forEach(member => {
      if (typeof next[member.id] === 'undefined') {
        next[member.id] = Number(member.executionResponsibilityRatio || member.weight || 0)
      }
    })
    Object.keys(next).forEach(memberId => {
      if (!projectResponsibilityMembers.value.some(member => member.id === memberId)) {
        delete next[memberId]
      }
    })
    projectMemberWeights.value = next
  },
  { deep: true }
)

</script>

<style scoped>
/* 1. 基础布局与动画 */
.detail-container {
  --card-gap: 20px;
  --section-gap: 12px;
  --panel-pair-gap: 14px;
  padding: 36px 32px 42px;
  width: 100%;
  max-width: 1360px;
  margin: 0 auto;
  background: var(--science-surface-muted);
  box-sizing: border-box;
  overflow-x: hidden;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 2. 头部区域：流派图标与标题 */
.header-section {
  margin-bottom: 30px;
}

.project-title-row {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
}

/* 🟢 修改后的流派图标样式 - 完美对齐侧边栏逻辑 */
.type-icon-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px;
  border-radius: 12px;
  min-width: 64px;
  border: 2.5px solid #0f172a;
  background: var(--science-surface);
  box-shadow: 4px 4px 0px #0f172a;
  transition: all 0.3s ease;
}

/* 匹配 flowType: PRODUCT */
.type-icon-wrapper.PRODUCT {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 4px 4px 0px #2563eb;
}
.type-icon-wrapper.PRODUCT .type-text-tiny { color: #2563eb; }

/* 匹配 flowType: PROJECT */
.type-icon-wrapper.PROJECT {
  border-color: #7c3aed;
  background: #f5f3ff;
  box-shadow: 4px 4px 0px #7c3aed;
}
.type-icon-wrapper.PROJECT .type-text-tiny { color: #7c3aed; }

.type-text-tiny {
  font-size: 10px;
  font-weight: 900;
  margin-top: 4px;
  letter-spacing: 0.5px;
}

.p-name {
  font-size: 32px;
  font-weight: 900;
  color: var(--text-main);
  margin: 0;
  flex: 1;
}

.p-desc {
  color: var(--text-sub);
  font-size: 15px;
  max-width: 800px;
  margin-top: 8px;
  line-height: 1.6;
}

.report-link-row {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  flex-wrap: wrap;
  font-size: 13px;
}

.report-link-label {
  color: var(--text-sub);
  font-weight: 700;
}

.report-link {
  color: #2563eb;
  text-decoration: none;
  word-break: break-all;
}

.report-link-button {
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
  font: inherit;
}

.report-link:hover {
  text-decoration: underline;
}

.detail-cta-row {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}


/* 3. 状态勋章：高对比度按钮 */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 800;
  border: 2.5px solid var(--border-soft);
  background: var(--science-surface);
  color: var(--text-main);
  box-shadow: 4px 4px 0px #0f172a;
  transition: all 0.2s;
  height: 44px;
  box-sizing: border-box;
}

.status-badge.clickable:hover {
  transform: translate(-2px, -2px);
  box-shadow: 6px 6px 0px #0f172a;
}

/* 状态颜色动态适配 */
.status-badge.DEVELOPING, .status-badge.IMPLEMENTING { background: #dbeafe; border-color: #2563eb; color: #1e40af; }
.status-badge.COMPLETED { background: #dcfce7; border-color: #16a34a; color: #166534; }
.status-badge.TESTING, .status-badge.BIDDING { background: #ffedd5; border-color: #f97316; color: #9a3412; }

/* 4. 主网格布局 */
.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(320px, 0.92fr);
  gap: var(--card-gap) !important;
  margin-top: var(--section-gap);
  align-items: start;
  width: 100%;
}

.left-col {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap) !important;
  align-items: stretch;
}

.project-task-panel-left {
  margin-left: 0;
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap) !important;
}

.panel {
  background: var(--science-surface);
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  padding: 24px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.timeline-panel {
  max-height: 460px;
  overflow: auto;
}

.product-stepper-panel {
  margin-bottom: var(--card-gap);
}

.product-stepper-track {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 14px;
}

.project-stepper-track {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.product-stepper-node {
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 12px;
  cursor: pointer;
  background: var(--science-surface-muted);
  min-height: 112px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.product-stepper-node.current {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.16);
}

.product-stepper-node.done {
  border-color: #16a34a;
  background: #f0fdf4;
}

.product-stepper-node.locked {
  opacity: 0.55;
}

.node-dot {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: #0f172a;
  color: #fff;
  font-size: 12px;
  margin-bottom: 8px;
}

.node-label {
  font-weight: 700;
  font-size: 13px;
  line-height: 1.3;
}

.node-owner {
  font-size: 11px;
  color: #64748b;
  margin-top: auto;
}

.product-flow-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(320px, 0.92fr);
  column-gap: var(--panel-pair-gap) !important;
  row-gap: var(--card-gap) !important;
  margin-bottom: 0;
  align-items: start;
}

.detail-body-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(320px, 0.92fr);
  gap: var(--card-gap) !important;
  align-items: start;
  margin-top: var(--card-gap);
}

.flow-column {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap) !important;
  min-width: 0;
}

.flow-column > .panel,
.left-col > .panel,
.right-col > .panel {
  min-width: 0;
}

.smart-block-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.smart-block {
  background: var(--science-surface-muted);
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 10px 12px;
}

.earnings-panel {
  background: linear-gradient(180deg, #fff8eb 0%, #fff 100%);
  border-color: rgba(217, 119, 6, 0.2);
}

.earnings-caption {
  margin-top: 6px;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.5;
}

.earnings-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.earnings-hero {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(251, 191, 36, 0.18), rgba(249, 115, 22, 0.08));
  border: 1px solid rgba(217, 119, 6, 0.16);
}

.earnings-pool-pill,
.earnings-tier-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  width: fit-content;
}

.earnings-pool-pill.business {
  background: #dcfce7;
  color: #166534;
}

.earnings-pool-pill.execution {
  background: #dbeafe;
  color: #1d4ed8;
}

.earnings-pool-pill.none {
  background: #e5e7eb;
  color: #374151;
}

.earnings-tier-pill {
  background: rgba(217, 119, 6, 0.12);
  color: #9a3412;
}

.earnings-amount {
  font-size: 34px;
  font-weight: 900;
  letter-spacing: -0.04em;
  color: #7c2d12;
}

.earnings-footnote {
  color: #7c2d12;
  font-size: 13px;
  line-height: 1.6;
}

.earnings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.earnings-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.assignment-summary-block {
  margin-top: 10px;
}

.compact-assignment-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 180px;
  overflow: auto;
}

.compact-assignment-item {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.demo-upload-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.action-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.lock-reason {
  margin-top: 10px;
  font-size: 12px;
  color: #b45309;
}

.panel-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  min-height: 36px;
}

.panel-title {
  font-size: 13px;
  font-weight: 800;
  color: var(--text-sub);
  letter-spacing: 1.5px;
  text-transform: uppercase;
  margin: 0;
}

.latest-results-header {
  align-items: flex-start;
}

.latest-results-actions {
  margin-top: 0;
  justify-content: flex-end;
}

/* 5. 里程碑时间轴微调 */
.node-title {
  font-weight: 700;
  font-size: 13px;
  color: var(--text-main);
}

:deep(.el-timeline-item) {
  padding-bottom: 10px;
}

:deep(.el-timeline-item__timestamp) {
  font-size: 12px;
}

.node-title.done {
  color: #94a3b8;
  text-decoration: line-through;
}

/* 6. 团队面板 (SQUAD) */
.avatar-group {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(108px, 108px));
  justify-content: center;
  gap: 18px 20px;
}

.member-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 7px;
  width: 108px;
  text-align: center;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 3px solid #f1f5f9;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  transition: transform 0.2s;
}

.member-item.prioritized .avatar {
  border-width: 4px;
  border-color: #facc15;
  box-shadow: 0 0 0 2px rgba(250, 204, 21, 0.35), 0 10px 18px rgba(250, 204, 21, 0.28);
}

.avatar:hover {
  transform: scale(1.1) rotate(5deg);
  border-color: #2563eb;
}

.role-badge {
  position: static;
  background: var(--science-dark-bg);
  color: white;
  font-size: 11px;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 6px;
  font-weight: bold;
  white-space: nowrap;
}

.ratio-badge-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  width: 100%;
  min-height: 32px;
}

.ratio-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 78px;
  min-height: 32px;
  font-size: 13px;
  line-height: 1;
  padding: 0 12px;
  border-radius: 10px;
  color: #fff;
  font-weight: 800;
  letter-spacing: 0.01em;
  font-variant-numeric: tabular-nums;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.ratio-badge-manager {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
}

.ratio-badge-exec {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
}

.execution-grid {
  display: grid;
  gap: 24px;
  margin-top: 30px;
}

.execution-plan-content,
.execution-section,
.file-folder-column,
.schedule-list,
.execution-file-list,
.ratio-editor-list,
.schedule-editor-list {
  display: grid;
  gap: 12px;
}

.project-task-panel .schedule-list {
  max-height: 420px;
  overflow: auto;
}

.execution-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.execution-tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #075985;
  font-size: 12px;
  font-weight: 700;
}

.execution-label,
.folder-desc,
.schedule-dates,
.schedule-role {
  color: var(--text-sub);
  font-size: 13px;
}

.execution-text {
  color: var(--text-main);
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.schedule-item,
.execution-file-item,
.ratio-editor-item,
.schedule-editor-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.65);
}

.schedule-item {
  flex-direction: column;
}

.schedule-header,
.schedule-status-row,
.folder-header-row,
.folder-header-actions,
.file-action-row,
.schedule-editor-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.schedule-delay.late,
.text-action.danger {
  color: #dc2626;
}

.file-folder-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 18px;
  align-items: start;
}

.file-folder-grid.single-folder-grid {
  grid-template-columns: minmax(0, 1fr);
}

.file-folder-column {
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 20px;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.84), rgba(248, 250, 252, 0.92));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.folder-a-column {
  background: linear-gradient(160deg, rgba(245, 243, 255, 0.95), rgba(255, 255, 255, 0.88));
}

.folder-b-column {
  background: linear-gradient(160deg, rgba(239, 246, 255, 0.95), rgba(255, 255, 255, 0.88));
}

.folder-b-column-full {
  max-width: none;
}

.folder-title strong {
  display: block;
  font-size: 16px;
  color: var(--text-main);
}

.folder-header-row {
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.folder-header-actions {
  justify-content: flex-end;
}

.folder-desc {
  margin: 6px 0 0;
  max-width: 42ch;
  line-height: 1.6;
}

.archive-path-row,
.archive-breadcrumb-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.archive-breadcrumb-row {
  margin-top: -2px;
}

.archive-breadcrumb-sep,
.archive-folder-path {
  color: var(--text-sub);
  font-size: 12px;
}

.archive-folder-list {
  display: grid;
  gap: 10px;
}

.archive-folder-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px dashed rgba(37, 99, 235, 0.25);
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  text-align: left;
}

.archive-folder-item:hover {
  border-color: rgba(37, 99, 235, 0.5);
  background: rgba(239, 246, 255, 0.88);
}

.archive-folder-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
}

.execution-file-list {
  align-content: start;
}

.execution-file-item {
  background: rgba(255, 255, 255, 0.82);
}

.text-action {
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-weight: 600;
}

.schedule-editor-item,
.ratio-editor-item {
  align-items: center;
}

.ratio-total-row {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #0f766e;
}

.ratio-total-row.invalid {
  color: #dc2626;
}

.compact-list {
  margin-top: 8px;
}

.add-member-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 2.5px dashed #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
  justify-self: center;
  align-self: start;
  color: #94a3b8;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-member-btn:hover {
  border-color: #2563eb;
  color: #2563eb;
  background: #eff6ff;
}

/* 7. 资产面板 (ASSETS) */
.file-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px;
  border-radius: 10px;
  transition: background 0.2s;
}

.actionable-file-item {
  justify-content: space-between;
}

.file-item:hover {
  background: var(--science-surface-muted);
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-icon {
  width: 42px;
  height: 42px;
  background: var(--science-dark-bg);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 900;
  color: #fff;
}

.file-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 2px;
}

.file-meta {
  font-size: 12px;
  color: var(--text-sub);
}

.download-btn {
  border: none;
  background: var(--science-surface-muted);
  color: var(--text-sub);
  width: 32px;
  height: 32px;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: all 0.2s;
}

.download-btn:hover {
  background: var(--science-dark-bg);
  color: #fff;
}

.upload-placeholder {
  margin-top: 20px;
  border: 2px dashed var(--border-soft);
  border-radius: 12px;
  padding: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--text-sub);
  font-weight: 600;
  cursor: pointer;
}

.upload-placeholder:hover {
  border-color: #2563eb;
  color: #2563eb;
  background: #eff6ff;
}

.git-repo-panel {
  margin-top: 18px;
}

.git-form-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.git-repo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.git-repo-item {
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  background: #f8fafc;
}

.git-repo-item.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.git-repo-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  margin-bottom: 4px;
}

.git-repo-head > * {
  min-width: 0;
}

.git-repo-head strong,
.git-log-meta strong,
.schedule-header strong,
.file-name {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.git-log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.git-log-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.git-log-item {
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 10px;
  padding: 10px 12px;
  background: #ffffff;
}

.git-log-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

/* 8. 辅助状态 */
.loading-state, .error-state {
  text-align: center;
  padding: 120px 0;
  color: var(--text-sub);
}

.spinner {
  border: 4px solid #f1f5f9;
  border-top: 4px solid #2563eb;
  border-radius: 50%;
  width: 32px;
  height: 32px;
  animation: spin 1s linear infinite;
  margin: 0 auto 16px;
}

@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }

.empty-panel-text {
  text-align: center;
  color: var(--text-sub);
  padding: 40px 0;
  font-style: italic;
  font-size: 14px;
}

.permission-hint {
  font-size: 12px;
  color: var(--text-sub);
  padding: 4px 10px;
  background: var(--science-surface-muted);
  border-radius: 6px;
  font-weight: 600;
}

/* 9. Element Plus 深度定制 */
:deep(.el-timeline-item__timestamp) {
  font-weight: 800;
  color: #64748b;
}

:deep(.tech-dialog) {
  border-radius: 16px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
}

:deep(.build-team-dialog .el-dialog__body) {
  padding-top: 18px;
  padding-bottom: 14px;
}

.build-team-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
  align-items: start;
}

:deep(.build-team-form .el-form-item) {
  margin-bottom: 8px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar-small {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  flex: 0 0 22px;
}

.option-name {
  color: var(--text-main);
  font-weight: 600;
}

.option-role {
  color: var(--text-sub);
  font-size: 12px;
}

:deep(.project-manager-select-popper .el-select-dropdown__item),
:deep(.project-team-select-popper .el-select-dropdown__item) {
  height: auto;
  line-height: 1.2;
  padding-top: 8px;
  padding-bottom: 8px;
}

.chat-panel {
  margin-top: 18px;
}

.chat-message-list {
  max-height: 260px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.chat-message-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--border-soft);
  background: var(--science-surface-muted);
}

.chat-meta {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.chat-content {
  font-size: 14px;
  color: var(--text-main);
  line-height: 1.5;
  white-space: pre-wrap;
}

.chat-input-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: end;
}

.chat-editor-wrap {
  position: relative;
}

.mention-dropdown {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 8px);
  background: var(--science-surface);
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  box-shadow: 0 8px 20px rgba(2, 6, 23, 0.12);
  max-height: 220px;
  overflow-y: auto;
  z-index: 12;
}

.mention-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  gap: 8px;
  align-items: center;
  text-align: left;
  padding: 8px 10px;
  cursor: pointer;
}

.mention-item:hover {
  background: var(--science-surface-muted);
}

.mention-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.inline-subtask-header {
  margin-bottom: 8px;
}

.compact-header {
  margin-bottom: 14px;
}

.empty-panel-text.compact {
  padding: 12px 0;
}

.folder-divider {
  height: 1px;
  background: var(--border-soft);
  margin: 14px 0;
}

.member-manage-section {
  margin-top: 14px;
  display: grid;
  gap: 10px;
}

.member-manage-list {
  display: grid;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.member-manage-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 8px 10px;
}

@media (max-width: 1280px) {
  .detail-body-grid,
  .main-grid,
  .product-flow-grid,
  .file-folder-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .detail-container {
    width: min(100vw - 24px, 100%);
    padding: 22px 14px 30px;
  }

  .detail-body-grid,
  .main-grid,
  .product-flow-grid,
  .product-stepper-track,
  .demo-upload-grid,
  .earnings-grid {
    grid-template-columns: 1fr;
  }

  .project-title-row,
  .latest-results-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .build-team-form {
    grid-template-columns: 1fr;
  }

  .file-folder-grid,
  .main-grid,
  .schedule-editor-row,
  .folder-header-row,
  .ratio-editor-item {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .folder-header-actions,
  .archive-folder-item {
    width: 100%;
  }
}

.status-dropdown {
  width: auto;
  max-width: 100%;
}

.status-dropdown .status-badge,
.status-badge.static-badge {
  margin-left: auto;
  width: auto;
  max-width: 100%;
}
</style>
