package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.mapper.AddressMapper;
import cn.lcj.rongxiaotong20260918.mapper.CartMapper;
import cn.lcj.rongxiaotong20260918.mapper.OrderItemMapper;
import cn.lcj.rongxiaotong20260918.mapper.OrdersMapper;
import cn.lcj.rongxiaotong20260918.mapper.ProductMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.dto.OrderCreateDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Address;
import cn.lcj.rongxiaotong20260918.pojo.entity.Cart;
import cn.lcj.rongxiaotong20260918.pojo.entity.OrderItem;
import cn.lcj.rongxiaotong20260918.pojo.entity.Orders;
import cn.lcj.rongxiaotong20260918.pojo.entity.Product;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderItemVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.UserBriefVO;
import cn.lcj.rongxiaotong20260918.service.OrdersService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单 模块实现
 * 错误码段：2001-2005（B 交易）
 */
@Service
public class OrdersServiceImpl implements OrdersService {

    /** 订单状态 */
    private static final int STATUS_PENDING_PAY = 0; // 待支付
    private static final int STATUS_PAID = 1;        // 已支付
    private static final int STATUS_SHIPPED = 2;     // 已发货
    private static final int STATUS_FINISHED = 3;    // 已完成
    private static final int STATUS_CANCELLED = 4;   // 已取消
    /** 退款状态 */
    private static final int REFUND_NONE = 0;        // 无退款
    private static final int REFUND_APPLYING = 1;    // 退款申请中
    /** 商品审核通过 + 上架 */
    private static final int AUDIT_PASSED = 1;
    private static final int STATUS_ON = 1;
    /** 管理员角色 */
    private static final int ROLE_ADMIN = 3;

    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private AddressMapper addressMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO create(Long userId, OrderCreateDTO dto) {
        requireLogin(userId);
        if (dto.getAddressId() == null) {
            throw new BusinessException(2001, "请选择收货地址");
        }
        if (dto.getCartIds() == null || dto.getCartIds().isEmpty()) {
            throw new BusinessException(2001, "请选择要下单的购物车项");
        }
        // 1. 收货地址校验（必须属于当前用户）
        Address addr = addressMapper.selectById(dto.getAddressId());
        if (addr == null || !addr.getUserId().equals(userId)) {
            throw new BusinessException(2002, "收货地址不存在");
        }
        // 2. 购物车项校验
        List<Cart> carts = cartMapper.selectBatchIds(dto.getCartIds());
        if (carts.size() != dto.getCartIds().size()) {
            throw new BusinessException(2002, "部分购物车项不存在");
        }
        // 3. 校验归属 + 商品可购买 + 库存，并按卖家分组（LinkedHashMap 保持插入顺序）
        Map<Long, List<Cart>> groupBySeller = new LinkedHashMap<>();
        for (Cart c : carts) {
            if (!c.getUserId().equals(userId)) {
                throw new BusinessException(2003, "只能下单自己的购物车");
            }
            Product p = productMapper.selectById(c.getProductId());
            if (p == null) {
                throw new BusinessException(2002, "商品不存在");
            }
            if (!Objects.equals(p.getAuditStatus(), AUDIT_PASSED)
                    || !Objects.equals(p.getStatus(), STATUS_ON)) {
                throw new BusinessException(2001, "商品已下架或未通过审核：" + p.getName());
            }
            if (c.getQuantity() > p.getStock()) {
                throw new BusinessException(2004, "库存不足：" + p.getName() + "，当前库存 " + p.getStock());
            }
            groupBySeller.computeIfAbsent(p.getSellerId(), k -> new ArrayList<>()).add(c);
        }
        // 4. 按卖家拆单
        String receiver = addr.getReceiver();
        String receiverPhone = addr.getPhone();
        String receiverAddress = (addr.getProvince() == null ? "" : addr.getProvince())
                + (addr.getCity() == null ? "" : addr.getCity())
                + (addr.getDistrict() == null ? "" : addr.getDistrict())
                + (addr.getDetail() == null ? "" : addr.getDetail());

        List<String> orderNos = new ArrayList<>();
        BigDecimal totalAll = BigDecimal.ZERO;
        for (Map.Entry<Long, List<Cart>> entry : groupBySeller.entrySet()) {
            Long sellerId = entry.getKey();
            List<Cart> itemCarts = entry.getValue();

            // 计算卖家小计（先算金额再插订单）
            BigDecimal sellerTotal = BigDecimal.ZERO;
            List<OrderItem> items = new ArrayList<>();
            for (Cart c : itemCarts) {
                Product p = productMapper.selectById(c.getProductId());
                BigDecimal subtotal = p.getPrice().multiply(BigDecimal.valueOf(c.getQuantity()));
                sellerTotal = sellerTotal.add(subtotal);

                OrderItem oi = new OrderItem();
                oi.setProductId(p.getId());
                oi.setProductName(p.getName());   // 名称快照
                oi.setProductImage(p.getImage()); // 主图快照
                oi.setPrice(p.getPrice());        // 单价快照
                oi.setQuantity(c.getQuantity());
                oi.setSubtotal(subtotal);
                items.add(oi);
            }

            // 插入订单主表
            Orders order = new Orders();
            order.setOrderNo(nextOrderNo());
            order.setBuyerId(userId);
            order.setSellerId(sellerId);
            order.setTotalAmount(sellerTotal);
            order.setAddressId(addr.getId());
            order.setReceiver(receiver);
            order.setReceiverPhone(receiverPhone);
            order.setReceiverAddress(receiverAddress);
            order.setRemark(dto.getRemark());
            order.setStatus(STATUS_PENDING_PAY);
            order.setRefundStatus(REFUND_NONE);
            ordersMapper.insert(order);

            // 插入明细 + 扣减库存 + 删除购物车项
            for (int i = 0; i < itemCarts.size(); i++) {
                Cart c = itemCarts.get(i);
                OrderItem oi = items.get(i);
                oi.setOrderId(order.getId());
                orderItemMapper.insert(oi);

                Product p = productMapper.selectById(c.getProductId());
                Product stockUpdate = new Product();
                stockUpdate.setId(p.getId());
                stockUpdate.setStock(p.getStock() - c.getQuantity());
                productMapper.updateById(stockUpdate);

                cartMapper.deleteById(c.getId());
            }
            orderNos.add(order.getOrderNo());
            totalAll = totalAll.add(sellerTotal);
        }

        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderNos(orderNos);
        vo.setTotalAmount(totalAll);
        return vo;
    }

