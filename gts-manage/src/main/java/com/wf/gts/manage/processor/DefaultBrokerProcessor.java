package com.wf.gts.manage.processor;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wf.gts.common.beans.TxTransactionGroup;
import com.wf.gts.common.beans.TxTransactionItem;
import com.wf.gts.manage.ManageController;
import com.wf.gts.manage.domain.Address;
import com.wf.gts.remoting.exception.RemotingCommandException;
import com.wf.gts.remoting.header.AddTransRequestHeader;
import com.wf.gts.remoting.header.FindTransGroupStatusRequestHeader;
import com.wf.gts.remoting.header.FindTransGroupStatusResponseHeader;
import com.wf.gts.remoting.header.PreCommitRequestHeader;
import com.wf.gts.remoting.header.RollBackTransGroupRequestHeader;
import com.wf.gts.remoting.netty.NettyRequestProcessor;
import com.wf.gts.remoting.protocol.RemotingCommand;
import com.wf.gts.remoting.protocol.RemotingSerializable;
import com.wf.gts.remoting.protocol.RequestCode;
import com.wf.gts.remoting.protocol.ResponseCode;

import io.netty.channel.ChannelHandlerContext;


public class DefaultBrokerProcessor implements NettyRequestProcessor {
  
   private static final Logger log = LoggerFactory.getLogger(DefaultBrokerProcessor.class);
   
   private ManageController manageController;
   
    public DefaultBrokerProcessor(ManageController manageController) {
            this.manageController = manageController;
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx,RemotingCommand request) throws RemotingCommandException {
        switch (request.getCode()) {
            case RequestCode.SAVE_TRANSGROUP:
              return createGroup(ctx, request);
            case RequestCode.ADD_TRANS:
              return addTrans(ctx, request);
            case RequestCode.FIND_TRANSGROUP_STATUS:
              return getTransactionGroupStatus(ctx, request);
            case RequestCode.PRE_COMMIT_TRANS: 
              return preCommit(ctx, request);
            case RequestCode.COMMIT_TRANS:
              return completeCommit(ctx, request);
            case RequestCode.ROLLBACK_TRANSGROUP:
              return rollback(ctx, request);
            default:
                break;
        }
        return null;
    }

    
    
    /**
     * 功能描述: 创建事务组
     * @author: chenjy
     * @date: 2018年3月14日 下午2:22:54 
     * @param ctx
     * @param request
     * @return
     * @throws RemotingCommandException
     */
    private RemotingCommand createGroup(ChannelHandlerContext ctx,RemotingCommand request) throws RemotingCommandException {
        try {
          RemotingCommand response = RemotingCommand.createResponseCommand(null);
          byte[] body = request.getBody();
          if (body != null) {
              TxTransactionGroup tx =RemotingSerializable.decode(body, TxTransactionGroup.class);
              if (CollectionUtils.isNotEmpty(tx.getItemList())) {
                  String modelName = ctx.channel().remoteAddress().toString();
                  //这里创建事务组的时候，事务组也作为第一条数据来存储,第二条数据才是发起方因此是get(1)
                  TxTransactionItem item = tx.getItemList().get(1);
                  item.setModelName(modelName);
                  item.setTmDomain(Address.getInstance().getDomain());
              }
              Boolean success = manageController.getTxManagerService().saveTxTransactionGroup(tx);
              if(success){
                response.setCode(ResponseCode.SUCCESS);
                response.setOpaque(request.getOpaque());
                ctx.writeAndFlush(response);
              }else{
                response.setCode(ResponseCode.SYSTEM_ERROR);
                return response;
              }
          }
        } catch (Exception e) {
            log.error("Failed to produce a proper response", e);
        }
        return null;
    }
    
    
    
    
    /**
     * 功能描述: 查找事务状态
     * @author: chenjy
     * @date: 2018年3月14日 下午3:05:45 
     * @param ctx
     * @param request
     * @return
     * @throws RemotingCommandException
     */
    private RemotingCommand getTransactionGroupStatus(ChannelHandlerContext ctx,RemotingCommand request) throws RemotingCommandException {
        RemotingCommand response = RemotingCommand.createResponseCommand(FindTransGroupStatusResponseHeader.class);
        FindTransGroupStatusResponseHeader resHeader=(FindTransGroupStatusResponseHeader)response.readCustomHeader();
        FindTransGroupStatusRequestHeader header=(FindTransGroupStatusRequestHeader)request.decodeCommandCustomHeader(FindTransGroupStatusRequestHeader.class);
        int status = manageController.getTxManagerService().findTxTransactionGroupStatus(header.getTxGroupId());
        resHeader.setStatus(status);
        response.setCode(ResponseCode.SUCCESS);
        response.setOpaque(request.getOpaque());
        ctx.writeAndFlush(response);
        return null;
    }
    
    
    
