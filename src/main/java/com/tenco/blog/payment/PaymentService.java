package com.tenco.blog.payment;

import com.tenco.blog._core.errors.Exception400;
import com.tenco.blog._core.errors.Exception404;
import com.tenco.blog.user.User;
import com.tenco.blog.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * 결제 서비스 (포트원 V2 연동)
 *
 * 1. 결제 요청 생성 (paymentId 발급 + 프론트에 storeId/channelKey 전달)
 * 2. 결제 검증 및 포인트 충전 (핵심 비즈니스 로직)
 *
 * V1 대비 차이점
 *  - 토큰 발급 단계 없음: Authorization 헤더에 "PortOne {API_SECRET}" 직접 사용
 *  - 결제 조회: GET https://api.portone.io/payments/{paymentId}
 *  - 성공 상태값: "PAID", 결제 금액: amount.total
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    @Value("${portone.api-secret}")
    private String apiSecret;

    /**
     * 1. 결제 요청 생성 (Prepare)
     *
     * 프론트엔드가 결제창을 띄우기 전에, 서버로부터 고유한 결제 건 식별자(paymentId) 를 발급받는다.
     *  - 중복 결제 방지 (유니크 paymentId 보장)
     *  - 위변조 방지 토대 (paymentId 는 서버가 생성)
     *
     * @param userId 요청한 사용자 ID
     * @param amount 충전하려는 금액
     * @return paymentId + 결제 금액 + storeId + channelKey
     */
    @Transactional
    public PaymentResponse.PrepareDTO 결제요청생성(Integer userId, Integer amount) {
        // 1. 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new Exception404("사용자를 찾을 수 없습니다");
        }

        // 2. paymentId 생성 (중복 시 재생성)
        String paymentId = generatePaymentId(userId);
        while (paymentRepository.existsByPaymentId(paymentId)) {
            paymentId = generatePaymentId(userId);
        }

        // 3. DTO 반환 (storeId, channelKey 는 프론트 결제창 호출용 공개값)
        return new PaymentResponse.PrepareDTO(paymentId, amount, storeId, channelKey);
    }

    /**
     * 2. 결제 검증 및 포인트 충전 (Complete)
     *
     * 포트원 결제 완료 후, 프론트가 보낸 paymentId 로 포트원 서버에 직접 조회해 검증하고
     * 검증 통과 시에만 포인트를 지급한다.
     *
     * @param userId    사용자 ID
     * @param paymentId 포트원 V2 결제 건 식별자 (서버가 발급해 프론트로 내려준 값)
     * @return 충전 금액 + 충전 후 현재 포인트
     */
    @Transactional
    public PaymentResponse.CompleteDTO 결제검증및충전(Integer userId, String paymentId) {
        // [1] 사전 검증 (DB)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 이미 처리된 결제인지 확인 (같은 paymentId 중복 지급 방지)
        if (paymentRepository.findByPaymentId(paymentId).isPresent()) {
            throw new Exception400("이미 처리된 결제입니다");
        }

        // [2] 외부 통신 (포트원 서버에 실제 결제 내역 조회)
        PaymentResponse.PortOnePaymentV2 payment = 포트원결제조회(paymentId);

        // [3] 데이터 무결성 검증
        if (payment.getStatus() == null || !"PAID".equals(payment.getStatus())) {
            throw new Exception400("결제가 완료되지 않았습니다. (상태: " + payment.getStatus() + ")");
        }
        if (payment.getAmount() == null || payment.getAmount().getTotal() == null) {
            throw new Exception400("결제 금액 정보를 확인할 수 없습니다.");
        }

        // [4] 비즈니스 로직 (포인트 충전 + 결제 내역 저장)
        // 금액은 프론트가 보낸 값이 아니라 포트원이 알려준 실제 결제 금액을 사용
        Integer amount = payment.getAmount().getTotal();

        // 4-1. 포인트 충전 (더티 체킹으로 자동 UPDATE)
        user.chargePoint(amount);

        // 4-2. 결제 내역 저장 (영수증)
        Payment record = Payment.builder()
                .paymentId(paymentId)
                .pgTxId(payment.getPgTxId())
                .user(user)
                .amount(amount)
                .status("PAID")
                .build();
        paymentRepository.save(record);

        log.info("결제 및 포인트 충전 완료: userId={}, paymentId={}, amount={}", userId, paymentId, amount);

        // [5] 결과 반환
        return new PaymentResponse.CompleteDTO(amount, user.getPoint());
    }

    // =================================================================
    //  Private Helper Methods
    // =================================================================

    /**
     * 결제 건 식별자 생성: point_{userId}_{timestamp}_{uuid8}
     */
    private String generatePaymentId(Integer userId) {
        return "point_" + userId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 포트원 V2 결제 단건 조회
     *  - 토큰 발급 단계 없이 Authorization 헤더에 "PortOne {API_SECRET}" 직접 전달
     *  - 카카오 액세스 토큰 발급 메서드와 동일한 RestTemplate.exchange 패턴
     *
     */
    private PaymentResponse.PortOnePaymentV2 포트원결제조회(String paymentId) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        // V2 인증 : Authorization 헤더에 "PortOne {API_SECRET}" 직접 전달
        headers.add("Authorization", "PortOne " + apiSecret);

        // GET 요청이라 바디는 없음. 헤더만 담아 HTTP 요청 메세지 구축
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // HTTP 요청 후 응답
        ResponseEntity<PaymentResponse.PortOnePaymentV2> response = restTemplate.exchange(
                "https://api.portone.io/payments/" + paymentId,
                HttpMethod.GET,
                request,
                PaymentResponse.PortOnePaymentV2.class
        );

        return response.getBody();
    }
}
