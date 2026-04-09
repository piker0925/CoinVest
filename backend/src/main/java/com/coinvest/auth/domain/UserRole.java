package com.coinvest.auth.domain;

public enum UserRole {
    USER, ADMIN,
    /** 시스템 봇 전용. 로그인 불가 계정. */
    BOT
}
