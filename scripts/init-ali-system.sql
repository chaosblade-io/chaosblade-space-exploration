-- =============================================================================
-- 初始化被测系统: 阿里适配系统 (OTel Demo in cms-demo namespace)
-- 数据来源: /Users/mymz/work/aliexplore/api_topology.json
-- =============================================================================

USE `spaceexploration`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 创建被测系统
-- =============================================
INSERT INTO `systems` (`system_key`, `name`, `description`, `owner`, `default_environment`)
VALUES (
  'cms-demo',
  '阿里适配系统',
  'OTel Demo 微服务系统，部署在 cms-demo 命名空间，包含 frontend-proxy、frontend、cart、checkout、currency、email、payment、shipping、quote、recommendation、product-catalog、inventory 等 13 个服务',
  'chaos-team',
  'cms-demo'
);

SET @system_id = LAST_INSERT_ID();

-- =============================================
-- 2. 创建 10 个 API 定义
-- =============================================

-- API 1: GET /api/products
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `timeout_ms`)
VALUES (@system_id, 'getProducts', 'GET', '/api/products', '获取所有商品列表', '["product"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE', 15000);
SET @api_1 = LAST_INSERT_ID();

-- API 2: GET /api/products/{productId}
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `path_params`, `timeout_ms`)
VALUES (@system_id, 'getProductById', 'GET', '/api/products/{productId}', '获取单个商品详情', '["product"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE',
  '[{"name":"productId","type":"string","required":true,"default":"OLJCESPC7Z","desc":"商品ID"}]', 15000);
SET @api_2 = LAST_INSERT_ID();

-- API 3: GET /api/cart
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `timeout_ms`)
VALUES (@system_id, 'getCart', 'GET', '/api/cart', '获取购物车', '["cart"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE', 15000);
SET @api_3 = LAST_INSERT_ID();

-- API 4: POST /api/cart
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `body_template`, `timeout_ms`)
VALUES (@system_id, 'addToCart', 'POST', '/api/cart', '添加商品到购物车', '["cart"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE',
  '{"item":{"productId":"OLJCESPC7Z","quantity":1},"userId":"6af11df9-5db1-4931-8b0f-438215eabaaa"}', 15000);
SET @api_4 = LAST_INSERT_ID();

-- API 5: DELETE /api/cart
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `body_template`, `timeout_ms`)
VALUES (@system_id, 'emptyCart', 'DELETE', '/api/cart', '清空购物车', '["cart"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE',
  '{"userId":"6af11df9-5db1-4931-8b0f-438215eabaaa"}', 15000);
SET @api_5 = LAST_INSERT_ID();

-- API 6: POST /api/checkout
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `body_template`, `timeout_ms`)
VALUES (@system_id, 'checkout', 'POST', '/api/checkout', '提交订单', '["order"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE',
  '{"userId":"6af11df9-5db1-4931-8b0f-438215eabaaa","email":"someone@example.com","address":{"streetAddress":"1600 Amphitheatre Parkway","zipCode":"94043","city":"Mountain View","state":"CA","country":"United States"},"userCurrency":"USD","creditCard":{"creditCardNumber":"4432-8015-6152-0454","creditCardExpirationMonth":1,"creditCardExpirationYear":2030,"creditCardCvv":672}}', 15000);
SET @api_6 = LAST_INSERT_ID();

-- API 7: GET /api/shipping
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `query_params`, `timeout_ms`)
VALUES (@system_id, 'getShippingQuote', 'GET', '/api/shipping', '获取运费报价', '["shipping"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE',
  CAST('[{"name":"itemList","type":"string","required":true,"default":"[{\\\"productId\\\":\\\"OLJCESPC7Z\\\",\\\"quantity\\\":1}]"},{"name":"currencyCode","type":"string","required":true,"default":"USD"},{"name":"address","type":"string","required":true,"default":"{\\\"streetAddress\\\":\\\"123 Main St\\\",\\\"city\\\":\\\"New York\\\",\\\"state\\\":\\\"NY\\\",\\\"country\\\":\\\"United States\\\",\\\"zipCode\\\":\\\"10001\\\"}"}]' AS JSON), 15000);
SET @api_7 = LAST_INSERT_ID();

-- API 8: GET /api/currency
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `timeout_ms`)
VALUES (@system_id, 'getCurrencies', 'GET', '/api/currency', '获取支持的货币列表', '["currency"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE', 15000);
SET @api_8 = LAST_INSERT_ID();

-- API 9: GET /api/recommendations
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `query_params`, `timeout_ms`)
VALUES (@system_id, 'getRecommendations', 'GET', '/api/recommendations', '获取推荐商品', '["recommendation"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE',
  '[{"name":"productIds","type":"string","required":true,"default":"OLJCESPC7Z"}]', 15000);
SET @api_9 = LAST_INSERT_ID();

