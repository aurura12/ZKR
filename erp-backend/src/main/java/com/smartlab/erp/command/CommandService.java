package com.smartlab.erp.command;

import com.smartlab.erp.command.dto.CommandExecuteRequest;
import com.smartlab.erp.command.dto.CommandExecuteResponse;
import com.smartlab.erp.command.dto.CommandInterpretRequest;
import com.smartlab.erp.command.dto.CommandPreviewResponse;
import com.smartlab.erp.dto.ProductIdeaRequest;
import com.smartlab.erp.dto.ProductMemberUpdateRequest;
import com.smartlab.erp.dto.ProjectInitiateRequestDTO;
import com.smartlab.erp.dto.ResearchInitiateRequest;
import com.smartlab.erp.dto.ResearchStatusTransitionRequest;
import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProductStatus;
import com.smartlab.erp.entity.ProjectStatus;
import com.smartlab.erp.entity.ProjectType;
import com.smartlab.erp.entity.ResearchStatus;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.service.ProductFlowService;
import com.smartlab.erp.service.ProjectFlowService;
import com.smartlab.erp.service.ProjectService;
import com.smartlab.erp.service.ResearchFlowService;
import com.smartlab.erp.util.AuthUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommandService {

    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:预算|投入|收入|预计收入|金额)[^0-9]{0,6}([0-9]+(?:\\.[0-9]+)?)(亿|万|千)?");
    private static final Set<String> STOP_TOKENS = Set.of("预算", "投入", "收入", "预计收入", "数据工程师", "行业", "负责人", "经理", "主持人", "总工", "总工程师", "创新点", "核心成员", "成员", "并", "然后");

    private final UserRepository userRepository;
    private final SysProjectRepository projectRepository;
    private final ProductFlowService productFlowService;
    private final ProjectService projectService;
    private final ProjectFlowService projectFlowService;
    private final ResearchFlowService researchFlowService;

    public CommandPreviewResponse interpret(CommandInterpretRequest request) {
        UserPrincipal currentUser = requireErpUser();
        String text = normalizeCommandText(request == null ? null : request.getText());
        if (text.isBlank()) {
            throw new BusinessException("请输入要执行的业务命令");
        }

        List<User> erpUsers = loadErpUsers();
        List<SysProject> accessibleProjects = loadAccessibleProjects(currentUser, request == null ? null : request.getCurrentProjectId());
        CommandActionType actionType = resolveActionType(text);

        return switch (actionType) {
            case CREATE_PRODUCT_PROJECT -> previewCreateProduct(text, currentUser);
            case CREATE_DELIVERY_PROJECT -> previewCreateDelivery(text, currentUser, erpUsers);
            case CREATE_RESEARCH_PROJECT -> previewCreateResearch(text, currentUser, erpUsers);
            case ADD_PROJECT_MEMBER -> previewAddProjectMember(text, request, accessibleProjects, erpUsers);
            case ADVANCE_WORKFLOW -> previewAdvanceWorkflow(text, request, accessibleProjects);
            case UNKNOWN -> preview(
                    CommandActionType.UNKNOWN,
                    "未识别命令",
                    "当前命令窗口第一轮支持发起三类项目、增加项目成员和推进流程。",
                    List.of(
                            "可试试：发起产品项目 智能投放引擎",
                            "可试试：创建交付项目 A客户交付，数据工程师是 高健，行业是 工业",
                            "可试试：把蒋之骏加入项目最终测试",
                            "可试试：把当前项目推进到下一阶段"
                    ),
                    List.of("请换一种更明确的表达再试"),
                    List.of(),
                    Map.of());
        };
    }

    public CommandExecuteResponse execute(CommandExecuteRequest request) {
        UserPrincipal currentUser = requireErpUser();
        if (request == null || request.getActionType() == null || request.getPayload() == null) {
            throw new BusinessException("命令执行载荷不能为空");
        }

        CommandActionType actionType = CommandActionType.valueOf(request.getActionType().trim().toUpperCase(Locale.ROOT));
        Map<String, Object> payload = request.getPayload();

        return switch (actionType) {
            case CREATE_PRODUCT_PROJECT -> executeCreateProduct(payload);
            case CREATE_DELIVERY_PROJECT -> executeCreateDelivery(payload);
            case CREATE_RESEARCH_PROJECT -> executeCreateResearch(payload);
            case ADD_PROJECT_MEMBER -> executeAddProjectMember(payload);
            case ADVANCE_WORKFLOW -> executeAdvanceWorkflow(payload, currentUser);
            case UNKNOWN -> throw new BusinessException("未识别命令，无法执行");
        };
    }

    private CommandPreviewResponse previewCreateProduct(String text, UserPrincipal currentUser) {
        String projectName = firstNonBlank(
                extractNamedValue(text, "名字叫", "名称是", "项目名是", "叫"),
                extractByPattern(text, "(?:发起|创建)(?:一个|一项|个)?产品(?:项目)?[：:\\s]*([^，。,；;]+)"));
        BigDecimal budget = extractMoney(text);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectName", projectName);
        payload.put("expectedBudget", budget == null ? BigDecimal.ZERO.toPlainString() : budget.toPlainString());
        payload.put("targetUsers", extractNamedValue(text, "目标用户", "用户画像"));
        payload.put("coreFeatures", extractNamedValue(text, "核心功能", "主打功能", "卖点"));
        payload.put("useCase", extractNamedValue(text, "用途", "使用场景"));
        payload.put("problemStatement", extractNamedValue(text, "问题", "解决的问题"));
        payload.put("techStackDesc", extractNamedValue(text, "技术栈", "技术路线"));

        List<String> missingFields = new ArrayList<>();
        if (isBlank(projectName)) {
            missingFields.add("产品名称");
        }

        List<String> previewLines = new ArrayList<>();
        previewLines.add("发起人：" + displayCurrentUser(currentUser));
        previewLines.add("项目名称：" + defaultText(projectName, "待从命令中补充"));
        previewLines.add("预算：" + ((budget == null) ? "默认按 0 处理" : budget.toPlainString() + " CNY"));
        previewLines.add("执行说明：若目标用户、核心功能等未明确，系统会使用可编辑占位描述创建初稿。 ");

        List<String> warnings = new ArrayList<>();
        warnings.add("产品项目会以当前 ERP 账号作为 Idea 主理人创建。 ");

        return preview(
                CommandActionType.CREATE_PRODUCT_PROJECT,
                "发起产品项目",
                isBlank(projectName) ? "先补充产品名称后即可发起。" : "将创建一个新的产品流项目草稿。",
                previewLines,
                missingFields,
                warnings,
                payload);
    }

    private CommandPreviewResponse previewCreateDelivery(String text, UserPrincipal currentUser, List<User> erpUsers) {
        String projectName = firstNonBlank(
                extractNamedValue(text, "项目名是", "项目名称是", "名字叫", "名称是", "叫"),
                extractByPattern(text, "(?:发起|创建)(?:一个|一项|个)?(?:交付)?项目[：:\\s]*([^，。,；;]+)"));
        User dataEngineer = resolveUserFromMarker(text, erpUsers, "数据工程师", "数据同学");
        ProjectType projectType = extractProjectType(text);
        BigDecimal estimatedRevenue = extractMoney(text);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectName", projectName);
        payload.put("dataEngineerId", dataEngineer == null ? "" : dataEngineer.getUserId());
        payload.put("projectType", projectType == null ? "" : projectType.name());
        payload.put("estimatedRevenue", estimatedRevenue == null ? BigDecimal.ZERO.toPlainString() : estimatedRevenue.toPlainString());

        List<String> missingFields = new ArrayList<>();
        if (isBlank(projectName)) missingFields.add("交付项目名称");
        if (dataEngineer == null) missingFields.add("数据工程师");
        if (projectType == null) missingFields.add("行业分类");

        List<String> warnings = new ArrayList<>();
        if (!"BUSINESS".equalsIgnoreCase(String.valueOf(currentUser.getRole()))) {
            warnings.add("当前账号不是 BUSINESS 角色，执行时后端会拒绝发起交付项目。 ");
        }
        if (estimatedRevenue == null) {
            warnings.add("未识别到预计收入，系统将按 0 填充。 ");
        }

        return preview(
                CommandActionType.CREATE_DELIVERY_PROJECT,
                "发起交付项目",
                missingFields.isEmpty() ? "将创建一个新的交付流项目。" : "补齐关键参数后即可发起交付项目。",
                List.of(
                        "发起人：" + displayCurrentUser(currentUser),
                        "项目名称：" + defaultText(projectName, "待补充"),
                        "数据工程师：" + defaultText(displayUser(dataEngineer), "待补充"),
                        "行业分类：" + (projectType == null ? "待补充" : projectType.name()),
                        "预计收入：" + (estimatedRevenue == null ? "默认按 0 处理" : estimatedRevenue.toPlainString() + " CNY")
                ),
                missingFields,
                warnings,
                payload);
    }

    private CommandPreviewResponse previewCreateResearch(String text, UserPrincipal currentUser, List<User> erpUsers) {
        String idea = firstNonBlank(
                extractNamedValue(text, "课题是", "主题是", "idea是", "项目名是", "名字叫", "叫"),
                extractByPattern(text, "(?:发起|创建)(?:一个|一项|个)?科研(?:项目|课题)?[：:\\s]*([^，。,；;]+)"));
        String innovationPoint = firstNonBlank(
                extractNamedValue(text, "创新点是", "创新点为", "创新点"),
                isBlank(idea) ? null : "围绕“" + idea + "”的创新研究方案");
        BigDecimal budget = extractMoney(text);
        User host = resolveUserFromMarker(text, erpUsers, "主持人", "host");
        User chiefEngineer = resolveUserFromMarker(text, erpUsers, "总工程师", "总工", "chief engineer");
        LinkedHashSet<String> coreMemberIds = resolveResearchCoreMembers(text, currentUser, erpUsers, host, chiefEngineer);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idea", idea);
        payload.put("innovationPoint", innovationPoint);
        payload.put("budget", budget == null ? BigDecimal.ZERO.toPlainString() : budget.toPlainString());
        payload.put("hostUserId", host == null ? currentUser.getId() : host.getUserId());
        payload.put("chiefEngineerUserId", chiefEngineer == null ? currentUser.getId() : chiefEngineer.getUserId());
        payload.put("coreMemberIds", new ArrayList<>(coreMemberIds));

        List<String> missingFields = new ArrayList<>();
        if (isBlank(idea)) missingFields.add("科研课题/idea");
        if (coreMemberIds.isEmpty()) missingFields.add("至少 1 名核心成员（发起人之外）");

        List<String> warnings = new ArrayList<>();
        if (!"RESEARCH".equalsIgnoreCase(String.valueOf(currentUser.getRole()))) {
            warnings.add("当前账号不是 RESEARCH 角色，执行时后端会拒绝发起科研项目。 ");
        }
        if (budget == null) warnings.add("未识别到预算，系统将按 0 填充。 ");

        List<String> previewLines = new ArrayList<>();
        previewLines.add("发起人：" + displayCurrentUser(currentUser));
        previewLines.add("科研主题：" + defaultText(idea, "待补充"));
        previewLines.add("创新点：" + defaultText(innovationPoint, "将按主题生成基础描述"));
        previewLines.add("主持人：" + defaultText(displayUser(host), displayCurrentUser(currentUser)));
        previewLines.add("总工程师：" + defaultText(displayUser(chiefEngineer), displayCurrentUser(currentUser)));
        previewLines.add("核心成员：" + (coreMemberIds.isEmpty()
                ? "待补充"
                : String.join("、", coreMemberIds.stream()
                        .map(id -> findUserById(erpUsers, id))
                        .filter(Objects::nonNull)
                        .map(this::displayUser)
                        .toList())));

        return preview(
                CommandActionType.CREATE_RESEARCH_PROJECT,
                "发起科研项目",
                missingFields.isEmpty() ? "将创建一个新的科研流项目。" : "补齐科研主题或核心成员后即可发起。",
                previewLines,
                missingFields,
                warnings,
                payload);
    }

    private CommandPreviewResponse previewAddProjectMember(String text,
                                                           CommandInterpretRequest request,
                                                           List<SysProject> accessibleProjects,
                                                           List<User> erpUsers) {
        SysProject project = resolveProject(text, request == null ? null : request.getCurrentProjectId(), accessibleProjects);
        User member = resolveAddMemberUser(text, erpUsers);

        List<String> missingFields = new ArrayList<>();
        if (project == null) missingFields.add("目标项目");
        if (member == null) missingFields.add("新增成员");

        List<String> warnings = new ArrayList<>();
        if (project != null && project.getFlowType() == FlowType.RESEARCH) {
            warnings.add("科研流成员增删尚未接入命令窗口，请先使用详情页。 ");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", project == null ? "" : project.getProjectId());
        payload.put("memberUserId", member == null ? "" : member.getUserId());
        payload.put("flowType", project == null || project.getFlowType() == null ? "" : project.getFlowType().name());

        return preview(
                CommandActionType.ADD_PROJECT_MEMBER,
                "增加项目成员",
                missingFields.isEmpty() ? "将把指定 ERP 成员加入目标项目。" : "请补充项目名和成员名后再执行。",
                List.of(
                        "目标项目：" + defaultText(project == null ? null : project.getName(), "待补充"),
                        "新增成员：" + defaultText(displayUser(member), "待补充"),
                        "项目流类型：" + (project == null || project.getFlowType() == null ? "待识别" : project.getFlowType().name())
                ),
                missingFields,
                warnings,
                payload);
    }

    private CommandPreviewResponse previewAdvanceWorkflow(String text,
                                                          CommandInterpretRequest request,
                                                          List<SysProject> accessibleProjects) {
        SysProject project = resolveProject(text, request == null ? null : request.getCurrentProjectId(), accessibleProjects);
        List<String> missingFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> payload = new LinkedHashMap<>();

        if (project == null) {
            missingFields.add("目标项目");
            return preview(
                    CommandActionType.ADVANCE_WORKFLOW,
                    "推进流程",
                    "请先指定项目名，或在项目详情页内执行“推进到下一阶段”。",
                    List.of("目标项目：待补充"),
                    missingFields,
                    warnings,
                    payload);
        }

        payload.put("projectId", project.getProjectId());
        payload.put("flowType", project.getFlowType().name());
        payload.put("targetStatus", "");

        String summary = "将尝试把目标项目推进到下一阶段。";
        boolean canExecute = true;
        switch (project.getFlowType()) {
            case PROJECT -> {
                ProjectStatus target = nextProjectStatus(project);
                if (target == null) {
                    canExecute = false;
                    warnings.add("当前项目已处于终态，无法继续推进。 ");
                } else {
                    payload.put("targetStatus", target.name());
                    warnings.add("交付流推进仍会经过后端完整校验，例如可行性报告、成员与子任务状态。 ");
                }
            }
            case PRODUCT -> {
                ProductStatus current = project.getProductStatus() == null ? ProductStatus.IDEA : project.getProductStatus();
                ProductStatus target = nextProductStatus(project);
                if (target == null) {
                    canExecute = false;
                    warnings.add("当前产品流阶段不支持通过命令窗口直接推进。 ");
                } else {
                    payload.put("targetStatus", target.name());
                    if (current == ProductStatus.MEETING_DECISION || current == ProductStatus.TESTING) {
                        canExecute = false;
                        warnings.add("会议决策和测试上线阶段依赖专用文件/反馈，仍需使用详情页动作面板。 ");
                    } else {
                        warnings.add("产品流命令推进仅执行通用状态跃迁，不替代推广组队、会议纪要或测试反馈等专用动作。 ");
                    }
                }
            }
            case RESEARCH -> {
                ResearchStatus target = nextResearchStatus(project);
                if (target == null) {
                    canExecute = false;
                    warnings.add("当前科研流阶段已终态，无法继续推进。 ");
                } else {
                    payload.put("targetStatus", target.name());
                    Map<String, Object> flags = buildResearchTransitionFlags(project, text);
                    payload.putAll(flags);
                    List<String> flagMissing = missingResearchFlags(project, flags);
                    if (!flagMissing.isEmpty()) {
                        canExecute = false;
                        missingFields.addAll(flagMissing);
                    }
                }
            }
        }

        return CommandPreviewResponse.builder()
                .actionType(CommandActionType.ADVANCE_WORKFLOW.name())
                .title("推进流程")
                .summary(summary)
                .confirmRequired(true)
                .canExecute(canExecute && missingFields.isEmpty())
                .previewLines(List.of(
                        "目标项目：" + project.getName(),
                        "流程类型：" + project.getFlowType().name(),
                        "当前状态：" + project.getCurrentStatus(),
                        "目标状态：" + defaultText(stringValue(payload.get("targetStatus")), "当前阶段无法推进")
                ))
                .missingFields(missingFields)
                .warnings(warnings)
                .payload(payload)
                .build();
    }

    private CommandExecuteResponse executeCreateProduct(Map<String, Object> payload) {
        String name = requiredString(payload, "projectName", "产品名称不能为空");
        ProductIdeaRequest request = new ProductIdeaRequest();
        request.setName(name);
        request.setExpectedBudget(decimalFromPayload(payload, "expectedBudget", BigDecimal.ZERO));
        request.setProjectType(ProjectType.valueOf(requiredString(payload, "projectType", "行业分类不能为空")));
        request.setTargetUsers(defaultText(stringValue(payload.get("targetUsers")), "由业务命令窗口创建，待补充目标用户画像"));
        request.setCoreFeatures(defaultText(stringValue(payload.get("coreFeatures")), "待进一步细化的核心功能"));
        request.setUseCase(defaultText(stringValue(payload.get("useCase")), "由命令窗口发起的产品场景"));
        request.setProblemStatement(defaultText(stringValue(payload.get("problemStatement")), "待进一步补充的问题描述"));
        request.setTechStackDesc(defaultText(stringValue(payload.get("techStackDesc")), "待确定技术路线"));
        request.setDescription(name + "｜由 AI 命令窗口发起");
        SysProject project = productFlowService.createIdea(request);
        return CommandExecuteResponse.builder()
                .actionType(CommandActionType.CREATE_PRODUCT_PROJECT.name())
                .resultSummary("已创建产品项目《" + project.getName() + "》")
                .navigateTo("/workspace/project/" + project.getProjectId())
                .result(Map.of("projectId", project.getProjectId(), "projectName", project.getName()))
                .build();
    }

    private CommandExecuteResponse executeCreateDelivery(Map<String, Object> payload) {
        ProjectInitiateRequestDTO request = new ProjectInitiateRequestDTO();
        request.setProjectName(requiredString(payload, "projectName", "交付项目名称不能为空"));
        request.setDataEngineerId(requiredString(payload, "dataEngineerId", "数据工程师不能为空"));
        request.setProjectType(ProjectType.valueOf(requiredString(payload, "projectType", "行业分类不能为空")));
        request.setEstimatedRevenue(decimalFromPayload(payload, "estimatedRevenue", BigDecimal.ZERO));
        SysProject project = projectService.initiateProject(request);
        return CommandExecuteResponse.builder()
                .actionType(CommandActionType.CREATE_DELIVERY_PROJECT.name())
                .resultSummary("已创建交付项目《" + project.getName() + "》")
                .navigateTo("/workspace/project/" + project.getProjectId())
                .result(Map.of("projectId", project.getProjectId(), "projectName", project.getName()))
                .build();
    }

    private CommandExecuteResponse executeCreateResearch(Map<String, Object> payload) {
        ResearchInitiateRequest request = new ResearchInitiateRequest();
        request.setIdea(requiredString(payload, "idea", "科研课题不能为空"));
        request.setInnovationPoint(defaultText(stringValue(payload.get("innovationPoint")), "由命令窗口生成的创新研究说明"));
        request.setBudget(decimalFromPayload(payload, "budget", BigDecimal.ZERO));
        request.setHostUserId(stringValue(payload.get("hostUserId")));
        request.setChiefEngineerUserId(stringValue(payload.get("chiefEngineerUserId")));
        request.setCoreMemberIds(stringList(payload.get("coreMemberIds")));
        SysProject project = researchFlowService.initiateResearch(request);
        return CommandExecuteResponse.builder()
                .actionType(CommandActionType.CREATE_RESEARCH_PROJECT.name())
                .resultSummary("已创建科研项目《" + project.getName() + "》")
                .navigateTo("/workspace/project/" + project.getProjectId())
                .result(Map.of("projectId", project.getProjectId(), "projectName", project.getName()))
                .build();
    }

    private CommandExecuteResponse executeAddProjectMember(Map<String, Object> payload) {
        String projectId = requiredString(payload, "projectId", "项目不能为空");
        String memberUserId = requiredString(payload, "memberUserId", "成员不能为空");
        FlowType flowType = FlowType.valueOf(requiredString(payload, "flowType", "流程类型不能为空"));
        ProductMemberUpdateRequest request = new ProductMemberUpdateRequest();
        request.setAddUserIds(List.of(memberUserId));
        request.setRemoveUserIds(List.of());

        Map<String, Object> result;
        if (flowType == FlowType.PRODUCT) {
            result = productFlowService.updateProductMembers(projectId, request);
        } else if (flowType == FlowType.PROJECT) {
            result = projectFlowService.updateExecutionMembers(projectId, request);
        } else {
            throw new BusinessException("科研流成员调整暂未接入命令窗口，请先使用详情页");
        }

        return CommandExecuteResponse.builder()
                .actionType(CommandActionType.ADD_PROJECT_MEMBER.name())
                .resultSummary("已将成员加入项目")
                .navigateTo("/workspace/project/" + projectId)
                .result(result)
                .build();
    }

    private CommandExecuteResponse executeAdvanceWorkflow(Map<String, Object> payload, UserPrincipal currentUser) {
        String projectId = requiredString(payload, "projectId", "项目不能为空");
        FlowType flowType = FlowType.valueOf(requiredString(payload, "flowType", "流程类型不能为空"));
        String targetStatus = requiredString(payload, "targetStatus", "目标状态不能为空");

        if (flowType == FlowType.PROJECT) {
            projectService.transitionProjectStatus(projectId, ProjectStatus.valueOf(targetStatus), currentUser.getId());
        } else if (flowType == FlowType.PRODUCT) {
            projectService.transitionProductStatus(projectId, ProductStatus.valueOf(targetStatus), currentUser.getId());
        } else {
            ResearchStatusTransitionRequest request = new ResearchStatusTransitionRequest();
            request.setToStatus(targetStatus);
            request.setBlueprintExists(booleanValue(payload.get("blueprintExists")));
            request.setSmallGroupAllConfirmed(booleanValue(payload.get("smallGroupAllConfirmed")));
            request.setTaskPlanDefined(booleanValue(payload.get("taskPlanDefined")));
            request.setResearchTasksAssigned(booleanValue(payload.get("researchTasksAssigned")));
            request.setArchitectureDefined(booleanValue(payload.get("architectureDefined")));
            request.setTechRouteDefined(booleanValue(payload.get("techRouteDefined")));
            request.setTaskBreakdownComplete(booleanValue(payload.get("taskBreakdownComplete")));
            request.setAllModulesCompleted(booleanValue(payload.get("allModulesCompleted")));
            request.setIntegrationSuccess(booleanValue(payload.get("integrationSuccess")));
            request.setCurrentVersionStable(booleanValue(payload.get("currentVersionStable")));
            request.setMajorTasksCompleted(booleanValue(payload.get("majorTasksCompleted")));
            request.setEvaluationCompleted(booleanValue(payload.get("evaluationCompleted")));
            request.setEvaluationResult(stringValue(payload.get("evaluationResult")));
            request.setVotePassRate(doubleValue(payload.get("votePassRate")));
            request.setVotePassThreshold(doubleValue(payload.get("votePassThreshold")));
            request.setChiefEngineerUserId(stringValue(payload.get("chiefEngineerUserId")));
            researchFlowService.transitionStatus(projectId, request);
        }

        return CommandExecuteResponse.builder()
                .actionType(CommandActionType.ADVANCE_WORKFLOW.name())
                .resultSummary("流程已推进到下一阶段")
                .navigateTo("/workspace/project/" + projectId)
                .result(Map.of("projectId", projectId, "targetStatus", targetStatus))
                .build();
    }

    private CommandPreviewResponse preview(CommandActionType actionType,
                                           String title,
                                           String summary,
                                           List<String> previewLines,
                                           List<String> missingFields,
                                           List<String> warnings,
                                           Map<String, Object> payload) {
        return CommandPreviewResponse.builder()
                .actionType(actionType.name())
                .title(title)
                .summary(summary)
                .confirmRequired(true)
                .canExecute(missingFields.isEmpty())
                .previewLines(previewLines)
                .missingFields(missingFields)
                .warnings(warnings)
                .payload(payload)
                .build();
    }

    private UserPrincipal requireErpUser() {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        if (currentUser.getAccountDomain() != null && currentUser.getAccountDomain() != AccountDomain.ERP) {
            throw new PermissionDeniedException("命令窗口当前仅支持 ERP 域账号");
        }
        return currentUser;
    }

    private List<User> loadErpUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getAccountDomain() == AccountDomain.ERP)
                .sorted(Comparator.comparing(user -> displayUser(user), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<SysProject> loadAccessibleProjects(UserPrincipal currentUser, String currentProjectId) {
        Map<String, SysProject> projects = new LinkedHashMap<>();
        projectRepository.findManagedProjects(currentUser.getId()).forEach(project -> projects.put(project.getProjectId(), project));
        projectRepository.findParticipatedProjects(currentUser.getId()).forEach(project -> projects.put(project.getProjectId(), project));
        if (!isBlank(currentProjectId)) {
            projectRepository.findProjectByIdAndUser(currentProjectId, currentUser.getId())
                    .ifPresent(project -> projects.put(project.getProjectId(), project));
        }
        return new ArrayList<>(projects.values());
    }

    private CommandActionType resolveActionType(String text) {
        if (containsAny(text, "加入", "添加", "拉进", "拉入", "新增成员")) {
            return CommandActionType.ADD_PROJECT_MEMBER;
        }
        if (containsAny(text, "推进", "下一阶段", "下个阶段", "推进流程", "流转")) {
            return CommandActionType.ADVANCE_WORKFLOW;
        }
        if (containsAny(text, "发起", "创建", "新建")) {
            if (containsAny(text, "产品")) return CommandActionType.CREATE_PRODUCT_PROJECT;
            if (containsAny(text, "科研", "研究")) return CommandActionType.CREATE_RESEARCH_PROJECT;
            if (containsAny(text, "项目", "交付")) return CommandActionType.CREATE_DELIVERY_PROJECT;
        }
        return CommandActionType.UNKNOWN;
    }

    private User resolveAddMemberUser(String text, List<User> users) {
        User fromMarker = resolveUserFromSegment(extractByPattern(text, "(?:把|将|让)([^，。,；;]+?)(?:加入|添加到|加到|拉进|拉入)"), users);
        if (fromMarker != null) return fromMarker;
        List<User> matches = matchedUsers(text, users);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private SysProject resolveProject(String text, String currentProjectId, List<SysProject> accessibleProjects) {
        if (!isBlank(currentProjectId) && containsAny(text, "当前项目", "这个项目", "本项目")) {
            return accessibleProjects.stream().filter(project -> currentProjectId.equals(project.getProjectId())).findFirst().orElse(null);
        }
        List<SysProject> matches = accessibleProjects.stream()
                .filter(project -> containsIgnoreCase(text, project.getName()))
                .sorted(Comparator.comparingInt((SysProject project) -> project.getName() == null ? 0 : project.getName().length()).reversed())
                .toList();
        if (!matches.isEmpty()) return matches.get(0);
        if (!isBlank(currentProjectId)) {
            return accessibleProjects.stream().filter(project -> currentProjectId.equals(project.getProjectId())).findFirst().orElse(null);
        }
        return null;
    }

    private List<User> matchedUsers(String text, List<User> users) {
        return users.stream()
                .filter(user -> containsIgnoreCase(text, user.getName()) || containsIgnoreCase(text, user.getUsername()))
                .sorted(Comparator.comparingInt((User user) -> displayUser(user).length()).reversed())
                .toList();
    }

    private User resolveUserFromMarker(String text, List<User> users, String... markers) {
        String candidate = extractNamedValue(text, markers);
        return resolveUserFromSegment(candidate, users);
    }

    private User resolveUserFromSegment(String candidate, List<User> users) {
        if (isBlank(candidate)) return null;
        String normalized = sanitizeExtractedValue(candidate);
        return users.stream()
                .filter(user -> normalized.equalsIgnoreCase(String.valueOf(user.getName()).trim())
                        || normalized.equalsIgnoreCase(String.valueOf(user.getUsername()).trim()))
                .findFirst()
                .orElseGet(() -> users.stream()
                        .filter(user -> containsIgnoreCase(normalized, user.getName()) || containsIgnoreCase(normalized, user.getUsername()))
                        .findFirst()
                        .orElse(null));
    }

    private LinkedHashSet<String> resolveResearchCoreMembers(String text,
                                                             UserPrincipal currentUser,
                                                             List<User> users,
                                                             User host,
                                                             User chiefEngineer) {
        LinkedHashSet<String> memberIds = new LinkedHashSet<>();
        String explicitSegment = extractNamedValue(text, "核心成员", "成员包括", "成员是");
        List<User> matched = isBlank(explicitSegment) ? matchedUsers(text, users) : matchedUsers(explicitSegment, users);
        for (User user : matched) {
            if (!Objects.equals(user.getUserId(), currentUser.getId())) {
                memberIds.add(user.getUserId());
            }
        }
        if (memberIds.isEmpty() && host != null && !Objects.equals(host.getUserId(), currentUser.getId())) {
            memberIds.add(host.getUserId());
        }
        if (memberIds.isEmpty() && chiefEngineer != null && !Objects.equals(chiefEngineer.getUserId(), currentUser.getId())) {
            memberIds.add(chiefEngineer.getUserId());
        }
        return memberIds;
    }

    private Map<String, Object> buildResearchTransitionFlags(SysProject project, String text) {
        Map<String, Object> flags = new LinkedHashMap<>();
        ResearchStatus current = project.getResearchStatus() == null ? ResearchStatus.INIT : project.getResearchStatus();
        switch (current) {
            case BLUEPRINT -> {
                flags.put("blueprintExists", containsAny(text, "蓝图已存在", "蓝图已完成", "已有蓝图"));
                flags.put("smallGroupAllConfirmed", containsAny(text, "小群成员已确认", "成员已确认", "确认完成"));
            }
            case EXPANSION -> {
                flags.put("taskPlanDefined", containsAny(text, "任务规划已完成", "任务计划已完成", "任务规划完成"));
                flags.put("researchTasksAssigned", containsAny(text, "研究任务已分配", "任务已分配", "任务分配完成"));
                flags.put("votePassRate", extractPercentage(text));
                flags.put("votePassThreshold", containsAny(text, "三分之二", "2/3") ? 0.67d : null);
            }
            case DESIGN -> {
                flags.put("architectureDefined", containsAny(text, "架构已完成", "架构已定义"));
                flags.put("techRouteDefined", containsAny(text, "技术路线已完成", "技术路线已定义"));
                flags.put("taskBreakdownComplete", containsAny(text, "任务分解已完成", "任务拆分完成"));
            }
            case EXECUTION -> {
                flags.put("allModulesCompleted", containsAny(text, "全部模块已完成", "模块已完成"));
                flags.put("integrationSuccess", containsAny(text, "集成成功", "集成已成功"));
                flags.put("currentVersionStable", containsAny(text, "当前版本稳定", "版本稳定"));
                flags.put("majorTasksCompleted", containsAny(text, "主要任务已完成", "重大任务已完成"));
            }
            case EVALUATION -> {
                flags.put("evaluationCompleted", containsAny(text, "评测已完成", "评审已完成", "评测完成"));
                if (containsAny(text, "accepted", "通过", "接收")) {
                    flags.put("evaluationResult", "accepted");
                } else if (containsAny(text, "usable", "可用")) {
                    flags.put("evaluationResult", "usable");
                }
            }
            default -> {
            }
        }
        return flags;
    }

    private List<String> missingResearchFlags(SysProject project, Map<String, Object> flags) {
        ResearchStatus current = project.getResearchStatus() == null ? ResearchStatus.INIT : project.getResearchStatus();
        List<String> missing = new ArrayList<>();
        switch (current) {
            case BLUEPRINT -> {
                if (!booleanValue(flags.get("blueprintExists"))) missing.add("蓝图已存在/已完成");
                if (!booleanValue(flags.get("smallGroupAllConfirmed"))) missing.add("小群成员全部确认");
            }
            case EXPANSION -> {
                boolean hostPath = booleanValue(flags.get("taskPlanDefined")) && booleanValue(flags.get("researchTasksAssigned"));
                boolean votePath = flags.get("votePassRate") != null;
                if (!hostPath && !votePath) missing.add("任务规划+任务分配完成，或投票通过率");
            }
            case DESIGN -> {
                if (!booleanValue(flags.get("architectureDefined"))) missing.add("架构已完成");
                if (!booleanValue(flags.get("techRouteDefined"))) missing.add("技术路线已完成");
                if (!booleanValue(flags.get("taskBreakdownComplete"))) missing.add("任务分解已完成");
            }
            case EXECUTION -> {
                boolean modeA = booleanValue(flags.get("allModulesCompleted")) && booleanValue(flags.get("integrationSuccess"));
                boolean modeB = booleanValue(flags.get("currentVersionStable")) && booleanValue(flags.get("majorTasksCompleted"));
                if (!modeA && !modeB) missing.add("全部模块完成+集成成功，或当前版本稳定+主要任务完成");
            }
            case EVALUATION -> {
                if (!booleanValue(flags.get("evaluationCompleted"))) missing.add("评测已完成");
                if (isBlank(stringValue(flags.get("evaluationResult")))) missing.add("评测结果 accepted/usable");
            }
            default -> {
            }
        }
        return missing;
    }

    private ProjectStatus nextProjectStatus(SysProject project) {
        ProjectStatus current = project.getProjectStatus();
        if (current == null) return ProjectStatus.INITIATED;
        return switch (current) {
            case INITIATED -> ProjectStatus.IMPLEMENTING;
            case IMPLEMENTING -> ProjectStatus.SETTLEMENT;
            case SETTLEMENT -> ProjectStatus.COMPLETED;
            default -> null;
        };
    }

    private ProductStatus nextProductStatus(SysProject project) {
        ProductStatus current = project.getProductStatus() == null ? ProductStatus.IDEA : project.getProductStatus();
        return switch (current) {
            case IDEA -> ProductStatus.PROMOTION;
            case PROMOTION -> ProductStatus.DEMO_EXECUTION;
            case DEMO_EXECUTION -> ProductStatus.MEETING_DECISION;
            default -> null;
        };
    }

    private ResearchStatus nextResearchStatus(SysProject project) {
        ResearchStatus current = project.getResearchStatus() == null ? ResearchStatus.INIT : project.getResearchStatus();
        return switch (current) {
            case INIT -> ResearchStatus.BLUEPRINT;
            case BLUEPRINT -> ResearchStatus.EXPANSION;
            case EXPANSION -> ResearchStatus.DESIGN;
            case DESIGN -> ResearchStatus.EXECUTION;
            case EXECUTION -> ResearchStatus.EVALUATION;
            case EVALUATION -> ResearchStatus.ARCHIVE;
            default -> null;
        };
    }

    private String extractNamedValue(String text, String... markers) {
        for (String marker : markers) {
            String regex = Pattern.quote(marker) + "(?:是|为|叫)?[：:\\s]*([^，。,；;]+)";
            String matched = extractByPattern(text, regex);
            if (!isBlank(matched)) {
                return sanitizeExtractedValue(matched);
            }
        }
        return null;
    }

    private String extractByPattern(String text, String regex) {
        if (isBlank(text)) return null;
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        if (!matcher.find()) return null;
        return sanitizeExtractedValue(matcher.group(1));
    }

    private String sanitizeExtractedValue(String rawValue) {
        if (isBlank(rawValue)) return null;
        String value = rawValue.trim();
        for (String stopToken : STOP_TOKENS) {
            int index = value.indexOf(stopToken);
            if (index > 0) {
                value = value.substring(0, index).trim();
            }
        }
        return value.replaceAll("^[：:]+", "").trim();
    }

    private BigDecimal extractMoney(String text) {
        if (isBlank(text)) return null;
        Matcher matcher = MONEY_PATTERN.matcher(text);
        if (!matcher.find()) return null;
        BigDecimal value = new BigDecimal(matcher.group(1));
        String unit = matcher.group(2);
        if ("亿".equals(unit)) return value.multiply(BigDecimal.valueOf(100000000L));
        if ("万".equals(unit)) return value.multiply(BigDecimal.valueOf(10000L));
        if ("千".equals(unit)) return value.multiply(BigDecimal.valueOf(1000L));
        return value;
    }

    private Double extractPercentage(String text) {
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)%|([0-9]+(?:\\.[0-9]+)?)").matcher(String.valueOf(text));
        if (!matcher.find()) return null;
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (value == null) return null;
        double parsed = Double.parseDouble(value);
        return parsed > 1 ? parsed / 100d : parsed;
    }

    private ProjectType extractProjectType(String text) {
        if (containsAny(text, "军工")) return ProjectType.MILITARY;
        if (containsAny(text, "医药", "医疗")) return ProjectType.MEDICAL;
        if (containsAny(text, "工业")) return ProjectType.INDUSTRIAL;
        if (containsAny(text, "群体智能", "swarm")) return ProjectType.SWARM_INTEL;
        if (containsAny(text, "ai for science", "ai科学", "aiforscience")) return ProjectType.AI_FOR_SCIENCE;
        if (containsAny(text, "自用", "self")) return ProjectType.SELF_USE;
        return null;
    }

    private String displayCurrentUser(UserPrincipal currentUser) {
        return defaultText(currentUser.getName(), currentUser.getUsername()) + " [ERP / " + defaultText(currentUser.getUsername(), currentUser.getId()) + " / " + currentUser.getId() + "]";
    }

    private String displayUser(User user) {
        if (user == null) return null;
        return defaultText(user.getName(), user.getUsername()) + " [ERP / " + defaultText(user.getUsername(), user.getUserId()) + " / " + user.getUserId() + "]";
    }

    private User findUserById(List<User> users, String userId) {
        return users.stream().filter(user -> Objects.equals(user.getUserId(), userId)).findFirst().orElse(null);
    }

    private boolean containsAny(String text, String... keywords) {
        String source = String.valueOf(text).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (source.contains(String.valueOf(keyword).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String part) {
        if (isBlank(text) || isBlank(part)) return false;
        return text.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }

    private String normalizeCommandText(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String defaultText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return null;
    }

    private String requiredString(Map<String, Object> payload, String key, String message) {
        String value = stringValue(payload.get(key));
        if (isBlank(value)) throw new BusinessException(message);
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return false;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Double doubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        String text = String.valueOf(value).trim();
        if (text.isBlank()) return null;
        return Double.parseDouble(text);
    }

    private BigDecimal decimalFromPayload(Map<String, Object> payload, String key, BigDecimal defaultValue) {
        Object value = payload.get(key);
        if (value == null) return defaultValue;
        String text = String.valueOf(value).trim();
        if (text.isBlank()) return defaultValue;
        return new BigDecimal(text);
    }

    private List<String> stringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(item -> !item.isBlank()).toList();
        }
        return List.of(String.valueOf(value));
    }
}
