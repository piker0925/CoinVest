package com.coinvest.trading.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.BalanceRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final BalanceRepository balanceRepository;

    private static final BigDecimal INITIAL_KRW = new BigDecimal("100000000"); // 1억
    private static final BigDecimal INITIAL_USD = new BigDecimal("10000");     // 1만

    /**
     * 신규 사용자를 위한 가상 계좌 및 초기 잔고 생성
     */
    @Transactional
    public void createDefaultAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        // 1. 가상 계좌 생성
        VirtualAccount account = VirtualAccount.builder()
                .user(user)
                .accountNumber("VA-" + userId + "-" + System.currentTimeMillis() % 1000)
                .build();
        VirtualAccount savedAccount = virtualAccountRepository.save(account);

        // 2. KRW 잔고 생성
        Balance krwBalance = Balance.builder()
                .account(savedAccount)
                .currency(Currency.KRW)
                .available(INITIAL_KRW)
                .locked(BigDecimal.ZERO)
                .unsettled(BigDecimal.ZERO)
                .build();
        balanceRepository.save(krwBalance);

        // 3. USD 잔고 생성
        Balance usdBalance = Balance.builder()
                .account(savedAccount)
                .currency(Currency.USD)
                .available(INITIAL_USD)
                .locked(BigDecimal.ZERO)
                .unsettled(BigDecimal.ZERO)
                .build();
        balanceRepository.save(usdBalance);
    }
}