-- API 10: GET /api/data
INSERT INTO `apis` (`system_id`, `operation_id`, `method`, `path`, `summary`, `tags`, `base_url`, `content_type`, `auth_type`, `query_params`, `timeout_ms`)
VALUES (@system_id, 'getAdData', 'GET', '/api/data', '获取广告/横幅数据', '["ad"]',
  'http://nlb-at0lja5aonn4x8ozyb.cn-hongkong.nlb.aliyuncsslbintl.com', 'application/json', 'NONE',
  '[{"name":"contextKeys","type":"string","required":true,"default":"OLJCESPC7Z"}]', 15000);
SET @api_10 = LAST_INSERT_ID();

-- =============================================
-- 3. 创建 10 个拓扑记录 (每个 API 一个拓扑)
-- =============================================

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_1, NOW(), '1.0', 'GET /api/products - 3 nodes, 2 edges');
SET @topo_1 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_2, NOW(), '1.0', 'GET /api/products/{productId} - 3 nodes, 2 edges');
SET @topo_2 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_3, NOW(), '1.0', 'GET /api/cart - 4 nodes, 3 edges');
SET @topo_3 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_4, NOW(), '1.0', 'POST /api/cart - 4 nodes, 3 edges');
SET @topo_4 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_5, NOW(), '1.0', 'DELETE /api/cart - 3 nodes, 2 edges');
SET @topo_5 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_6, NOW(), '1.0', 'POST /api/checkout - 9 nodes, 8 edges');
SET @topo_6 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_7, NOW(), '1.0', 'GET /api/shipping - 5 nodes, 4 edges');
SET @topo_7 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_8, NOW(), '1.0', 'GET /api/currency - 3 nodes, 2 edges');
SET @topo_8 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_9, NOW(), '1.0', 'GET /api/recommendations - 4 nodes, 4 edges');
SET @topo_9 = LAST_INSERT_ID();

INSERT INTO `api_topologies` (`system_id`, `api_id`, `discovered_at`, `source_version`, `notes`)
VALUES (@system_id, @api_10, NOW(), '1.0', 'GET /api/data - 2 nodes, 1 edge');
SET @topo_10 = LAST_INSERT_ID();

-- =============================================
-- 4. 拓扑节点 (每个拓扑的服务节点)
-- =============================================

-- ---- Topo 1: GET /api/products ----
-- nodes: frontend-proxy(root, layer=1), frontend(layer=2), product-catalog(layer=3)
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_1, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t1_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_1, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":4}');
SET @t1_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_1, 'product-catalog', 'product-catalog', 3, 'HTTP', '{"root":false,"span_count":1}');
SET @t1_product_catalog = LAST_INSERT_ID();

-- edges
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_1, @t1_frontend_proxy, @t1_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_1, @t1_frontend, @t1_product_catalog, '{"protocol":"unknown"}');

-- ---- Topo 2: GET /api/products/{productId} ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_2, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t2_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_2, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":4}');
SET @t2_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_2, 'product-catalog', 'product-catalog', 3, 'HTTP', '{"root":false,"span_count":1}');
SET @t2_product_catalog = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_2, @t2_frontend_proxy, @t2_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_2, @t2_frontend, @t2_product_catalog, '{"protocol":"unknown"}');

-- ---- Topo 3: GET /api/cart ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_3, 'rum-cms-demo', 'rum-cms-demo', 0, 'HTTP', '{"root":true,"span_count":1}');
SET @t3_rum = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_3, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":false,"span_count":2}');
SET @t3_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_3, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":4}');
SET @t3_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_3, 'cart', 'cart', 3, 'HTTP', '{"root":true,"span_count":3}');
SET @t3_cart = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_3, @t3_rum, @t3_frontend_proxy, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_3, @t3_frontend_proxy, @t3_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_3, @t3_frontend, @t3_cart, '{"protocol":"HTTP"}');

-- ---- Topo 4: POST /api/cart ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_4, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t4_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_4, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":5}');
SET @t4_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_4, 'cart', 'cart', 3, 'HTTP', '{"root":true,"span_count":10}');
SET @t4_cart = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_4, 'inventory', 'inventory', 4, 'HTTP', '{"root":false,"span_count":3}');
SET @t4_inventory = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_4, @t4_frontend_proxy, @t4_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_4, @t4_frontend, @t4_cart, '{"protocol":"HTTP"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_4, @t4_cart, @t4_inventory, '{"protocol":"unknown"}');

-- ---- Topo 5: DELETE /api/cart ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_5, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t5_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_5, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":4}');
SET @t5_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_5, 'cart', 'cart', 3, 'HTTP', '{"root":true,"span_count":5}');
SET @t5_cart = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_5, @t5_frontend_proxy, @t5_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_5, @t5_frontend, @t5_cart, '{"protocol":"HTTP"}');

