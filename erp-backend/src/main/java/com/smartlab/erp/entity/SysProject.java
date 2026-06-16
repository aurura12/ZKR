package com.smartlab.erp.entity;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectType;
import com.smartlab.erp.entity.ProductStatus;
import com.smartlab.erp.entity.ResearchStatus;
import com.smartlab.erp.enums.ProjectTierEnum;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * [Smart Lab v3.0] 系统统一项目表
 * 支持三流并行架构：项目流 (PROJECT) / 产品流 (PRODUCT) / 科研流 (RESEARCH)
 */
@Entity
@Table(name = "sys_project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysProject {

    // 1. ID 改为 String 类型
    @Id
    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // 3. 项目类型 (行业分类)
    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 20)
    private ProjectType projectType;

    // 三流并行架构 - 流程类型 (PROJECT/PRODUCT/RESEARCH)
    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 20)
    private FlowType flowType;

    // 4. 状态 (根据flowType不同，使用不同的状态枚举)
    @Enumerated(EnumType.STRING)
    @Column(name = "project_status", length = 20)
    @Builder.Default
    private ProjectStatus projectStatus = ProjectStatus.LEAD;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_status", length = 20)
    @Builder.Default
    private ProductStatus productStatus = ProductStatus.IDEA;

    @Enumerated(EnumType.STRING)
    @Column(name = "research_status", length = 30)
    private ResearchStatus researchStatus;

    // 4. 负责人
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User manager;

    // 5. 财务字段
    @Column(precision = 15, scale = 2)
    private BigDecimal budget;

    // 预计项目收入金额
    @Column(name = "estimated_revenue", precision = 19, scale = 4)
    private BigDecimal estimatedRevenue;

    // 可行性报告文件地址
    @Column(name = "feasibility_report_url")
    private String feasibilityReportUrl;

    // 项目评级
    @Enumerated(EnumType.STRING)
    @Column(name = "project_tier", length = 10)
    private ProjectTierEnum projectTier;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    @Transient
    private Boolean costControlEnabled;

    // 6. 产品研发专用字段 (可空)
    @Column(name = "tech_stack")
    private String techStack;

    @Column(name = "repo_url")
    private String repoUrl;

    @Column(name = "deploy_url")
    private String deployUrl;

    // 7. OCR 结算相关字段
    @Column(name = "ocr_timestamp")
    private LocalDateTime ocrTimestamp;

    @Column(name = "ocr_work_hours")
    private Integer ocrWorkHours;

    @Column(name = "settlement_proof_url")
    private String settlementProofUrl;

    // 8. 时间字段
    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // 自动生成 UUID 和 时间
    @PrePersist
    protected void onCreate() {
        if (this.projectId == null) {
            this.projectId = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 根据流程类型获取当前状态
     * @return 当前状态的字符串表示
     */
    public String getCurrentStatus() {
        return switch (flowType) {
            case PROJECT -> projectStatus != null ? projectStatus.name() : ProjectStatus.LEAD.name();
            case PRODUCT -> productStatus != null ? productStatus.name() : ProductStatus.IDEA.name();
            case RESEARCH -> researchStatus != null ? researchStatus.name() : ResearchStatus.INIT.name();
        };
    }

    /**
     * 获取状态中文名称
     * @return 状态的中文名称
     */
    public String getStatusDisplayName() {
        return switch (flowType) {
            case PROJECT -> projectStatus != null ? projectStatus.getStageName() : ProjectStatus.LEAD.getStageName();
            case PRODUCT -> productStatus != null ? getProductStatusName(productStatus) : "创意孵化";
            case RESEARCH -> researchStatus != null ? getResearchStatusName(researchStatus) : "初探阶段";
        };
    }

    private String getProductStatusName(ProductStatus status) {
        return switch (status) {
            case IDEA -> "创意孵化";
            case PROMOTION -> "推广组队";
            case DEMO_EXECUTION -> "Demo实施";
            case MEETING_DECISION -> "虚拟会议决策";
            case TESTING -> "测试与上线";
            case LAUNCHED -> "已转化为正式项目";
            case SHELVED -> "搁置/流产";
        };
    }

    private String getResearchStatusName(ResearchStatus status) {
        return switch (status) {
            case INIT -> "发起";
            case BLUEPRINT -> "小群蓝图";
            case EXPANSION -> "大群深化";
            case DESIGN -> "实施前设计";
            case EXECUTION -> "施工执行";
            case EVALUATION -> "评测";
            case ARCHIVE -> "入库完成";
            case SHELVED -> "已搁置";
            case PROBE -> "发起";
            case DEEPENING -> "大群深化";
            case PRE_EXECUTION -> "实施前设计";
            case CONSTRUCTION -> "施工执行";
            case ARCHIVED_TO_MIDDLEWARE -> "入库完成";
        };
    }
}
