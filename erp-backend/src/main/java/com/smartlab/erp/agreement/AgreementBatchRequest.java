package com.smartlab.erp.agreement;

import lombok.Data;

import java.util.List;

@Data
public class AgreementBatchRequest {
    private List<AgreementType> types;
}
