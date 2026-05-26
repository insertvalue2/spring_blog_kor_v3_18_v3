package com.tenco.blog.payment;

import com.tenco.blog.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

/**
 * 결제 내역 엔티티 (포트원 V2 PG 결제)
 *
 * 포인트 충전 시 발생한 실제 결제 1건을 기록한다.
 *  - paymentId: 우리 서버가 발급한 결제 건 식별자 (V2의 핵심 키, 중복 지급 방지)
 *  - pgTxId:    PG사 거래 번호 (포트원 조회 응답에서 받음, 참고/추적용)
 *  - amount:    결제 금액 = 적립 포인트
 *  - status:    PAID(완료) 등 포트원 결제 상태
 */
@Data
@NoArgsConstructor
@Table(name = "payment_tb")
@Entity
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // 우리 서버가 발급한 결제 건 식별자 (V2 paymentId) — 유니크 (중복 지급 방지)
    @Column(unique = true, nullable = false)
    private String paymentId;

    // PG사 거래 번호 (포트원 조회 응답의 pgTxId, 없을 수 있어 nullable)
    // 제 PG사가 매긴 고유 거래번호
    private String pgTxId;

    // 결제한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 결제 금액 (포인트)
    @Column(nullable = false)
    private Integer amount;

    //    PAID                    결제 완료
    //    FAILED                  결제 실패
    //    CANCELLED               전액 취소
    // 결제 상태 (PAID: 결제 완료)
    @Column(nullable = false)
    private String status;

    @CreationTimestamp
    private Timestamp createdAt;

    @Builder
    public Payment(String paymentId, String pgTxId, User user, Integer amount, String status) {
        this.paymentId = paymentId;
        this.pgTxId = pgTxId;
        this.user = user;
        this.amount = amount;
        this.status = status;
    }
}
