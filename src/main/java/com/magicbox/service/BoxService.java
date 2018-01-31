package com.magicbox.service;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.magicbox.base.support.Page;
import com.magicbox.base.utilities.XCollectionUtils;
import com.magicbox.mapper.BoxMapper;
import com.magicbox.model.Box;
import com.magicbox.model.BoxExample;

@Service
public class BoxService {
	
    @Autowired
    private BoxMapper boxMapper;

    protected Page<Box> selectPageByExample(BoxExample example) {
        return new Page<>(example.getPageNo(), example.getLimit(), boxMapper.selectByExample(example), boxMapper.countByExample(example));
    }

	public Box selectOneByBoxCode(String boxCode) {
		if (StringUtils.isBlank(boxCode)) {
			return null;
		}
		
		BoxExample example = new BoxExample();
		example.or().andBoxCodeEqualTo(boxCode);
		
		return XCollectionUtils.getFirstElement(boxMapper.selectByExample(example));
	}

	public void updateProductCodeByBoxCode(String productCode, String boxCode) {
		if (StringUtils.isBlank(productCode) || StringUtils.isBlank(boxCode)) {
			return;
		}
		
		BoxExample example = new BoxExample();
		example.or().andBoxCodeEqualTo(boxCode);
		
		Box record = new Box();
		record.setProductCode(productCode);
		
		boxMapper.updateByExampleSelective(record, example);
	}

	public List<Box> selectListByProductCode(String productCode) {
		if (StringUtils.isBlank(productCode)) {
			return Collections.emptyList();
		}
		
		BoxExample example = new BoxExample();
		example.setOrderByClause("id desc");
		example.or().andProductCodeEqualTo(productCode);
		
		return boxMapper.selectByExample(example);
	}
}