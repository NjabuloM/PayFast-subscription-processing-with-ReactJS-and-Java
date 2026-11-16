package dev.njabulo.spaza.shop.web.controller;

import dev.njabulo.spaza.shop.common.SubscriptionPlan;
import dev.njabulo.spaza.shop.persistence.dto.SubscriptionDTO;
import dev.njabulo.spaza.shop.persistence.model.Magazine;
import dev.njabulo.spaza.shop.persistence.model.Subscription;
import dev.njabulo.spaza.shop.persistence.service.ShopService;

import org.modelmapper.ModelMapper;
import org.payment.gateway.PayFastProviderUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600) //http://localhost:3000
@RestController
@RequestMapping("/shop")
public class SpazaShopController {
    Logger logger = LoggerFactory.getLogger(SpazaShopController.class);

    private final ShopService shopService;

    private ModelMapper modelMapper;

    public SpazaShopController(ShopService shopService, ModelMapper modelMapper) {
        this.shopService = shopService;
        this.modelMapper = modelMapper;
    }

    @GetMapping("/newsstand")
    public List<Magazine> getNewsStand() {
        return shopService.viewMagazineStand();
    }

    @GetMapping("/newsstand/magazine/{id}")
    public Magazine getMagazine(@PathVariable long id) {
        return shopService.getMagazineById(id);
    }

    /** The billing options for a magazine, with prices worked out on the server */
    @GetMapping("/newsstand/magazine/{id}/plans")
    public ResponseEntity<List<SubscriptionPlan>> getPlans(@PathVariable long id) {
        Magazine magazine = shopService.getMagazineById(id);
        if (magazine == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(shopService.getPlans(magazine));
    }

    @PostMapping("/newsstand/subscribe")
    public ResponseEntity<SubscriptionDTO> addSubscription(@RequestBody SubscriptionDTO subscriptionDto) {
        Subscription subscription = modelMapper.map(subscriptionDto, Subscription.class);
        Subscription savedSubscription = shopService.addSubscription(subscription);

        SubscriptionDTO subscriptionDTO = modelMapper.map(savedSubscription, SubscriptionDTO.class);
        return ResponseEntity.accepted().body(subscriptionDTO);
    }

    /**
     * Saves a PENDING subscription and returns the signed form the browser posts to PayFast:
     * {@code {"action": "<PayFast process URL>", "fields": [...]}}.
     */
    @PostMapping("/newsstand/checkout")
    public ResponseEntity<Object> checkout(@RequestBody SubscriptionDTO subscriptionDto) {
        if (subscriptionDto.getEmailAddress() == null || subscriptionDto.getEmailAddress().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("Error", "An email address is required"));
        }
        if (subscriptionDto.getPaymentFrequency() == null) {
            return ResponseEntity.badRequest().body(Map.of("Error", "Choose a billing option (MONTHLY or ANNUALLY)"));
        }

        //Get product detail
        Magazine magazine = shopService.getMagazineById(subscriptionDto.getMagazineId());
        if (magazine == null) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                    .body("{\"Error\": \"Request could not be processed. Please contact Admin\"}");
        }

        //The price comes from the server; the client only chooses the billing option
        SubscriptionPlan plan = SubscriptionPlan.of(magazine.getPrice(), subscriptionDto.getPaymentFrequency());

        //Save the subscription (PENDING) first, so that PayFast's notification can be matched back to it
        Subscription subscription = shopService.addSubscription(
                modelMapper.map(subscriptionDto, Subscription.class), plan);

        return ResponseEntity.accepted().body(shopService.generateCheckoutForm(subscription, magazine, plan));
    }

    /**
     * PayFast's ITN (Instant Transaction Notification) endpoint, i.e. the NOTIFY_URL. PayFast calls it after the
     * first payment, after every recurring payment, and when the subscription is cancelled.
     */
    @PostMapping(value = "/payment/notification", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Object> payFastNotifyCallback(HttpServletRequest request) throws IOException {
        Map<String, String> receivedFields = readItnFields(request);

        boolean genuine = shopService.processPaymentNotification(receivedFields, request.getRemoteAddr());

        //A 200 tells PayFast the notification was received, so it won't be sent again. Rejected (e.g. forged)
        //notifications get a 400.
        return genuine ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    /**
     * The ITN signature is computed over the fields in the order PayFast sent them. The Servlet API doesn't promise
     * to keep request parameters in that order, so the raw body is parsed instead. This only works while nothing
     * has read the request parameters yet (which would consume the body); if something has, fall back to them.
     */
    private Map<String, String> readItnFields(HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!body.isBlank()) {
            return PayFastProviderUtility.parseItnBody(body);
        }

        Map<String, String> fromParameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> fromParameters.put(key, values.length > 0 ? values[0] : ""));
        if (!fromParameters.isEmpty()) {
            logger.warn("ITN body was already consumed; falling back to request parameters, whose order is not guaranteed");
        }
        return fromParameters;
    }

}
