package com.tenco.blog.payment;

import com.tenco.blog._core.errors.Exception400;
import lombok.Data;

public class PaymentRequest {

    /**
     * 결제 요청 생성 DTO (prepare 단계)
     */
    @Data
    public static class PrepareDTO {
        private Integer amount;  // 충전할 포인트

        public void validate() {
            if (amount == null || amount <= 0) {
                throw new Exception400("충전할 포인트는 0보다 커야 합니다");
            }
            if (amount < 100) {
                throw new Exception400("최소 충전 금액은 100포인트입니다");
            }
            if (amount > 100000) {
                throw new Exception400("최대 충전 금액은 100,000포인트입니다");
            }
        }
    }

    /**
     * 결제 완료(검증) DTO (complete 단계)
     *  - 프론트엔드가 포트원 V2 결제 완료 후 paymentId 를 보내옴
     */
    @Data
    public static class CompleteDTO {
        private String paymentId;  // 포트원 V2 결제 건 식별자

        public void validate() {
            if (paymentId == null || paymentId.trim().isEmpty()) {
                throw new Exception400("결제 건 식별자가 필요합니다");
            }
        }
    }
}
