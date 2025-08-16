package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiAdminActionRequest {

    private Long paymentId;
    private String action; // APPROVE or REJECT
    private String notes;
}
