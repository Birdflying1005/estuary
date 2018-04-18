package com.neighborhood.aka.laplace.estuary.web.service

import com.neighborhood.aka.laplace.estuary.bean.task.Mysql2KafkaTaskInfoBean
import com.neighborhood.aka.laplace.estuary.mysql.Mysql2KafkaTaskInfoManager
import com.neighborhood.aka.laplace.estuary.mysql.lifecycle.MysqlBinlogController
import com.neighborhood.aka.laplace.estuary.web.akka.ActorRefHolder
import org.slf4j.{Logger, LoggerFactory}

/**
  * Created by john_liu on 2018/3/10.
  */
object Mysql2KafkaService {

//  val logger:Logger = LoggerFactory.getLogger(Mysql2KafkaService.getClass)
//  logger.info("====================logback log start======================")

  def loadOneExistTask(syncTaskId: String): Mysql2KafkaTaskInfoBean = {
    ???
  }

  def loadAllExistTask: List[Mysql2KafkaTaskInfoBean] = {
    ???
  }

  def startAllExistTask: String = {
    loadAllExistTask
      .map(startNewOneTask(_))
      .mkString(",")


  }

  def startOneExistTask(syncTaskId: String): String = {
    startNewOneTask(loadOneExistTask(syncTaskId))
  }


  def startNewOneTask(mysql2KafkaTaskInfoBean: Mysql2KafkaTaskInfoBean): String = {
    val prop = MysqlBinlogController.props(mysql2KafkaTaskInfoBean)
    ActorRefHolder.syncDaemon ! (prop, Option(mysql2KafkaTaskInfoBean.syncTaskId))
    //todo 持久化任务
    s"mession:${mysql2KafkaTaskInfoBean.syncTaskId} submitted"
  }

  def checkTaskStatus(syncTaskId: String): String = {
    Option(Mysql2KafkaTaskInfoManager.taskStatusMap.get(syncTaskId))
    match {
      case Some(x) => {
        s"{$syncTaskId:${x.map(kv => s"${kv._1}:${kv._2}").mkString(",")}}"
      }
      case None => s"$syncTaskId:None}"
    }

  }

  def reStartTask(syncTaskId: String): Boolean = {
    val map = ActorRefHolder.actorRefMap
    Option(map
      .get(syncTaskId))
    match {
      case Some(x) => x ! "restart"; true
      case None => false
    }
  }

  def stopTask(syncTaskId: String): Boolean = {
    val map = ActorRefHolder.actorRefMap
    Option(
      map
        .get(syncTaskId)
    ) match {
      case Some(x) => ActorRefHolder.system.stop(x); map.remove(syncTaskId); true
      case None => false
    }

  }

  def checkSystemStatus = {
    ???
  }

  def checklogCount(syncTaskId: String): String = {
    val manager = Mysql2KafkaTaskInfoManager.taskManagerMap.get(syncTaskId)
    Option(manager)
    match {
      case Some(x) => if (x.taskInfo.isCounting) s"{$syncTaskId: ${
        Mysql2KafkaTaskInfoManager
          .logCount(x)
          .map(kv => s"${kv._1}:${kv._2}")
          .mkString(",")
      } }" else s"{$syncTaskId:count is not set}"
      case None => "task not exist"
    }
  }

  def checkTimeCost(syncTaskId: String): String = {
    val manager = Mysql2KafkaTaskInfoManager.taskManagerMap.get(syncTaskId)
    Option(manager)
    match {
      case Some(x) => if (x.taskInfo.isCosting) s"{$syncTaskId: ${
        Mysql2KafkaTaskInfoManager
          .logTimeCost(x)
          .map(kv => s"${kv._1}:${kv._2}")
          .mkString(",")
      } }" else s"{$syncTaskId:profiling is not set}"
      case None => "task not exist"
    }
  }

  def checklastSavedlogPosition(syncTaskId: String): String = {
    val manager = Mysql2KafkaTaskInfoManager.taskManagerMap.get(syncTaskId)
    Option(manager)
    match {
      case Some(x) => if (x.taskInfo.isProfiling) s"{$syncTaskId:${x.sinkerLogPosition.get()} }" else s"{$syncTaskId:profiling is not set}"
      case None => "task not exist"
    }
  }
}
