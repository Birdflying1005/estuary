package com.neighborhood.aka.laplce.estuary.mysql.actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, OneForOneStrategy, Props}
import com.alibaba.otter.canal.parse.inbound.mysql.MysqlConnection
import com.neighborhood.aka.laplce.estuary.core.lifecycle
import com.neighborhood.aka.laplce.estuary.core.lifecycle.{ListenerMessage, Status, SyncController, SyncControllerMessage}
import com.neighborhood.aka.laplce.estuary.mysql.Mysql2KafkaTaskInfoManager
import org.I0Itec.zkclient.exception.ZkTimeoutException

/**
  * Created by john_liu on 2018/2/1.
  */

class MySqlBinlogController(mysql2KafkaTaskInfoManager: Mysql2KafkaTaskInfoManager) extends SyncController with Actor {
  //资源管理器，一次同步任务所有的resource都由resourceManager负责
  val resourceManager = mysql2KafkaTaskInfoManager
  //配置
  val config = context.system.settings.config
  //canal的mysqlConnection
  val mysqlConnection: MysqlConnection = resourceManager.mysqlConnection
  override var errorCountThreshold: Int = 3
  override var errorCount: Int = 0

  initWorkers

  def initWorkers = {
    //todo logstash
    //初始化HeartBeatsListener
    context.actorOf(Props(classOf[MysqlConnectionListener], resourceManager), "heartBeatsListener")
    //初始化binlogSinker
    //如果并行打开使用并行sinker
    val binlogSinker = if (resourceManager.taskInfo.isTransactional) {
      context.actorOf(ConcurrentBinlogSinker.prop(resourceManager), "binlogSinker")
    } else {
      //不是然使用transaction式
      context.actorOf(Props(classOf[BinlogTransactionBufferSinker], resourceManager), "binlogSinker")
    }
    //初始化binlogEventBatcher
    val binlogEventBatcher = context.actorOf(BinlogEventBatcher.prop(binlogSinker, resourceManager), "binlogBatcher")
    //初始化binlogFetcher
    context.actorOf(Props(classOf[MysqlBinlogFetcher], resourceManager, binlogEventBatcher), "binlogFetcher")
  }

  //offline 状态
  override def receive: Receive = {
    case "start" => {
      context.become(online)
      startAllWorkers
    }
    case ListenerMessage(msg) => {
      msg match {
        case "restart" => {
          sender ! SyncControllerMessage("start")
        }
        case "reconnect" => {
          mysqlConnection.synchronized(mysqlConnection.reconnect())
        }
      }
    }

    case SyncControllerMessage(msg) => {
      msg match {
        case "restart" => self ! "start"
      }
    }
  }

  def online: Receive = {

    case ListenerMessage(msg) => {
      msg match {
        case "restart" => {
          sender ! SyncControllerMessage("start")
        }
        case "reconnect" => {
          try {
            mysqlConnection.synchronized(mysqlConnection.reconnect())
          } catch {
            case e: Exception => processError(e, ListenerMessage("reconnect"))
          }

        }
      }
    }

    case SyncControllerMessage(msg) => {

    }
  }

  def startAllWorkers = {
    //启动sinker
    context
      .child("binlogSinker")
      .map {
        ref => ref ! SyncControllerMessage("start")
      }
    //启动batcher
    context
      .child("binlogBatcher")
      .map {
        ref => ref ! SyncControllerMessage("start")
      }
    //启动fetcher
    context
      .child("binlogFetcher")
      .map {
        ref => ref ! SyncControllerMessage("start")
      }
    //启动listener
    context
      .child("heartBeatsListener")
      .map {
        ref => ref ! SyncControllerMessage("start")
      }
  }

  /**
    * 错误处理
    */
  override def processError(e: Throwable, message: lifecycle.WorkerMessage): Unit = {

    //todo 记录log
    errorCount += 1
    if (isCrashed) {
      switch2Error
      errorCount = 0
      throw new Exception("fetching data failure for 3 times")
    } else {
      message
      self ! message
    }
  }

  /**
    * ********************* 状态变化 *******************
    */
  private def switch2Offline = {
    mysql2KafkaTaskInfoManager.syncControllerStatus = Status.OFFLINE
  }

  private def switch2Busy = {
    mysql2KafkaTaskInfoManager.syncControllerStatus = Status.BUSY
  }

  private def switch2Error = {
    mysql2KafkaTaskInfoManager.syncControllerStatus = Status.ERROR
  }

  private def switch2Free = {
    mysql2KafkaTaskInfoManager.syncControllerStatus = Status.FREE
  }

  private def switch2Restarting = {
    mysql2KafkaTaskInfoManager.syncControllerStatus = Status.RESTARTING
  }

  /**
    * **************** Actor生命周期 *******************
    */

  /**
    * 每次启动都会调用，在构造器之后调用
    * 1.初始化HeartBeatsListener
    * 2.初始化binlogSinker
    * 3.初始化binlogEventBatcher
    * 4.初始化binlogFetcher
    */
  override def preStart(): Unit

  = {

  }

  //正常关闭时会调用，关闭资源
  override def postStop(): Unit

  = {
    //todo logstash
    mysqlConnection.disconnect()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit

  = {
    //todo logstash
    //默认的话是会调用postStop，preRestart可以保存当前状态
    context.become(receive)
    self ! SyncControllerMessage("restart")
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit

  = {
    //todo logstash
    //可以恢复之前的状态，默认会调用
    super.postRestart(reason)
  }
  override def supervisorStrategy = {
    OneForOneStrategy() {
      case e: ZkTimeoutException => {
        Restart
        //todo log
      }
      case e: Exception => Restart
      case error:Error => Restart
      case _ => Restart
    }
  }
}