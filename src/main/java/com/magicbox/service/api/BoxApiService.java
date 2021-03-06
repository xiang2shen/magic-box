package com.magicbox.service.api;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.magicbox.model.*;
import com.magicbox.service.FrameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.magicbox.base.constants.MqttConstants;
import com.magicbox.base.exception.ErrorCodes;
import com.magicbox.base.support.ResponseWrapper;
import com.magicbox.base.utilities.BeanChecker;
import com.magicbox.mapper.BoxMapper;
import com.magicbox.mqtt.MqttClient;
import com.magicbox.service.BoxService;
import com.magicbox.service.ProductService;
import com.magicbox.service.ShopService;

@Service
public class BoxApiService {
	
	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger(BoxApiService.class);

	@Autowired
	private ProductService productService;
	@Autowired
	private BoxService boxService;
	@Autowired
	private BoxMapper boxMapper;
	@Autowired
	private ShopService shopService;
	@Autowired
	private MemberApiService memberApiService;
	@Autowired
	private FrameService frameService;
	@Autowired
	private MqttClient mqttClient;
	

	public ResponseWrapper<Box> bindBoxWithProduct(Long memberId, String productCode, String boxCode) {
		BeanChecker.getInstance().notNull(memberId).notBlank(productCode).notBlank(boxCode);
		
		// 校验卖家
		Seller seller = memberApiService.findSellerByMemberId(memberId).getBody();
		if (null == seller) {
			return ResponseWrapper.fail(ErrorCodes.NOT_SELLER);
		}
		
		Product product = productService.selectOneByProductCode(productCode);
		if (null == product) {
			return ResponseWrapper.fail(ErrorCodes.PRODUCT_NOT_FOUND);
		}
		
		Box box = boxService.selectOneByBoxCode(boxCode);
		if (null == box) {
			return ResponseWrapper.fail(ErrorCodes.BOX_NOT_FOUND);
		}
		
		// 校验店铺是否相同
		Shop shop = shopService.selectOneByShopCode(product.getShopCode());
		if (null == shop) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_FOUND);
		}
		if (!shop.getSellerId().equals(seller.getId())) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_BELONG_TO_SELLER);
		}

		boxService.updateProductCodeByBoxCode(productCode, boxCode);
		
		return ResponseWrapper.succeed(boxService.selectOneByBoxCode(boxCode));
	}


	public ResponseWrapper<Box> createBox(Long memberId, Box box) {
		BeanChecker.getInstance().notNull(memberId).notNull(box).notBlank(box.getShopCode());
		
		// 校验卖家
		Seller seller = memberApiService.findSellerByMemberId(memberId).getBody();
		if (null == seller) {
			return ResponseWrapper.fail(ErrorCodes.NOT_SELLER);
		}
		
		Shop shop = shopService.selectOneByShopCode(box.getShopCode());
		if (null == shop) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_FOUND);
		}
		if (!shop.getSellerId().equals(seller.getId())) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_BELONG_TO_SELLER);
		}

		String boxCode = "BOX" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
		Date now = new Date();
		
		box.setBoxCode(boxCode);
		box.setFrameCode("testFrameCode");
		box.setBoxStatus(1);
		box.setBoxModel("测试型号");
		box.setCapacity(8);
		box.setCreateTime(now);
		box.setUpdateTime(now);

		boxMapper.insert(box);
		
		return ResponseWrapper.succeed(box);
	}

	public ResponseWrapper<Box> createOrUpdateBox(String frameCode, String boxCode, Integer boxPosition, Integer capacity, Integer stock) {
		BeanChecker.getInstance().notBlank(frameCode).notBlank(boxCode).positive(capacity).positiveOrZero(stock);

		Frame frame = frameService.selectOneByFrameCode(frameCode);
		Box box = boxService.selectOneByBoxCode(boxCode);
		if (null == box) {
			
			box = new Box();
			Date now = new Date();

			box.setShopCode(frame.getShopCode());
			box.setFrameCode(frameCode);
			box.setBoxCode(boxCode);
			box.setCapacity(capacity);
			box.setBoxPosition(boxPosition);
			box.setBoxStatus(1);
			box.setCreateTime(now);
			box.setUpdateTime(now);
			box.setProductStock(stock);
			
			boxMapper.insert(box);
		} else {

			box.setShopCode(frame.getShopCode());
			box.setFrameCode(frameCode);	// 更新设备号
			box.setProductStock(stock);
			boxMapper.updateByPrimaryKeySelective(box);
		}
		
		return ResponseWrapper.succeed(boxService.selectOneByBoxCode(boxCode));
	}


	public ResponseWrapper<?> updateStock(Long memberId, String boxCode, Integer stock) {
		BeanChecker.getInstance().notNull(memberId).notBlank(boxCode).positiveOrZero(stock);
		
		// 校验卖家
		Seller seller = memberApiService.findSellerByMemberId(memberId).getBody();
		if (null == seller) {
			return ResponseWrapper.fail(ErrorCodes.NOT_SELLER);
		}
		
		Box box = boxService.selectOneByBoxCode(boxCode);
		if (null == box) {
			return ResponseWrapper.fail(ErrorCodes.BOX_NOT_FOUND);
		}
		
		// 校验店铺是否相同
		Shop shop = shopService.selectOneByShopCode(box.getShopCode());
		if (null == shop) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_FOUND);
		}
		if (!shop.getSellerId().equals(seller.getId())) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_BELONG_TO_SELLER);
		}
		
		boxService.updateStockByBoxCode(boxCode, stock);
		
		mqttClient.publish(MqttConstants.TOPIC_UPDATE_STOCK + box.getFrameCode(), box.getBoxCode() + "|" + stock);
		
		return ResponseWrapper.succeed();
	}


	public ResponseWrapper<?> resetBox(Long memberId, String boxCode) {
		BeanChecker.getInstance().notNull(memberId).notBlank(boxCode);
		
		Box box = boxService.selectOneByBoxCode(boxCode);
		if (null == box) {
			return ResponseWrapper.fail(ErrorCodes.BOX_NOT_FOUND);
		}
		
		// 校验店铺是否相同
		Frame frame = frameService.selectOneByFrameCode(box.getFrameCode());
		if (null == frame) {
			return ResponseWrapper.fail(ErrorCodes.FRAME_NOT_FOUND);
		}
		Shop shop = shopService.selectOneByShopCode(frame.getShopCode());
		if (null == shop) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_FOUND);
		}
		
		// 校验卖家店员权限
		ResponseWrapper<List<Shop>> authResp = memberApiService.getShopListBySellerOrShopAssistantAuth(memberId);
		if (authResp.isFailed()) {
			return authResp;
		}
		List<Shop> shops = authResp.getBody();
		List<Long> shopIds = shops.stream().map(Shop::getId).collect(Collectors.toList());
		if (!shopIds.contains(shop.getId())) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_BELONG_TO_SELLER);
		}
		
		mqttClient.publish(MqttConstants.TOPIC_RESET + box.getFrameCode(), box.getBoxCode());
		
		return ResponseWrapper.succeed();
	}


	public ResponseWrapper<?> openBox(Long memberId, String boxCode) {
		BeanChecker.getInstance().notNull(memberId).notBlank(boxCode);
		
		Box box = boxService.selectOneByBoxCode(boxCode);
		if (null == box) {
			return ResponseWrapper.fail(ErrorCodes.BOX_NOT_FOUND);
		}
		
		// 校验店铺是否相同
		Frame frame = frameService.selectOneByFrameCode(box.getFrameCode());
		if (null == frame) {
			return ResponseWrapper.fail(ErrorCodes.FRAME_NOT_FOUND);
		}
		Shop shop = shopService.selectOneByShopCode(frame.getShopCode());
		if (null == shop) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_FOUND);
		}
		
		// 校验卖家店员权限
		ResponseWrapper<List<Shop>> authResp = memberApiService.getShopListBySellerOrShopAssistantAuth(memberId);
		if (authResp.isFailed()) {
			return authResp;
		}
		List<Shop> shops = authResp.getBody();
		List<Long> shopIds = shops.stream().map(Shop::getId).collect(Collectors.toList());
		if (!shopIds.contains(shop.getId())) {
			return ResponseWrapper.fail(ErrorCodes.SHOP_NOT_BELONG_TO_SELLER);
		}
		
		String msgContent = MqttConstants.FORCE_OPEN_ORDER_CODE + "|" + boxCode + "|" + 1;
		mqttClient.publish(MqttConstants.TOPIC_OPEN_AFTER_PAY + box.getFrameCode(), msgContent);
		
		return ResponseWrapper.succeed();
	}


	public ResponseWrapper<?> triggerUpdateStock(Long memberId, String frameCode) {
		BeanChecker.getInstance().notNull(memberId).notBlank(frameCode);
		
		// 校验卖家
		Seller seller = memberApiService.findSellerByMemberId(memberId).getBody();
		if (null == seller) {
			return ResponseWrapper.fail(ErrorCodes.NOT_SELLER);
		}
		
		boxService.unbindWithFrame(frameCode);

		logger.info("publish message [{}] to topick [{}]", 1, MqttConstants.TOPIC_TRIGGER_SYN_STOCK + frameCode);
		mqttClient.publish(MqttConstants.TOPIC_TRIGGER_SYN_STOCK + frameCode, "1");
		
		return ResponseWrapper.succeed();
	}
}
