package com.wms.inventory.controller;

import com.wms.inventory.dto.ApiResponse;
import com.wms.inventory.dto.SkuCreateRequest;
import com.wms.inventory.entity.Sku;
import com.wms.inventory.service.SkuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sku")
@RequiredArgsConstructor
public class SkuController {

    private final SkuService skuService;

    @PostMapping
    public ApiResponse<Sku> create(@RequestBody @Valid SkuCreateRequest request) {
        return ApiResponse.success(skuService.create(request));
    }

    @GetMapping("/{skuCode}")
    public ApiResponse<Sku> getByCode(@PathVariable String skuCode) {
        return ApiResponse.success(skuService.getByCode(skuCode));
    }

    @GetMapping("/list")
    public ApiResponse<List<Sku>> listAll() {
        return ApiResponse.success(skuService.listAll());
    }

    @GetMapping("/page")
    public ApiResponse<Page<Sku>> page(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(skuService.page(pageable));
    }
}
