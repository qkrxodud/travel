package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.exploration.domain.explorer.Account;
import com.kobi.territory.exploration.domain.explorer.AccountIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** account 테이블 ↔ Account(Explorer 애그리거트의 자식, 1:1). 연결 후 바뀌지 않는다(V4). */
@Entity
@Table(name = "account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AccountJpaEntity from(String explorerId, Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.explorerId = explorerId;
        entity.provider = account.identity().provider();
        entity.subject = account.identity().subject();
        entity.email = account.identity().email();
        entity.createdAt = account.linkedAt();
        return entity;
    }

    public String explorerId() {
        return explorerId;
    }

    public Account toDomain() {
        return new Account(new AccountIdentity(provider, subject, email), createdAt);
    }
}