-- ---- Topo 6: POST /api/checkout (最复杂: 9 nodes, 8 edges) ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t6_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":4}');
SET @t6_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'checkout', 'checkout', 3, 'HTTP', '{"root":false,"span_count":11}');
SET @t6_checkout = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'cart', 'cart', 4, 'HTTP', '{"root":true,"span_count":8}');
SET @t6_cart = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'currency', 'currency', 4, 'HTTP', '{"root":false,"span_count":1}');
SET @t6_currency = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'email', 'email', 4, 'HTTP', '{"root":false,"span_count":4}');
SET @t6_email = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'payment', 'payment', 4, 'gRPC', '{"root":false,"span_count":2}');
SET @t6_payment = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'shipping', 'shipping', 4, 'HTTP', '{"root":false,"span_count":3}');
SET @t6_shipping = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_6, 'quote', 'quote', 5, 'HTTP', '{"root":false,"span_count":3}');
SET @t6_quote = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_6, @t6_frontend_proxy, @t6_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_6, @t6_frontend, @t6_checkout, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_6, @t6_checkout, @t6_cart, '{"protocol":"HTTP"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_6, @t6_checkout, @t6_currency, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_6, @t6_checkout, @t6_email, '{"protocol":"HTTP"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_6, @t6_checkout, @t6_payment, '{"protocol":"gRPC"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_6, @t6_checkout, @t6_shipping, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_6, @t6_shipping, @t6_quote, '{"protocol":"HTTP"}');

-- ---- Topo 7: GET /api/shipping ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_7, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t7_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_7, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":7}');
SET @t7_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_7, 'shipping', 'shipping', 3, 'HTTP', '{"root":false,"span_count":2}');
SET @t7_shipping = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_7, 'currency', 'currency', 3, 'HTTP', '{"root":false,"span_count":1}');
SET @t7_currency = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_7, 'quote', 'quote', 4, 'HTTP', '{"root":false,"span_count":3}');
SET @t7_quote = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_7, @t7_frontend_proxy, @t7_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_7, @t7_frontend, @t7_shipping, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_7, @t7_frontend, @t7_currency, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_7, @t7_shipping, @t7_quote, '{"protocol":"HTTP"}');

-- ---- Topo 8: GET /api/currency ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_8, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t8_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_8, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":4}');
SET @t8_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_8, 'currency', 'currency', 3, 'HTTP', '{"root":false,"span_count":1}');
SET @t8_currency = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_8, @t8_frontend_proxy, @t8_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_8, @t8_frontend, @t8_currency, '{"protocol":"unknown"}');

-- ---- Topo 9: GET /api/recommendations ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_9, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t9_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_9, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":8}');
SET @t9_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_9, 'recommendation', 'recommendation', 3, 'HTTP', '{"root":false,"span_count":5}');
SET @t9_recommendation = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_9, 'product-catalog', 'product-catalog', 3, 'HTTP', '{"root":false,"span_count":5}');
SET @t9_product_catalog = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_9, @t9_frontend_proxy, @t9_frontend, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_9, @t9_frontend, @t9_recommendation, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_9, @t9_frontend, @t9_product_catalog, '{"protocol":"unknown"}');
INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_9, @t9_recommendation, @t9_product_catalog, '{"protocol":"unknown"}');

-- ---- Topo 10: GET /api/data ----
INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_10, 'frontend-proxy', 'frontend-proxy', 1, 'HTTP', '{"root":true,"span_count":2}');
SET @t10_frontend_proxy = LAST_INSERT_ID();

INSERT INTO `api_topology_nodes` (`topology_id`, `node_key`, `name`, `layer`, `protocol`, `metadata`)
VALUES (@topo_10, 'frontend', 'frontend', 2, 'HTTP', '{"root":false,"span_count":4}');
SET @t10_frontend = LAST_INSERT_ID();

INSERT INTO `api_topology_edges` (`topology_id`, `from_node_id`, `to_node_id`, `metadata`)
VALUES (@topo_10, @t10_frontend_proxy, @t10_frontend, '{"protocol":"unknown"}');

-- =============================================
-- 5. 验证
-- =============================================
SELECT 'systems' AS `table`, COUNT(*) AS `count` FROM `systems` WHERE `system_key` = 'cms-demo'
UNION ALL
SELECT 'apis', COUNT(*) FROM `apis` WHERE `system_id` = @system_id
UNION ALL
SELECT 'api_topologies', COUNT(*) FROM `api_topologies` WHERE `system_id` = @system_id
UNION ALL
SELECT 'api_topology_nodes', COUNT(*) FROM `api_topology_nodes` WHERE `topology_id` BETWEEN @topo_1 AND @topo_10
UNION ALL
SELECT 'api_topology_edges', COUNT(*) FROM `api_topology_edges` WHERE `topology_id` BETWEEN @topo_1 AND @topo_10;

SET FOREIGN_KEY_CHECKS = 1;
