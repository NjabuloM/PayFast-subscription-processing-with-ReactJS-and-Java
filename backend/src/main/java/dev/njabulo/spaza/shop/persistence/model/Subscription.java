package dev.njabulo.spaza.shop.persistence.model;

import dev.njabulo.spaza.shop.common.FrequencyType;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "SUBSCRIPTION")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private long id;

    @Column(name = "create_date")
    @Getter
    @Setter
    private LocalDateTime createDate;

    @Column(name = "email_address")
    @Getter
    @Setter
    private String emailAddress;

    @Column(name = "first_name")
    @Getter
    @Setter
    private String firstName;

    @Column(name = "last_name")
    @Getter
    @Setter
    private String lastName;

    @Column(name = "reference")
    @Getter
    @Setter
    private String reference;

    @Column(name = "pf_token")
    @Getter
    @Setter
    private String payFastToken;

    @Column(name = "magazine_id")
    @Getter
    @Setter
    private long magazineId;

    @Column(name = "payment_frequency")
    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    private FrequencyType paymentFrequency;
}
