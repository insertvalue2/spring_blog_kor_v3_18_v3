package com.tenco.blog.payment;

import com.tenco.blog._core.util.Define;
import com.tenco.blog.user.User;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 결제 컨트롤러 (포트원 V2 PG 연동 API)
 *  - POST /api/payment/prepare  : 결제 건 생성 (paymentId 발급 + storeId/channelKey 전달)
 *  - POST /api/payment/complete : 결제 검증 및 포인트 충전
 *
 * 주의: 이 프로젝트의 LoginInterceptor 는 /api/** 를 보호하지 않으므로
 *       컨트롤러에서 직접 세션(로그인 여부)을 확인한다.
 */
@RequiredArgsConstructor
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 요청 생성 API
     *  - paymentId 를 생성해 프론트로 전달, 프론트는 storeId/channelKey 와 함께 V2 결제창 호출
     */
    @PostMapping("/api/payment/prepare")
    public ResponseEntity<?> preparePayment(@RequestBody PaymentRequest.PrepareDTO reqDTO,
                                            HttpSession session) {
        reqDTO.validate();

        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        PaymentResponse.PrepareDTO prepareDTO =
                paymentService.결제요청생성(sessionUser.getId(), reqDTO.getAmount());

        // V2 프론트 SDK 가 그대로 쓰도록 camelCase 키로 응답
        return ResponseEntity.ok().body(Map.of(
                "paymentId", prepareDTO.getPaymentId(),
                "amount", prepareDTO.getAmount(),
                "storeId", prepareDTO.getStoreId(),
                "channelKey", prepareDTO.getChannelKey()
        ));
    }

    /**
     * 결제 검증 및 포인트 충전 API
     *  - 포트원 V2 API 로 결제를 검증하고, 성공 시 포인트 충전 + 세션 동기화
     */
    @PostMapping("/api/payment/complete")
    public ResponseEntity<?> completePayment(@RequestBody PaymentRequest.CompleteDTO reqDTO,
                                             HttpSession session) {
        reqDTO.validate();

        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        PaymentResponse.CompleteDTO completeDTO =
                paymentService.결제검증및충전(sessionUser.getId(), reqDTO.getPaymentId());

        // 세션의 사용자 포인트 즉시 갱신 (충전 반영)
        sessionUser.setPoint(completeDTO.getCurrentPoint());
        session.setAttribute(Define.SESSION_USER, sessionUser);

        return ResponseEntity.ok().body(completeDTO);
    }
}