   /**
    * 功能描述: 增加事务信息
    * @author: chenjy
    * @date: 2018年3月14日 下午3:30:53 
    * @param ctx
    * @param request
    * @return
    * @throws RemotingCommandException
    */
   private RemotingCommand addTrans(ChannelHandlerContext ctx,RemotingCommand request) throws RemotingCommandException {
      RemotingCommand response = RemotingCommand.createResponseCommand(null);
      AddTransRequestHeader header=(AddTransRequestHeader)request.decodeCommandCustomHeader(AddTransRequestHeader.class);
     
      byte[] body = request.getBody();
      if (body != null) {
            TxTransactionItem item =RemotingSerializable.decode(body, TxTransactionItem.class);
            item.setModelName(ctx.channel().remoteAddress().toString());
            item.setTmDomain(Address.getInstance().getDomain());
            Boolean success =  manageController.getTxManagerService().addTxTransaction(header.getTxGroupId(), item);
            if(success){
              response.setCode(ResponseCode.SUCCESS);
              response.setOpaque(request.getOpaque());
              ctx.writeAndFlush(response);
            }else{
              response.setCode(ResponseCode.SYSTEM_ERROR);
              return response;
            }
      }
      return null;
  }
    
    
   
   
   
   
   
   /**
    * 功能描述: 通知整个事务回滚
    * @author: chenjy
    * @date: 2018年3月14日 下午3:36:31 
    * @param ctx
    * @param request
    * @return
    * @throws RemotingCommandException
    */
   private RemotingCommand rollback(ChannelHandlerContext ctx,RemotingCommand request) throws RemotingCommandException {
     
     RemotingCommand response = RemotingCommand.createResponseCommand(null);
     
     RollBackTransGroupRequestHeader header=(RollBackTransGroupRequestHeader)request.decodeCommandCustomHeader(RollBackTransGroupRequestHeader.class);
     //收到客户端的回滚通知  此通知为事务发起（start）里面通知的,发送给其它的客户端
     manageController.getTxTransactionExecutor().rollBack(header.getTxGroupId(),manageController);
     
     response.setCode(ResponseCode.SUCCESS);
     response.setOpaque(request.getOpaque());
     ctx.writeAndFlush(response);
     return null;
   }
   
   
   /**
    * 功能描述: 预提交
    * @author: chenjy
    * @date: 2018年3月15日 上午9:06:22 
    * @param ctx
    * @param request
    * @return
    * @throws RemotingCommandException
    */
   private RemotingCommand preCommit(ChannelHandlerContext ctx,RemotingCommand request) throws RemotingCommandException {
     RemotingCommand response = RemotingCommand.createResponseCommand(null);
     PreCommitRequestHeader header=(PreCommitRequestHeader)request.decodeCommandCustomHeader(PreCommitRequestHeader.class);
     
     manageController.getTxTransactionExecutor().preCommit(header.getTxGroupId(),manageController);
     
     
     response.setCode(ResponseCode.SUCCESS);
     response.setOpaque(request.getOpaque());
     ctx.writeAndFlush(response);
     return null;
   }
   
   
   
   /**
    * 功能描述: 提交事务
    * @author: chenjy
    * @date: 2018年3月14日 下午3:39:38 
    * @param ctx
    * @param request
    * @return
    * @throws RemotingCommandException
    */
   private RemotingCommand completeCommit(ChannelHandlerContext ctx,RemotingCommand request) throws RemotingCommandException {
     RemotingCommand response = RemotingCommand.createResponseCommand(null);
     byte[] body = request.getBody();
     if (body != null) {
         TxTransactionGroup tx =RemotingSerializable.decode(body, TxTransactionGroup.class);
         if (CollectionUtils.isNotEmpty(tx.getItemList())) {
           manageController.getTxManagerService().updateTxTransactionItemStatus(tx.getId(), tx.getItemList().get(0).getTaskKey(),tx.getItemList().get(0).getStatus());
         }
         response.setCode(ResponseCode.SUCCESS);
         response.setOpaque(request.getOpaque());
         ctx.writeAndFlush(response);
     }
     return null;
   }
   
   
    @Override
    public boolean rejectRequest() {
        return false;
    }

  
}
