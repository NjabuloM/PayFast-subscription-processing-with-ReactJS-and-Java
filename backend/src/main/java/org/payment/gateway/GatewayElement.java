package org.payment.gateway;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GatewayElement {
    String label();
    int placement();

    /**
     * When true, the field takes part in the signature calculation but must never be sent to the browser
     * or posted to PayFast (e.g. the merchant passphrase).
     */
    boolean signatureOnly() default false;
}
