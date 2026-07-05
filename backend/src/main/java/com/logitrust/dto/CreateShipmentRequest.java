package com.logitrust.dto;

import com.logitrust.domain.ProductCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateShipmentRequest(
        @NotBlank String originLabel,
        @NotBlank String destinationLabel,
        @Email String destinationPartyEmail,
        @NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotBlank String serialNumber,
            @NotBlank String productName,
            @NotNull ProductCategory productCategory) {
    }
}
