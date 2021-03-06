package com.wf.gts.manage.processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wf.gts.manage.client.ClientChannelInfo;
import com.wf.gts.manage.client.ProducerManager;
import com.wf.gts.remoting.exception.RemotingCommandException;
import com.wf.gts.remoting.header.UnregisterClientRequestHeader;
import com.wf.gts.remoting.header.UnregisterClientResponseHeader;
import com.wf.gts.remoting.netty.NettyRequestProcessor;
import com.wf.gts.remoting.protocol.HeartbeatData;
import com.wf.gts.remoting.protocol.RemotingCommand;
import com.wf.gts.remoting.protocol.RequestCode;
import com.wf.gts.remoting.protocol.ResponseCode;

import io.netty.channel.ChannelHandlerContext;

public class ClientManageProcessor implements NettyRequestProcessor {

  private static final Logger log = LoggerFactory.getLogger(ClientManageProcessor.class);
   
  private ProducerManager producerManager;

  public ClientManageProcessor(ProducerManager producerManager) {
      this.producerManager = producerManager;
  }
  
  
  @Override
  public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request)
      throws RemotingCommandException {
      switch (request.getCode()) {
          case RequestCode.HEART_BEAT:
              return this.heartBeat(ctx, request);
          case RequestCode.UNREGISTER_CLIENT:
              return this.unregisterClient(ctx, request);
          default:
              break;
      }
      return null;
  }

  public RemotingCommand heartBeat(ChannelHandlerContext ctx, RemotingCommand request) {
    RemotingCommand response = RemotingCommand.createResponseCommand(null);
    HeartbeatData heartbeatData = HeartbeatData.decode(request.getBody(), HeartbeatData.class);
    ClientChannelInfo clientChannelInfo = new ClientChannelInfo(ctx.channel(),heartbeatData.getClientID());
    this.producerManager.registerProducer(ctx.channel().remoteAddress().toString(), clientChannelInfo);
    response.setCode(ResponseCode.SUCCESS);
    response.setRemark(null);
    return response;
  }
  
  
  
  public RemotingCommand unregisterClient(ChannelHandlerContext ctx, RemotingCommand request)throws RemotingCommandException {
    RemotingCommand response =RemotingCommand.createResponseCommand(UnregisterClientResponseHeader.class);
    UnregisterClientRequestHeader requestHeader =(UnregisterClientRequestHeader) request.decodeCommandCustomHeader(UnregisterClientRequestHeader.class);
    ClientChannelInfo clientChannelInfo = new ClientChannelInfo(ctx.channel(),requestHeader.getClientID());
    this.producerManager.unregisterProducer(ctx.channel().remoteAddress().toString(), clientChannelInfo);
    response.setCode(ResponseCode.SUCCESS);
    response.setRemark(null);
    return response;
  }
  
  
  @Override
  public boolean rejectRequest() {
      return false;
  }

}