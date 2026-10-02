package com.kobi.territory.archfixture.progression.application;

import com.kobi.territory.archfixture.exploration.api.Leak;
import com.kobi.territory.archfixture.exploration.api.event.Fine;

/** ArchUnit 회귀 픽스처: 다른 컨텍스트의 api 루트(Leak)를 참조 — 허용 목록 규칙이 잡아야 한다. Fine 은 허용. */
public class Consumer {
    public Leak leak;
    public Fine fine;
}
