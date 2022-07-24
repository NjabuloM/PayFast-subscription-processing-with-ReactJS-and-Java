package dev.njabulo.spaza.shop.persistence.dto;

import dev.njabulo.spaza.shop.common.FrequencyType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubscriptionDTO {
    private LocalDateTime createDate;
    private String emailAddress;
    private String firstName;
    private String lastName;
    private String reference;
    private String payFastToken;
    private long magazineId;
    private FrequencyType paymentFrequency;
}
