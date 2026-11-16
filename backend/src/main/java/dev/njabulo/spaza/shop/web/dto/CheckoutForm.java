package dev.njabulo.spaza.shop.web.dto;

import dev.njabulo.spaza.shop.common.HtmlFormField;

import java.util.List;

/**
 * What the browser needs to send the customer to PayFast: where to post, and the signed hidden fields.
 *
 * @param action the PayFast process URL (sandbox or live, set by PAYFAST_SANDBOX on the server)
 * @param fields the hidden form fields, including the signature
 */
public record CheckoutForm(String action, List<HtmlFormField> fields) {
}
