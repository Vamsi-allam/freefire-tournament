package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrizeDistributionResponse {

    // Total pool calculated from confirmed registrations × entryFee
    private BigDecimal totalPrizePool;
    // Actual sum of current distribution preview (what will be paid now)
    private BigDecimal toBeDistributed;
    // Optional convenience: show undistributed remainder (total - toBeDistributed)
    private BigDecimal undistributedRemainder;
    private Integer winnersCount;
    private List<PrizeDistributionDetail> distributions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PrizeDistributionDetail {

        private Long userId;
        private String playerName;
        private String teamName;
        private Integer position;
        private Integer kills;
        private BigDecimal prizeAmount;
        private Boolean alreadyCredited;
    }
}
