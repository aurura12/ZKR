package com.smartlab.erp.entity;

public enum WorkOrderStatus {
    PENDING,      // 待处理（已发起）
    IN_PROGRESS,  // 进行中（执行人接单）
    COMPLETED,    // 已完成（执行人提交完成）
    CLOSED,       // 已关闭（组长确认完成）
    CANCELLED     // 已取消（组长取消工单）
}