    @Override
    public PageResult<OrderListVO> purchaseList(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        requireLogin(userId);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getBuyerId, userId)
                .eq(status != null, Orders::getStatus, status)
                .orderByDesc(Orders::getCreateTime);
        Page<Orders> page = ordersMapper.selectPage(new Page<>(num, size), wrapper);

        Map<Long, User> sellerMap = userMap(page.getRecords(), Orders::getSellerId);
        List<OrderListVO> list = page.getRecords().stream()
                .map(o -> toListVO(o, sellerMap.get(o.getSellerId()), null))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    @Override
    public PageResult<OrderListVO> saleList(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        requireLogin(userId);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getSellerId, userId)
                .eq(status != null, Orders::getStatus, status)
                .orderByDesc(Orders::getCreateTime);
        Page<Orders> page = ordersMapper.selectPage(new Page<>(num, size), wrapper);

        Map<Long, User> buyerMap = userMap(page.getRecords(), Orders::getBuyerId);
        List<OrderListVO> list = page.getRecords().stream()
                .map(o -> toListVO(o, null, buyerMap.get(o.getBuyerId())))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    @Override
    public OrderDetailVO detail(Long userId, Long id) {
        requireLogin(userId);
        Orders o = ordersMapper.selectById(id);
        if (o == null) {
            throw new BusinessException(2002, "订单不存在");
        }
        // 权限：买家、卖家、管理员可看
        User user = userMapper.selectById(userId);
        boolean isAdmin = user != null && user.getRole() != null && user.getRole() == ROLE_ADMIN;
        if (!isAdmin && !o.getBuyerId().equals(userId) && !o.getSellerId().equals(userId)) {
            throw new BusinessException(2003, "无权查看该订单");
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setBuyerId(o.getBuyerId());
        vo.setSellerId(o.getSellerId());
        vo.setRemark(o.getRemark());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setStatus(o.getStatus());
        vo.setRefundStatus(o.getRefundStatus());
        vo.setRefundRemark(o.getRefundRemark());
        vo.setReceiver(o.getReceiver());
        vo.setReceiverPhone(o.getReceiverPhone());
        vo.setReceiverAddress(o.getReceiverAddress());
        vo.setPayTime(o.getPayTime());
        vo.setShipTime(o.getShipTime());
        vo.setFinishTime(o.getFinishTime());
        vo.setCreateTime(o.getCreateTime());
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public void pay(Long userId, Long id) {
        requireLogin(userId);
        Orders o = mustGet(id);
        checkBuyer(o, userId);
        if (o.getStatus() != STATUS_PENDING_PAY) {
            throw new BusinessException(2005, "当前状态不可支付");
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(STATUS_PAID);
        update.setPayTime(LocalDateTime.now());
        ordersMapper.updateById(update);
    }

    @Override
    public void ship(Long userId, Long id) {
        requireLogin(userId);
        Orders o = mustGet(id);
        checkSeller(o, userId);
        if (o.getStatus() != STATUS_PAID) {
            throw new BusinessException(2005, "仅已支付订单可发货");
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(STATUS_SHIPPED);
        update.setShipTime(LocalDateTime.now());
        ordersMapper.updateById(update);
    }

    @Override
    public void finish(Long userId, Long id) {
        requireLogin(userId);
        Orders o = mustGet(id);
        checkBuyer(o, userId);
        if (o.getStatus() != STATUS_SHIPPED) {
            throw new BusinessException(2005, "仅已发货订单可确认收货");
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(STATUS_FINISHED);
        update.setFinishTime(LocalDateTime.now());
        ordersMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long id) {
        requireLogin(userId);
        Orders o = mustGet(id);
        checkBuyer(o, userId);
        if (o.getStatus() != STATUS_PENDING_PAY) {
            throw new BusinessException(2005, "仅待支付订单可取消");
        }
        // 取消后恢复库存（从明细还原）
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        for (OrderItem oi : items) {
            Product p = productMapper.selectById(oi.getProductId());
            if (p != null) {
                Product stockUpdate = new Product();
                stockUpdate.setId(p.getId());
                stockUpdate.setStock(p.getStock() + oi.getQuantity());
                productMapper.updateById(stockUpdate);
            }
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(STATUS_CANCELLED);
        ordersMapper.updateById(update);
    }

    @Override
    public void refund(Long userId, Long id, String refundRemark) {
        requireLogin(userId);
        Orders o = mustGet(id);
        checkBuyer(o, userId);
        if (o.getStatus() == STATUS_PENDING_PAY || o.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException(2005, "当前状态不可申请退款");
        }
        if (o.getRefundStatus() != REFUND_NONE) {
            throw new BusinessException(2005, "已有退款申请，请勿重复申请");
        }
        Orders update = new Orders();
        update.setId(id);
        update.setRefundStatus(REFUND_APPLYING);
        update.setRefundRemark(StringUtils.hasText(refundRemark) ? refundRemark.trim() : "未填写退款原因");
        ordersMapper.updateById(update);
    }

    // ---------- 私有辅助方法 ----------

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(2001, "未登录或登录已过期");
        }
    }

    private Orders mustGet(Long id) {
        Orders o = ordersMapper.selectById(id);
        if (o == null) {
            throw new BusinessException(2002, "订单不存在");
        }
        return o;
    }

    private void checkBuyer(Orders o, Long userId) {
        if (!o.getBuyerId().equals(userId)) {
            throw new BusinessException(2003, "只能操作自己的订单");
        }
    }

    private void checkSeller(Orders o, Long userId) {
        if (!o.getSellerId().equals(userId)) {
            throw new BusinessException(2003, "只能操作自己的订单");
        }
    }

    /**
     * 生成订单号：RX + yyyyMMdd + 3位当日序号（如 RX20260922001）
     * 说明：以当天已有订单数 +1 生成序号，教学场景够用；高并发下可改为
     * 数据库自增ID、Redis 自增或分布式ID，避免重复。
     */
    private String nextOrderNo() {
        String prefix = "RX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long todayCount = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>().likeRight(Orders::getOrderNo, prefix));
        return prefix + String.format("%03d", todayCount + 1);
    }

    /** 批量查询订单涉及的某类用户，得到 id -> user 映射 */
    private Map<Long, User> userMap(List<Orders> orders, Function<Orders, Long> idGetter) {
        Set<Long> ids = orders.stream()
                .map(idGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 订单行 -> 列表VO（采购填 seller，销售填 buyer） */
    private OrderListVO toListVO(Orders o, User seller, User buyer) {
        OrderListVO vo = new OrderListVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setStatus(o.getStatus());
        vo.setRefundStatus(o.getRefundStatus());
        vo.setReceiver(o.getReceiver());
        vo.setReceiverPhone(o.getReceiverPhone());
        vo.setReceiverAddress(o.getReceiverAddress());
        if (seller != null) {
            vo.setSeller(toBrief(seller));
        }
        if (buyer != null) {
            vo.setBuyer(toBrief(buyer));
        }
        vo.setCreateTime(o.getCreateTime());
        return vo;
    }

    /** 用户 -> 简要VO */
    private UserBriefVO toBrief(User u) {
        UserBriefVO v = new UserBriefVO();
        v.setId(u.getId());
        v.setRealName(u.getRealName());
        v.setFarmName(u.getFarmName());
        return v;
    }

    /** 明细行 -> VO */
    private OrderItemVO toItemVO(OrderItem oi) {
        OrderItemVO vo = new OrderItemVO();
        vo.setId(oi.getId());
        vo.setProductId(oi.getProductId());
        vo.setProductName(oi.getProductName());
        vo.setProductImage(oi.getProductImage());
        vo.setPrice(oi.getPrice());
        vo.setQuantity(oi.getQuantity());
        vo.setSubtotal(oi.getSubtotal());
        return vo;
    }
}
