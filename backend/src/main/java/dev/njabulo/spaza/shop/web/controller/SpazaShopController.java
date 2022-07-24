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

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        subscription.setReference(UUID.randomUUID().toString());
        Subscription savedSubscription = shopService.addSubscription(subscription);

        SubscriptionDTO subscriptionDTO = modelMapper.map(savedSubscription, SubscriptionDTO.class);
        return ResponseEntity.accepted().body(subscriptionDTO);
    }

    @PostMapping("/newsstand/checkout")
    public ResponseEntity<Object> checkout(@RequestBody SubscriptionDTO subscriptionDto) {
        Subscription subscription = modelMapper.map(subscriptionDto, Subscription.class);
        //Get product detail
        Magazine magazine = shopService.getMagazineById(subscription.getMagazineId());
        if (magazine == null) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                    .body("{\"Error\": \"Request could not be processed. Please contact Admin\"}");
        }

        List<HtmlFormField> hiddenHTMLFormFields = shopService.generateHiddenHTMLForm(subscription, magazine);

        return ResponseEntity.accepted().body(hiddenHTMLFormFields);
    }

    @PostMapping(value = "/payment/notification", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Object> payFastNotifyCallback(@RequestParam Map<String, String> responseObject) {
        //Using responseObject, do security checks before updating own records
        //TODO: Verify signature
        //TODO: Validate notification source, i.e. from valid PayFast domain
        //TODO: Check the expected amount and the actual amounts match
        //TODO: Confirm order details with received data by making a server call to PayFast

        logger.debug("Response from call-back: {}", responseObject);

        //...thereafter, update own records accordingly

        //Respond with a 200 to prevent a retry to send the same notification
        return ResponseEntity.ok().build();
    }

}
