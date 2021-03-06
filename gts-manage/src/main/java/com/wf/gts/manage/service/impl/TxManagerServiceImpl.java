package com.wf.gts.manage.service.impl;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.wf.gts.common.beans.TxTransactionGroup;
import com.wf.gts.common.beans.TxTransactionItem;
import com.wf.gts.common.enums.TransactionRoleEnum;
import com.wf.gts.common.enums.TransactionStatusEnum;
import com.wf.gts.manage.constant.Constant;
import com.wf.gts.manage.service.TxManagerService;
import com.wufumall.redis.util.JedisUtils;

@Service
public class TxManagerServiceImpl implements TxManagerService{

  private static final Logger LOGGER = LoggerFactory.getLogger(TxManagerServiceImpl.class);
  
  @Override
  public Boolean saveTxTransactionGroup(TxTransactionGroup txTransactionGroup) {
    try {
        final String groupId = txTransactionGroup.getId();
        final List<TxTransactionItem> itemList = txTransactionGroup.getItemList();
        if (CollectionUtils.isNotEmpty(itemList)) {
            for (TxTransactionItem item : itemList) {
                JedisUtils.getJedisInstance().execHsetToCache(cacheKey(groupId), item.getTaskKey(),JSON.toJSONString(item));
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
        LOGGER.error("保存事务组信息报错:{}",e);
        return false;
    }
    return true;
  }

  
  
  @Override
  public Boolean addTxTransaction(String txGroupId, TxTransactionItem txTransactionItem) {
      try {
        JedisUtils.getJedisInstance().execHsetToCache(cacheKey(txGroupId), txTransactionItem.getTaskKey(),JSON.toJSONString(txTransactionItem));
      } catch (Exception e) {
          e.printStackTrace();
          LOGGER.error("增加事务信息报错:{}",e);
          return false;
      }
      return true;
  }

  @Override
  public List<TxTransactionItem> listByTxGroupId(String txGroupId) {
      Map<String,String> entries = JedisUtils.getJedisInstance().execHgetAllToCache(cacheKey(txGroupId));
      return entries.values().stream().map(s->JSON.parseObject(s, TxTransactionItem.class)).collect(Collectors.toList());
  }

  @Override
  public void removeRedisByTxGroupId(String txGroupId) {
      JedisUtils.getJedisInstance().execDelToCache(cacheKey(txGroupId));
  }

  @Override
  public Boolean updateTxTransactionItemStatus(String key, String hashKey, int status) {
      try {
        String item =JedisUtils.getJedisInstance().execHgetToCache(cacheKey(key), hashKey);
        TxTransactionItem txTransactionItem=JSON.parseObject(item, TxTransactionItem.class);
        txTransactionItem.setStatus(status);
        JedisUtils.getJedisInstance().execHsetToCache(cacheKey(key), txTransactionItem.getTaskKey(),JSON.toJSONString(txTransactionItem));
      } catch (BeansException e) {
          e.printStackTrace();
          LOGGER.error("更新事务状态信息报错:{}",e);
          return false;
      }
      return true;
  }

  
  
  @Override
  public int findTxTransactionGroupStatus(String txGroupId) {
      try {
          String item =JedisUtils.getJedisInstance().execHgetToCache(cacheKey(txGroupId), txGroupId);
          TxTransactionItem txTransactionItem=JSON.parseObject(item, TxTransactionItem.class);
          return txTransactionItem.getStatus();
      } catch (Exception e) {
          e.printStackTrace();
          return TransactionStatusEnum.ROLLBACK.getCode();
      }
  }


  private String cacheKey(String key) {
      return String.format(Constant.REDIS_PRE_FIX, key);
  }



  @Override
  public List<List<TxTransactionItem>> listTxTransactionItem() {
    Collection<String> keys=JedisUtils.getJedisInstance().execKeysToCache(Constant.REDIS_KEYS);
    List<List<TxTransactionItem>>  lists=Lists.newArrayList();
    keys.stream().forEach(key->{
        final Map<String, String> entries = JedisUtils.getJedisInstance().execHgetAllToCache(key);
        final Collection<String> values = entries.values();
        final List<TxTransactionItem> items=values.stream()
            .map(item->JSON.parseObject(item, TxTransactionItem.class))
            .collect(Collectors.toList());
        lists.add(items);
     });  
    return lists;
  }
  

  @Override
  public void removeCommitTxGroup() {
    Collection<String> keys=JedisUtils.getJedisInstance().execKeysToCache(Constant.REDIS_KEYS);
    keys.stream().forEach(key -> {
        final Map<String, String> entries = JedisUtils.getJedisInstance().execHgetAllToCache(key);
        final Collection<String> values = entries.values();
        final Optional<TxTransactionItem> any =
                values.stream().map(item->JSON.parseObject(item, TxTransactionItem.class))
                .filter(item -> item.getRole() == TransactionRoleEnum.START.getCode()
                     && item.getStatus() == TransactionStatusEnum.ROLLBACK.getCode())
                .findAny();
        if (any.isPresent()) {
          JedisUtils.getJedisInstance().execDelToCache(key);
        }
    });
  }
  
  /**
   * 
  * 功能描述: <br>
  * @author: xiongkun
  * @date: 2017年11月16日 上午9:48:09
   */
  @Override
  public void removeAllCommit() {
	    Collection<String> keys=JedisUtils.getJedisInstance().execKeysToCache(Constant.REDIS_KEYS);
	    keys.stream().forEach(key -> {
	        final Map<String, String> entries = JedisUtils.getJedisInstance().execHgetAllToCache(key);
	        final Collection<String> values = entries.values();
	        boolean b = values.stream().map(item->JSON.parseObject(item, TxTransactionItem.class))
	        		.allMatch(item -> item.getStatus() == TransactionStatusEnum.COMMIT.getCode());
	        if(b){
	        	JedisUtils.getJedisInstance().execDelToCache(key);
	        }
	    });
	  }
  

}
