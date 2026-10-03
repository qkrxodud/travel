package com.kobi.territory.sharing.application;

import java.time.Instant;

/** 카드 PNG 한 장과 그린 시각. */
public record CardImage(byte[] png, Instant renderedAt) {}
