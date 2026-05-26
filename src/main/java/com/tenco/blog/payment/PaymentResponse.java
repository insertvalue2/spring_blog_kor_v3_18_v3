package com.tenco.blog.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

public class PaymentResponse {

    // prepare 응답 — 프론트가 V2 결제창 호출에 사용
    @Data
    public static class PrepareDTO {
        private String paymentId;   // 서버가 발급한 결제 건 식별자
        private Integer amount;     // 결제(충전) 금액
        private String storeId;     // 포트원 상점 ID (프론트 공개값)
        private String channelKey;  // 포트원 채널 키 (프론트 공개값)

        public PrepareDTO(String paymentId, Integer amount, String storeId, String channelKey) {
            this.paymentId = paymentId;
            this.amount = amount;
            this.storeId = storeId;
            this.channelKey = channelKey;
        }
    }

    // complete 응답 — 화면 alert 에 띄울 최소 데이터
    @Data
    public static class CompleteDTO {
        private Integer amount;        // 충전 금액
        private Integer currentPoint;  // 충전 후 현재 잔액

        public CompleteDTO(Integer amount, Integer currentPoint) {
            this.amount = amount;
            this.currentPoint = currentPoint;
        }
    }

    /**
     * 포트원 V2 결제 단건 조회 응답 매핑
     *  - V2 응답은 camelCase 라 별도 네이밍 전략이 필요 없음
     *  - 응답 필드가 매우 많으므로 필요한 필드만 매핑하고 나머지는 무시
     */
    @Data
    // JSON 에 필드가 있고 자바 클래스 필드 선언이 없다면 그냥 무시 하라
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PortOnePaymentV2 {
        private String status;   // READY / PAID / FAILED / CANCELLED ...
        private String id;       // paymentId
        private String pgTxId;   // PG 거래 번호
        private Amount amount;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Amount {
            private Integer total;    // 총 결제 금액 (충전 포인트)
            private Integer taxFree;
            private Integer vat;
        }
    }
}
