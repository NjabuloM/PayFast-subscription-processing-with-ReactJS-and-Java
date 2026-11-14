package dev.njabulo.spaza.shop.web.controller;

import dev.njabulo.spaza.shop.common.HtmlFormField;
import dev.njabulo.spaza.shop.persistence.dto.SubscriptionDTO;
import dev.njabulo.spaza.shop.persistence.model.Magazine;
import dev.njabulo.spaza.shop.persistence.model.Subscription;
import dev.njabulo.spaza.shop.persistence.service.ShopService;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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

    @PostMapping("/newsstand/subscribe")
    public ResponseEntity<SubscriptionDTO> addSubscription(@RequestBody SubscriptionDTO subscriptionDto) {
        Subscription subscription = modelMapper.map(subscriptionDto, Subscription.class);
        Subscription savedSubscription = shopService.addSubscription(subscription);

        SubscriptionDTO subscriptionDTO = modelMapper.map(savedSubscription, SubscriptionDTO.class);
        return ResponseEntity.accepted().body(subscriptionDTO);
    }

    @PostMapping("/newsstand/checkout")
    public ResponseEntity<Object> checkout(@RequestBody SubscriptionDTO subscriptionDto) {
        if (subscriptionDto.getEmailAddress() == null || subscriptionDto.getEmailAddress().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("Error", "An email address is required"));
        }

        //Get product detail
        Magazine magazine = shopService.getMagazineById(subscriptionDto.getMagazineId());
        if (magazine == null) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                    .body("{\"Error\": \"Request could not be processed. Please contact Admin\"}");
        }

        //Save the subscription (PENDING) first, so that PayFast's notification can be matched back to it
        Subscription subscription = shopService.addSubscription(modelMapper.map(subscriptionDto, Subscription.class));

        List<HtmlFormField> hiddenHTMLFormFields = shopService.generateHiddenHTMLForm(subscription, magazine);

        return ResponseEntity.accepted().body(hiddenHTMLFormFields);
    }

    /**
     * PayFast's ITN (Instant Transaction Notification) endpoint, i.e. the NOTIFY_URL. PayFast calls it after the
     * first payment, after every recurring payment, and when the subscription is cancelled.
     */
    @PostMapping(value = "/payment/notification", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Object> payFastNotifyCallback(HttpServletRequest request) {
        //The signature is computed over the fields in the order PayFast sent them; the servlet container keeps
        //request parameters in that order.
        Map<String, String> receivedFields = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> receivedFields.put(key, values.length > 0 ? values[0] : ""));

        boolean applied = shopService.processPaymentNotification(receivedFields, request.getRemoteAddr());

        //A 200 tells PayFast the notification was received, so it won't be sent again. Rejected (e.g. forged)
        //notifications get a 400.
        return applied ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

}
