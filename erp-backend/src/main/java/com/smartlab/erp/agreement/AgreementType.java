package com.smartlab.erp.agreement;

public enum AgreementType {
    INTERNET("互联网实习生协议"),
    GENERAL("实习生协议"),
    PROOF("实习证明");

    private final String displayName;

    AgreementType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
