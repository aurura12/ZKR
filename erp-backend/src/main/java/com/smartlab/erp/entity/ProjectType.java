package com.smartlab.erp.entity;

/**
 * 项目类型枚举
 * 已更新为 5 大领域
 */
public enum ProjectType {
    BUSINESS,       // 保留旧数据兼容 (可选)

    MILITARY,       // 军工
    AI_FOR_SCIENCE, // AI 科研
    MEDICAL,        // 医药 (新增)
    INDUSTRIAL,     // 工业
    SWARM_INTEL,    // 群体智能 (新增)
    SELF_USE        // 自用
}