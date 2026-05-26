package com.tenco.blog.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 결제 내역 Repository (포트원 V2)
 */
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    // [무엇을] paymentId 로 결제 내역 1건을 "가져온다" (없으면 빈 Optional 이 옴)
    // [왜 쓰나] 결제 검증(complete) 단계에서 사용한다.
    //          "이 결제 건을 우리가 이미 처리(포인트 지급)했는가?" 를 확인하기 위함이다.
    //          이미 저장돼 있으면 → 중복 지급이므로 막는다. (같은 결제로 두 번 충전 방지)
    //          (추후 환불처럼 그 결제 레코드 자체를 꺼내서 수정해야 할 때도 이 메서드로 가져온다)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<Payment> findByPaymentId(@Param("paymentId") String paymentId);

    // [무엇을] 같은 paymentId 가 DB 에 "있는지 없는지"만 true/false 로 확인한다
    //          (객체를 안 가져오고 개수만 세므로 위 findBy 보다 가볍다)
    // [왜 쓰나] 결제 요청 생성(prepare) 단계에서 새 paymentId 를 만들 때 사용한다.
    //          혹시 이미 쓰인 번호면 안 되므로, "이미 있으면 true" → 번호를 다시 만들게 한다.
    //          즉 주문번호(paymentId)가 절대 겹치지 않도록 유일성을 보장하는 용도다.
    @Query("SELECT COUNT(p) > 0 FROM Payment p WHERE p.paymentId = :paymentId")
    boolean existsByPaymentId(@Param("paymentId") String paymentId);
}
