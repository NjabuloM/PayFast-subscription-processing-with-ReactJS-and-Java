package dev.njabulo.spaza.shop.config;

import org.payment.gateway.PayFastItnValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PayFastProperties.class)
public class PayFastConfiguration {

    @Bean
    public PayFastItnValidator payFastItnValidator(PayFastProperties properties) {
        return new PayFastItnValidator(properties.merchantId(), properties.passPhrase(), properties.host(),
                properties.verifySourceIp());
    }
}
