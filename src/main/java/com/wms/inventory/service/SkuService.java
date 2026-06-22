package com.wms.inventory.service;

import com.wms.inventory.dto.SkuCreateRequest;
import com.wms.inventory.entity.Sku;
import com.wms.inventory.exception.InventoryException;
import com.wms.inventory.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkuService {

    private final SkuRepository skuRepository;

    @Transactional
    public Sku create(SkuCreateRequest request) {
        if (skuRepository.existsBySkuCode(request.getSkuCode())) {
            throw new InventoryException("货品编码已存在: " + request.getSkuCode());
        }
        Sku sku = new Sku();
        sku.setSkuCode(request.getSkuCode());
        sku.setSkuName(request.getSkuName());
        sku.setUnit(request.getUnit() != null ? request.getUnit() : "件");
        sku.setShelfLifeDays(request.getShelfLifeDays());
        sku.setDescription(request.getDescription());
        sku = skuRepository.save(sku);
        log.info("创建货品成功: skuCode={}, skuName={}", sku.getSkuCode(), sku.getSkuName());
        return sku;
    }

    @Transactional(readOnly = true)
    public Sku getByCode(String skuCode) {
        return skuRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new InventoryException("货品不存在: " + skuCode));
    }

    @Transactional(readOnly = true)
    public List<Sku> listAll() {
        return skuRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Sku> page(Pageable pageable) {
        return skuRepository.findAll(pageable);
    }
}
