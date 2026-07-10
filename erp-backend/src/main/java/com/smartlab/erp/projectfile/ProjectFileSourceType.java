package com.smartlab.erp.projectfile;

public enum ProjectFileSourceType {
    PROJECT_ASSET("项目资料"),
    EXECUTION_FILE("执行文件"),
    PROJECT_EXPENSE_FILE("费用报销"),
    FINANCE_EXPENSE_SUBMISSION("财务报销"),
    PROJECT_COST_ADJUSTMENT("成本调整"),
    UPLOADED_FILE("上传文件");

    private final String defaultFolderName;

    ProjectFileSourceType(String defaultFolderName) {
        this.defaultFolderName = defaultFolderName;
    }

    public String getDefaultFolderName() {
        return defaultFolderName;
    }
}
