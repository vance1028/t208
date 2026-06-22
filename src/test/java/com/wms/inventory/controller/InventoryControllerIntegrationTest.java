package com.wms.inventory.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.inventory.dto.*;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.entity.Sku;
import com.wms.inventory.repository.BatchInventoryRepository;
import com.wms.inventory.repository.InventoryTransactionRepository;
import com.wms.inventory.repository.SkuRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SkuRepository skuRepository;

    @Autowired
    private BatchInventoryRepository batchInventoryRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    private ObjectMapper objectMapper;

    private static final String SKU_API = "/api/sku";
    private static final String INV_API = "/api/inventory";

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        transactionRepository.deleteAll();
        batchInventoryRepository.deleteAll();
        skuRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
    }

    private <T> ApiResponse<T> parseResponse(String json, Class<T> dataClass) throws Exception {
        Map<?, ?> raw = objectMapper.readValue(json, new TypeReference<>() {});
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setCode((Integer) raw.get("code"));
        resp.setMessage((String) raw.get("message"));
        if (raw.get("data") != null) {
            T data = objectMapper.convertValue(raw.get("data"), dataClass);
            resp.setData(data);
        }
        return resp;
    }

    private <T> T parseData(String json, Class<T> dataClass) throws Exception {
        ApiResponse<T> resp = parseResponse(json, dataClass);
        assertEquals(200, resp.getCode(), "接口返回成功: " + resp.getMessage());
        return resp.getData();
    }

    private Sku createSku(String code, String name) throws Exception {
        SkuCreateRequest req = new SkuCreateRequest();
        req.setSkuCode(code);
        req.setSkuName(name);
        req.setShelfLifeDays(180);
        req.setUnit("件");

        MvcResult res = mockMvc.perform(post(SKU_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        return parseData(res.getResponse().getContentAsString(), Sku.class);
    }

    @Test
    @DisplayName("SKU接口: 创建+查询+列表")
    void testSkuCrud() throws Exception {
        Sku created = createSku("API-SKU-001", "接口测试货品A");
        assertNotNull(created.getId());
        assertEquals("API-SKU-001", created.getSkuCode());
        assertEquals("接口测试货品A", created.getSkuName());

        MvcResult getRes = mockMvc.perform(get(SKU_API + "/API-SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.skuName").value("接口测试货品A"))
                .andReturn();
        Sku fetched = parseData(getRes.getResponse().getContentAsString(), Sku.class);
        assertEquals(created.getId(), fetched.getId());

        createSku("API-SKU-002", "接口测试货品B");

        MvcResult listRes = mockMvc.perform(get(SKU_API + "/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        ApiResponse<Object> listResp = parseResponse(listRes.getResponse().getContentAsString(), Object.class);
        List<?> list = objectMapper.convertValue(listResp.getData(), List.class);
        assertTrue(list.size() >= 2);
    }

    @Test
    @DisplayName("SKU接口: 重复创建返回400")
    void testSkuDuplicate() throws Exception {
        createSku("API-SKU-DUP", "重复测试");

        SkuCreateRequest req = new SkuCreateRequest();
        req.setSkuCode("API-SKU-DUP");
        req.setSkuName("重复测试2");

        mockMvc.perform(post(SKU_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("入库接口: 新建批次正确")
    void testInbound() throws Exception {
        createSku("API-SKU-IN", "入库测试货品");

        InboundRequest req = new InboundRequest();
        req.setSkuCode("API-SKU-IN");
        req.setBatchNo("BATCH-API-001");
        req.setLocationCode("LOC-API-01");
        req.setProductionDate(LocalDate.now().minusDays(10));
        req.setExpiryDate(LocalDate.now().plusDays(170));
        req.setQty(100);
        req.setOperator("api-test");

        MvcResult res = mockMvc.perform(post(INV_API + "/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.physicalQty").value(100))
                .andExpect(jsonPath("$.data.availableQty").value(100))
                .andExpect(jsonPath("$.data.reservedQty").value(0))
                .andReturn();

        InboundRequest req2 = new InboundRequest();
        req2.setSkuCode("API-SKU-IN");
        req2.setBatchNo("BATCH-API-001");
        req2.setLocationCode("LOC-API-01");
        req2.setProductionDate(LocalDate.now().minusDays(10));
        req2.setExpiryDate(LocalDate.now().plusDays(170));
        req2.setQty(50);

        mockMvc.perform(post(INV_API + "/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.physicalQty").value(150))
                .andExpect(jsonPath("$.data.availableQty").value(150));
    }

    @Test
    @DisplayName("入库接口: 生产日期晚于到期日被拒绝")
    void testInboundInvalidDate() throws Exception {
        createSku("API-SKU-INV-DATE", "无效日期测试");

        InboundRequest req = new InboundRequest();
        req.setSkuCode("API-SKU-INV-DATE");
        req.setBatchNo("B-BAD");
        req.setLocationCode("L-BAD");
        req.setProductionDate(LocalDate.now().plusDays(10));
        req.setExpiryDate(LocalDate.now());
        req.setQty(10);

        mockMvc.perform(post(INV_API + "/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FEFO出库接口: 先扣最早到期，跨批次拆分")
    void testFefoOutbound() throws Exception {
        createSku("API-SKU-FEFO", "FEFO出库测试");
        LocalDate today = LocalDate.now();

        doInbound("API-SKU-FEFO", "B-EARLY", "L1", today.minusDays(100), today.plusDays(10), 50);
        doInbound("API-SKU-FEFO", "B-MID", "L2", today.minusDays(80), today.plusDays(30), 100);
        doInbound("API-SKU-FEFO", "B-LATE", "L3", today.minusDays(50), today.plusDays(90), 200);

        OutboundAllocateRequest req = new OutboundAllocateRequest();
        req.setSkuCode("API-SKU-FEFO");
        req.setQty(180);
        req.setOrderNo("ORD-API-001");

        MvcResult res = mockMvc.perform(post(INV_API + "/outbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.details").isArray())
                .andExpect(jsonPath("$.data.details.length()").value(3))
                .andExpect(jsonPath("$.data.details[0].batchNo").value("B-EARLY"))
                .andExpect(jsonPath("$.data.details[0].allocatedQty").value(50))
                .andExpect(jsonPath("$.data.details[1].batchNo").value("B-MID"))
                .andExpect(jsonPath("$.data.details[1].allocatedQty").value(100))
                .andExpect(jsonPath("$.data.details[2].batchNo").value("B-LATE"))
                .andExpect(jsonPath("$.data.details[2].allocatedQty").value(30))
                .andReturn();

        MvcResult sumRes = mockMvc.perform(get(INV_API + "/summary/API-SKU-FEFO"))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<Object> sumResp = parseResponse(sumRes.getResponse().getContentAsString(), Object.class);
        Map<?, ?> sum = objectMapper.convertValue(sumResp.getData(), Map.class);
        assertEquals(170, ((Number) sum.get("physicalQty")).intValue());
        assertEquals(170, ((Number) sum.get("availableQty")).intValue());
    }

    @Test
    @DisplayName("出库接口: 可用量不足返回success=false和缺口数量")
    void testOutboundShortage() throws Exception {
        createSku("API-SKU-SHORT", "缺货测试");
        doInbound("API-SKU-SHORT", "B-ONLY", "L1",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(170), 50);

        OutboundAllocateRequest req = new OutboundAllocateRequest();
        req.setSkuCode("API-SKU-SHORT");
        req.setQty(100);

        mockMvc.perform(post(INV_API + "/outbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.shortageQty").value(50))
                .andExpect(jsonPath("$.data.totalAvailable").value(50));

        MvcResult sumRes = mockMvc.perform(get(INV_API + "/summary/API-SKU-SHORT"))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<Object> sumResp = parseResponse(sumRes.getResponse().getContentAsString(), Object.class);
        Map<?, ?> sum = objectMapper.convertValue(sumResp.getData(), Map.class);
        assertEquals(50, ((Number) sum.get("physicalQty")).intValue());
    }

    @Test
    @DisplayName("预占+释放+确认: 完整生命周期接口")
    void testReserveReleaseConfirmLifecycle() throws Exception {
        createSku("API-SKU-RES", "预占生命周期测试");
        doInbound("API-SKU-RES", "B-RES", "L1",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(170), 100);

        ReserveRequest rReq = new ReserveRequest();
        rReq.setSkuCode("API-SKU-RES");
        rReq.setQty(40);
        rReq.setTimeoutMinutes(10);
        rReq.setOrderNo("ORD-RES-001");

        MvcResult resRes = mockMvc.perform(post(INV_API + "/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reservationNo").exists())
                .andReturn();

        Map<?, ?> resData = objectMapper.convertValue(
                parseResponse(resRes.getResponse().getContentAsString(), Object.class).getData(),
                Map.class);
        String reservationNo = (String) resData.get("reservationNo");
        assertNotNull(reservationNo);

        MvcResult midSum = mockMvc.perform(get(INV_API + "/summary/API-SKU-RES"))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> mid = objectMapper.convertValue(
                parseResponse(midSum.getResponse().getContentAsString(), Object.class).getData(), Map.class);
        assertEquals(100, ((Number) mid.get("physicalQty")).intValue());
        assertEquals(40, ((Number) mid.get("reservedQty")).intValue());
        assertEquals(60, ((Number) mid.get("availableQty")).intValue());

        mockMvc.perform(post(INV_API + "/reserve/" + reservationNo + "/release")
                        .param("operator", "api-tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult afterRelease = mockMvc.perform(get(INV_API + "/summary/API-SKU-RES"))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> rel = objectMapper.convertValue(
                parseResponse(afterRelease.getResponse().getContentAsString(), Object.class).getData(), Map.class);
        assertEquals(0, ((Number) rel.get("reservedQty")).intValue());
        assertEquals(100, ((Number) rel.get("availableQty")).intValue());

        ReserveRequest rReq2 = new ReserveRequest();
        rReq2.setSkuCode("API-SKU-RES");
        rReq2.setQty(30);
        rReq2.setTimeoutMinutes(10);
        MvcResult res2 = mockMvc.perform(post(INV_API + "/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rReq2)))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> resData2 = objectMapper.convertValue(
                parseResponse(res2.getResponse().getContentAsString(), Object.class).getData(), Map.class);
        String resNo2 = (String) resData2.get("reservationNo");

        mockMvc.perform(post(INV_API + "/reserve/" + resNo2 + "/confirm")
                        .param("operator", "api-tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult afterConfirm = mockMvc.perform(get(INV_API + "/summary/API-SKU-RES"))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> conf = objectMapper.convertValue(
                parseResponse(afterConfirm.getResponse().getContentAsString(), Object.class).getData(), Map.class);
        assertEquals(70, ((Number) conf.get("physicalQty")).intValue());
        assertEquals(0, ((Number) conf.get("reservedQty")).intValue());
        assertEquals(70, ((Number) conf.get("availableQty")).intValue());
    }

    @Test
    @DisplayName("调拨接口: 源库位减少，目标库位增加，总量守恒")
    void testTransfer() throws Exception {
        createSku("API-SKU-TR", "调拨测试");
        doInbound("API-SKU-TR", "B-TR", "FROM-LOC",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(170), 100);

        TransferRequest tReq = new TransferRequest();
        tReq.setSkuCode("API-SKU-TR");
        tReq.setBatchNo("B-TR");
        tReq.setFromLocation("FROM-LOC");
        tReq.setToLocation("TO-LOC");
        tReq.setQty(40);
        tReq.setOperator("api-tester");

        mockMvc.perform(post(INV_API + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.transferNo").exists());

        MvcResult sumRes = mockMvc.perform(get(INV_API + "/summary/API-SKU-TR"))
                .andExpect(status().isOk()).andReturn();
        ApiResponse<Object> sumResp = parseResponse(sumRes.getResponse().getContentAsString(), Object.class);
        Map<?, ?> sum = objectMapper.convertValue(sumResp.getData(), Map.class);
        assertEquals(100, ((Number) sum.get("physicalQty")).intValue());

        List<?> batches = objectMapper.convertValue(sum.get("batches"), List.class);
        assertEquals(2, batches.size());
    }

    @Test
    @DisplayName("调拨接口: 源库位可用量不足时拒绝")
    void testTransferInsufficient() throws Exception {
        createSku("API-SKU-TR-BAD", "调拨失败测试");
        doInbound("API-SKU-TR-BAD", "B-TR", "FROM-LOC",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(170), 10);

        TransferRequest tReq = new TransferRequest();
        tReq.setSkuCode("API-SKU-TR-BAD");
        tReq.setBatchNo("B-TR");
        tReq.setFromLocation("FROM-LOC");
        tReq.setToLocation("TO-LOC");
        tReq.setQty(50);

        mockMvc.perform(post(INV_API + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("临期预警接口: 按阈值正确筛选")
    void testExpiryAlert() throws Exception {
        createSku("API-SKU-EXP", "临期预警测试");
        LocalDate today = LocalDate.now();
        doInbound("API-SKU-EXP", "B-EXPIRED", "L1", today.minusDays(200), today.minusDays(5), 10);
        doInbound("API-SKU-EXP", "B-NEAR", "L2", today.minusDays(150), today.plusDays(10), 20);
        doInbound("API-SKU-EXP", "B-NORMAL", "L3", today.minusDays(10), today.plusDays(200), 30);

        MvcResult res = mockMvc.perform(get(INV_API + "/expiry-alert")
                        .param("thresholdDays", "30")
                        .param("skuCode", "API-SKU-EXP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        ApiResponse<Object> resp = parseResponse(res.getResponse().getContentAsString(), Object.class);
        List<?> alerts = objectMapper.convertValue(resp.getData(), List.class);
        assertEquals(2, alerts.size());

        Map<?, ?> alert0 = objectMapper.convertValue(alerts.get(0), Map.class);
        String batch0 = (String) alert0.get("batchNo");
        assertTrue(batch0.equals("B-EXPIRED") || batch0.equals("B-NEAR"),
                "应该返回临期或已过期批次，但返回了: " + batch0);
    }

    @Test
    @DisplayName("流水接口: 出入库操作后可查询流水")
    void testTransactionLog() throws Exception {
        createSku("API-SKU-TXN", "流水测试");
        doInbound("API-SKU-TXN", "B-TXN", "L1",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(170), 100);

        OutboundAllocateRequest oReq = new OutboundAllocateRequest();
        oReq.setSkuCode("API-SKU-TXN");
        oReq.setQty(30);
        mockMvc.perform(post(INV_API + "/outbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oReq)))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(get(INV_API + "/transactions")
                        .param("skuCode", "API-SKU-TXN")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andReturn();

        ApiResponse<Object> resp = parseResponse(res.getResponse().getContentAsString(), Object.class);
        Map<?, ?> page = objectMapper.convertValue(resp.getData(), Map.class);
        List<?> content = objectMapper.convertValue(page.get("content"), List.class);
        assertTrue(content.size() >= 2, "至少应有入库和出库两条流水，实际: " + content.size());

        long inboundCount = content.stream()
                .map(o -> objectMapper.convertValue(o, Map.class))
                .filter(m -> "INBOUND".equals(m.get("txnType")))
                .count();
        long outboundCount = content.stream()
                .map(o -> objectMapper.convertValue(o, Map.class))
                .filter(m -> "OUTBOUND".equals(m.get("txnType")))
                .count();
        assertTrue(inboundCount >= 1, "应有入库流水");
        assertTrue(outboundCount >= 1, "应有出库流水");
    }

    @Test
    @DisplayName("中文字段不返回乱码: SKU名称含中文往返一致")
    void testChineseEncoding() throws Exception {
        String chineseName = "测试货品-牛奶纯牛奶-全麦面包-中文名称";
        Sku created = createSku("API-CN-001", chineseName);
        assertEquals(chineseName, created.getSkuName());

        MvcResult res = mockMvc.perform(get(SKU_API + "/API-CN-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuName").value(chineseName))
                .andReturn();
        Sku fetched = parseData(res.getResponse().getContentAsString(), Sku.class);
        assertEquals(chineseName, fetched.getSkuName());

        String content = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(content.contains(chineseName), "UTF-8解码后的响应体应包含中文名称，实际: " + content);
    }

    private void doInbound(String sku, String batch, String loc,
                           LocalDate prod, LocalDate expiry, int qty) throws Exception {
        InboundRequest req = new InboundRequest();
        req.setSkuCode(sku);
        req.setBatchNo(batch);
        req.setLocationCode(loc);
        req.setProductionDate(prod);
        req.setExpiryDate(expiry);
        req.setQty(qty);
        req.setOperator("api-test");
        mockMvc.perform(post(INV_API + "/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
